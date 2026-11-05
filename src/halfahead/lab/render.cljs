(ns halfahead.lab.render
  "The lab's reference renderer, and the only namespace that touches
  three.js or the GPU: it draws :ha/glyph-frames as SDF glyphs on instanced
  quads and decides nothing. WebGPU where the browser has it, WebGL2
  otherwise (three's WebGPURenderer picks), with one TSL material for both."
  (:require ["@mapbox/tiny-sdf" :default TinySDF]
            ["three/tsl" :as tsl]
            ["three/webgpu" :as three]))

;; --- glyph atlas -------------------------------------------------------------
;; One R8 texture of signed distance fields, packed in shelves. Characters
;; are rasterised the first time anyone asks about them; when the atlas is
;; full it doubles in height (a new texture, since GPU textures cannot be
;; resized), and rectangles stay valid because they are kept in pixels.

(def ^:private font-px 48)
(def ^:private sdf-buffer 8)
(def ^:private sdf-radius 12)
(def ^:private atlas-width 1024)

;; The app's label face; system fonts only, nothing is downloaded.
(def ^:private font-family
  "\"Arial Narrow\", \"Helvetica Neue\", \"Roboto Condensed\", system-ui, sans-serif")

(defn- data-texture [^js pixels height]
  (doto (three/DataTexture. pixels atlas-width height three/RedFormat three/UnsignedByteType)
    (aset "minFilter" three/LinearFilter)
    (aset "magFilter" three/LinearFilter)
    (aset "needsUpdate" true)))

(defn- new-atlas []
  (let [height 512
        pixels (js/Uint8Array. (* atlas-width height))]
    {:sdf (TinySDF. #js {:fontSize font-px :buffer sdf-buffer :radius sdf-radius
                         :cutoff 0.25 :fontFamily font-family :fontWeight "600"})
     :pixels pixels :height height :texture (data-texture pixels height)
     :shelf {:x 0 :y 0 :row 0}
     :glyphs {}}))

(defn- grow [{:keys [^js pixels height ^js texture] :as atlas}]
  (let [height' (* 2 height)
        pixels' (doto (js/Uint8Array. (* atlas-width height')) (.set pixels))]
    (.dispose texture)
    (assoc atlas :pixels pixels' :height height' :texture (data-texture pixels' height'))))

(defn- place
  "The shelf position for a w x h bitmap, growing the atlas as needed."
  [atlas w h]
  (loop [{:keys [shelf height] :as atlas} atlas]
    (let [{:keys [x y row]} shelf
          [x y row] (if (> (+ x w) atlas-width) [0 (+ y row 1) 0] [x y row])]
      (if (> (+ y h) height)
        (recur (grow atlas))
        [(assoc atlas :shelf {:x (+ x w 1) :y y :row (max row h)}) x y]))))

(defn- rasterise [{:keys [^js sdf] :as atlas} c]
  (let [^js g (.draw sdf c)
        w (.-width g), h (.-height g)
        [{:keys [^js pixels ^js texture] :as atlas} x y] (place atlas w h)
        ^js data (.-data g)
        em (fn [px] (/ px font-px))]
    (dotimes [row h]
      (.set pixels (.subarray data (* row w) (* (inc row) w)) (+ x (* (+ y row) atlas-width))))
    (aset texture "needsUpdate" true)
    (assoc-in atlas [:glyphs c]
              {:rect [x y w h]
               :advance (em (.-glyphAdvance g))
               ;; quad size and its centre relative to the pen on the baseline
               :size [(em w) (em h)]
               :centre [(em (+ (- (.-glyphLeft g) sdf-buffer) (/ w 2)))
                        (em (- (+ (.-glyphTop g) sdf-buffer) (/ h 2)))]})))

(defn- glyph
  "The atlas entry for c, rasterising it on first use."
  [{:keys [atlas ^js atlas-node]} c]
  (or (get-in @atlas [:glyphs c])
      (let [before (:texture @atlas)
            {:keys [texture] :as after} (swap! atlas rasterise c)]
        (when-not (identical? before texture)
          (aset atlas-node "value" texture))
        (get-in after [:glyphs c]))))

;; --- material ----------------------------------------------------------------

(def ^:private ink "#efe6d6")
(def ^:private vermilion "#ff7a55")
(def ^:private ember "#ff8a3d")

(defn- glyph-material
  "Alpha from the distance field with a smooth edge; glow as a wider, softer
  halo from the same field, tinted vermilion; ghosts in ember orange; wobble
  as a jelly sway of the quad's corners."
  [^js atlas-texture ^js atlas-size]
  (let [rect (tsl/attribute "glyphRect" "vec4")
        opacity (tsl/attribute "glyphOpacity" "float")
        glow (tsl/attribute "glyphGlow" "float")
        wobble (tsl/attribute "glyphWobble" "float")
        ghost (tsl/attribute "glyphGhost" "float")
        ^js uv (tsl/uv)
        ;; PlaneGeometry's v runs bottom-up; the atlas rows run top-down.
        texel (.div (.add (.-xy rect) (.mul (tsl/vec2 (.-x uv) (.oneMinus (.-y uv))) (.-zw rect)))
                    atlas-size)
        ^js atlas-node (tsl/texture atlas-texture texel)
        ^js d (.-r atlas-node)
        edge 0.75
        fill (tsl/smoothstep (- edge 0.06) (+ edge 0.06) d)
        halo (.mul (.mul (tsl/smoothstep 0.15 edge d) glow) 0.85)
        base (tsl/mix (tsl/color ink) (tsl/color ember) ghost)
        rgb (tsl/mix (tsl/color vermilion) base fill)
        ^js g tsl/positionGeometry
        sway (tsl/vec3 (.mul (tsl/sin (.add (.mul tsl/time 11.0) (.mul (.-y g) 5.0))) 0.18)
                       (.mul (tsl/sin (.add (.mul tsl/time 9.0) (.mul (.-x g) 6.0))) 0.14)
                       0.0)
        ^js m (three/MeshBasicNodeMaterial.)]
    (set! (.-transparent m) true)
    (set! (.-depthWrite m) false)
    (set! (.-side m) three/DoubleSide)
    (set! (.-colorNode m) (tsl/vec4 rgb 1.0))
    (set! (.-opacityNode m) (.mul (tsl/max fill halo) opacity))
    (set! (.-positionNode m) (.add tsl/positionLocal (.mul (.mul sway wobble) (.length (.-xy g)))))
    {:material m :atlas-node atlas-node}))

;; --- instances ---------------------------------------------------------------

(def ^:private per-instance
  {"glyphRect" 4 "glyphOpacity" 1 "glyphGlow" 1 "glyphWobble" 1 "glyphGhost" 1})

(defn- glyph-mesh [capacity ^js material]
  (let [^js geometry (three/PlaneGeometry. 1 1 6 6)]
    (doseq [[attr width] per-instance]
      (.setAttribute geometry attr
                     (doto (three/InstancedBufferAttribute. (js/Float32Array. (* capacity width)) width)
                       (.setUsage three/DynamicDrawUsage))))
    (doto (three/InstancedMesh. geometry material capacity)
      (aset "frustumCulled" false)
      (-> .-instanceMatrix (.setUsage three/DynamicDrawUsage)))))

(defn- ensure-capacity!
  "The glyph mesh, replaced by one twice as large when n poses overflow it."
  [{:keys [mesh ^js scene ^js material]} n]
  (let [^js current @mesh
        capacity (.-length (.-array (.-instanceMatrix current)))]
    (if (<= n (/ capacity 16))
      current
      (let [^js bigger (glyph-mesh (max n (/ capacity 8)) material)]
        (.remove scene current)
        (.dispose (.-geometry current))
        (.add scene bigger)
        (reset! mesh bigger)))))

;; --- camera ------------------------------------------------------------------

(defn- fit-viewport!
  "Matches the drawing buffer and the camera's aspect to the canvas's CSS
  size, only when that size changed."
  [{:keys [^js renderer ^js camera viewport]} ^js canvas]
  (let [w (max 1 (.-clientWidth canvas)), h (max 1 (.-clientHeight canvas))
        dpr (min 2 (or js/window.devicePixelRatio 1))]
    (when (not= @viewport [w h dpr])
      (reset! viewport [w h dpr])
      (.setPixelRatio renderer dpr)
      (.setSize renderer w h false)
      (set! (.-aspect camera) (/ w h))
      (.updateProjectionMatrix camera))))

;; --- public ------------------------------------------------------------------

(def ^:private text-px
  "On-screen size of 1 em at the text plane, in CSS pixels, at most."
  40)

(defn em-px
  "How many CSS pixels one em of text occupies in a viewport w px wide; the
  shell divides by this to give the layout a width that fits."
  [w]
  (min text-px (/ w 12)))

(defn- view
  "A camera slightly below and in front of the text plane (z = 0, text
  starting at the origin and running down), aimed up into it, so motion in
  z reads as depth; the first line sits near the top of the canvas."
  [^js camera ^js canvas]
  (let [w (max 1 (.-clientWidth canvas)), h (max 1 (.-clientHeight canvas))
        px (em-px w)
        fov-rad (* (.-fov camera) (/ js/Math.PI 180))
        ;; distance at which 1 em spans px CSS pixels vertically
        dist (/ (/ h px) (* 2 (js/Math.tan (/ fov-rad 2))))
        cx (- (/ w px 2) 1)
        cy (- 2.5 (/ h px 2))]
    (.set (.-position camera) cx (- cy (* 0.18 dist)) dist)
    (.lookAt camera cx (- cy 0.4) 0)))

(defn backend
  "\"WebGPU\" or \"WebGL2\", whichever this renderer is using."
  [r]
  (:backend r))

(defn advance
  "The advance of char c in em, from the font's metrics."
  [r c]
  (:advance (glyph r c)))

(defn drawn
  "Glyph instances drawn in the last frame."
  [r]
  @(:drawn r))

(def ^:private scratch (three/Object3D.))

(defn draw!
  "Draws one :ha/glyph-frame."
  [{:keys [^js renderer ^js scene ^js camera ^js canvas atlas ^js atlas-size ^js caret] :as r}
   {:keys [poses] :as frame}]
  (let [n (count poses)
        ^js mesh (ensure-capacity! r n)
        ^js geometry (.-geometry mesh)
        attr (fn [k] (.-array (.getAttribute geometry k)))
        rects (attr "glyphRect"), opacity (attr "glyphOpacity"), glow (attr "glyphGlow")
        wobble (attr "glyphWobble"), ghost (attr "glyphGhost")
        ^js o scratch]
    (fit-viewport! r canvas)
    (view camera canvas)
    (dotimes [i n]
      (let [p (nth poses i)
            {:keys [rect size centre]} (glyph r (:char p))
            [rx ry rw rh] rect, [w h] size, [cx cy] centre
            s (:scale p)]
        (.set (.-position o) (+ (:x p) cx) (+ (:y p) cy) (:z p))
        (.set (.-rotation o) (:rx p) (:ry p) (:rz p))
        (.set (.-scale o) (* s w) (* s h) 1)
        (.updateMatrix o)
        (.setMatrixAt mesh i (.-matrix o))
        (.set rects #js [rx ry rw rh] (* 4 i))
        (aset opacity i (:opacity p))
        (aset glow i (:glow p))
        (aset wobble i (:wobble p))
        (aset ghost i (if (:ghost? p) 1 0))))
    (.set (.-value atlas-size) atlas-width (:height @atlas))
    (doseq [k (keys per-instance)]
      (aset (.getAttribute geometry k) "needsUpdate" true))
    (aset (.-instanceMatrix mesh) "needsUpdate" true)
    (set! (.-count mesh) n)
    (let [{:keys [x y]} (:caret frame)]
      (.set (.-position caret) (+ x 0.04) (+ y 0.3) 0))
    (.render renderer scene camera)
    (reset! (:drawn r) n)))

(defn- build
  "Everything drawn, around a renderer that has finished init."
  [^js renderer ^js canvas]
  (let [atlas (atom (new-atlas))
        ^js atlas-size (tsl/uniform (three/Vector2. atlas-width (:height @atlas)))
        {:keys [material atlas-node]} (glyph-material (:texture @atlas) atlas-size)
        scene (three/Scene.)
        camera (three/PerspectiveCamera. 35 1 0.1 500)
        caret (three/Mesh. (three/PlaneGeometry. 0.06 1.1)
                           (three/MeshBasicNodeMaterial. #js {:color vermilion}))
        r {:renderer renderer :scene scene :camera camera :canvas canvas
           :atlas atlas :atlas-node atlas-node :atlas-size atlas-size
           :material material :caret caret
           :mesh (atom (glyph-mesh 256 material))
           :drawn (atom 0)
           :viewport (atom nil)
           :backend (if (.. renderer -backend -isWebGPUBackend) "WebGPU" "WebGL2")}]
    (set! (.-background scene) (three/Color. "#141210"))
    (.add scene @(:mesh r))
    (.add scene caret)
    (doseq [c (map char (range 32 127))] (glyph r (str c)))
    r))

(defn- init [^js canvas webgl?]
  (let [^js renderer (three/WebGPURenderer. #js {:canvas canvas :antialias true
                                                  :forceWebGL webgl?})]
    (.then (.init renderer) #(build renderer canvas))))

(defn- fresh-canvas
  "A copy of canvas in its place: a canvas that has handed out a WebGPU
  context cannot give a WebGL2 one."
  ^js [^js canvas]
  (let [^js copy (.cloneNode canvas false)]
    (.replaceWith canvas copy)
    copy))

(defn start!
  "A promise of a renderer drawing into canvas (or a fresh copy of it).
  WebGPU when the browser has it and it can draw a first frame, WebGL2
  otherwise; `backend` says which. Some browsers expose a WebGPU whose
  interface is older than this three.js expects, and only the first frame
  shows it, hence the probe."
  [^js canvas]
  (-> (init canvas false)
      (.then (fn [r]
               (try
                 (draw! r {:t-ms 0 :poses [] :caret {:x 0.0 :y 0.0}})
                 r
                 (catch :default e
                   (js/console.warn "WebGPU could not draw; falling back to WebGL2:" (.-message ^js e))
                   (.dispose ^js (:renderer r))
                   (init (fresh-canvas canvas) true)))))))
