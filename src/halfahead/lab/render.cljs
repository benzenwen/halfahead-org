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

;; --- stage -------------------------------------------------------------------

(def ^:private bg "#141210")

(def ^:private floor-drop
  "Gap between the block's lowest baseline region and the floor, in em."
  1.2)

(defn- floor-mesh
  "A wide plane under the text, receding into the distance: fine grid lines
  in ink at low alpha (blending is in linear light, so 0.02 shows as about
  8% of the way from the background to ink), anti-aliased by their
  screen-space width, fixed in world space so they never slide as the
  plane follows the block. Fog fades it into the background."
  []
  (let [^js p (.div (.-xz tsl/positionWorld) 2.0)
        ^js cell (.div (tsl/abs (.sub (tsl/fract (.sub p 0.5)) 0.5)) (tsl/fwidth p))
        line (.oneMinus (tsl/min (tsl/min (.-x cell) (.-y cell)) 1.0))
        ^js m (three/MeshBasicNodeMaterial.)]
    (set! (.-transparent m) true)
    (set! (.-depthWrite m) false)
    (set! (.-colorNode m) (tsl/vec4 (tsl/color ink) 1.0))
    (set! (.-opacityNode m) (.mul line 0.02))
    (doto (three/Mesh. (three/PlaneGeometry. 400 400) m)
      (-> .-rotation (.set (- (/ js/Math.PI 2)) 0 0))
      (aset "renderOrder" -1))))

;; --- camera ------------------------------------------------------------------

(def ^:private fov-deg 40)

(def ^:private pitch-deg
  "How far the camera looks down onto the block. A vertical edge at
  normalised image x (tangent of its angle off-axis) leans by about
  atan(x tan pitch); the block's outer edges sit near x = 0.4, so 8 degrees
  would lean them about 3 and 3 degrees leans them about 1.2, under the 2
  degree bound with margin, while still showing the floor receding."
  3)

(def ^:private lift
  "The block sits this fraction of the viewport height above centre, by
  shifting the lens rather than pitching further, so verticals stay upright."
  0.08)

(defn- fit-viewport!
  "Matches the drawing buffer and the camera's aspect and lens shift to the
  canvas's CSS size, only when that size changed."
  [{:keys [^js renderer ^js camera viewport]} ^js canvas]
  (let [w (max 1 (.-clientWidth canvas)), h (max 1 (.-clientHeight canvas))
        dpr (min 2 (or js/window.devicePixelRatio 1))]
    (when (not= @viewport [w h dpr])
      (reset! viewport [w h dpr])
      (.setPixelRatio renderer dpr)
      (.setSize renderer w h false)
      (set! (.-aspect camera) (/ w h))
      (.setViewOffset camera w h 0 (* lift h) w h))))

;; --- public ------------------------------------------------------------------

(def ^:private line-em
  "The layout width the shell asks for: in the condensed face about 36-40
  characters, a comfortable line."
  18)

(defn em-px
  "How many CSS pixels one em of text occupies in a viewport w px wide: a
  line of 18 em fills about 70% of the width, never smaller than 16 px.
  The shell divides by this to give the layout a width that fits."
  [w]
  (max 16 (/ (* 0.7 w) line-em)))

(defn- column-extent
  "[x0 x1 y0 y1] in em of the page the text is laid out on: the column's
  full width, and from the caret's baseline (always on the last line) up
  to the first line's cap height. Taken from the layout alone, so neither
  a letter's motion nor a new letter on the same line can move it."
  [column-em {caret-y :y}]
  [0 column-em caret-y 0.7])

(def ^:private follow-ms
  "Time constant with which the stage eases toward a changed block."
  200)

(defn- follow!
  "Eases the stage's extent toward this frame's column and returns the eased
  extent. It snaps on the first frame, and on every frame when still?
  (reduced motion), so the camera never glides on its own."
  [{:keys [stage]} {:keys [t-ms poses caret]} still? column-em]
  (let [target (column-extent column-em caret)
        {:keys [eased at]} @stage
        k (if (and eased (not still?))
            (- 1 (js/Math.exp (- (/ (max 0 (- t-ms at)) follow-ms))))
            1)
        eased' (if eased (mapv (fn [e t] (+ e (* k (- t e)))) eased target) target)]
    (reset! stage {:eased eased' :at t-ms :target target
                   :live (keep-indexed (fn [i p] (when-not (:ghost? p) i)) poses)})
    eased'))

(defn- view!
  "Aims the camera's axis at the centre of the eased column from in front
  and slightly above, at the distance where 1 em spans em-px CSS pixels;
  rests the floor under the block and sets the fog around the text plane,
  so letters coming forward brighten and the floor fades into the dark."
  [{:keys [^js camera ^js scene ^js floor]} ^js canvas [x0 x1 y0 y1]]
  (let [w (max 1 (.-clientWidth canvas)), h (max 1 (.-clientHeight canvas))
        fov-rad (* fov-deg (/ js/Math.PI 180))
        pitch (* pitch-deg (/ js/Math.PI 180))
        dist (/ (/ h (em-px w)) (* 2 (js/Math.tan (/ fov-rad 2))))
        fx (/ (+ x0 x1) 2), fy (/ (+ y0 y1) 2)
        ^js fog (.-fog scene)]
    (.set (.-position camera) fx (+ fy (* dist (js/Math.sin pitch))) (* dist (js/Math.cos pitch)))
    (.lookAt camera fx fy 0)
    (.set (.-position floor) fx (- y0 floor-drop) 0)
    ;; just past the text plane, so letters at rest keep full ink and only
    ;; those pushed back into the scene dim
    (set! (.-near fog) (+ dist 0.5))
    (set! (.-far fog) (+ dist 12))))

(defn- project
  "World point v (mutated) in CSS pixels of the canvas, y running down."
  [{:keys [^js camera viewport]} ^js v]
  (let [[w h] @viewport
        ^js ndc (.project v camera)]
    [(* w (/ (inc (.-x ndc)) 2)) (* h (/ (- 1 (.-y ndc)) 2))]))

(defn lean
  "Degrees between the screen vertical and the projected left edge (bottom
  to top corner) of drawn glyph i's quad, as placed in the last frame; the
  shader's jelly sway is not included."
  [r i]
  (let [^js mesh @(:mesh r)]
    (when-not (and (integer? i) (< -1 i (.-count mesh)))
      (throw (ex-info "No such glyph" {:type :lab/no-such-glyph :index i :count (.-count mesh)})))
    (let [^js m (three/Matrix4.)
          _ (.getMatrixAt mesh i m)
          [bx by] (project r (.applyMatrix4 (three/Vector3. -0.5 -0.5 0) m))
          [tx ty] (project r (.applyMatrix4 (three/Vector3. -0.5 0.5 0) m))]
      (* (/ 180 js/Math.PI) (js/Math.atan2 (- tx bx) (- by ty))))))

(defn block-centre
  "Where the last frame's text block lands on the canvas, in CSS pixels with
  y down, as #js {:x :y :aimX :aimY :width :height}. x and y are the centre
  of the bounding box of the live glyphs' quads as drawn, independent of
  where the camera aims; aimX and aimY are where the centre of the column
  lands, which is the camera's aim point once it has caught up with it."
  [r]
  (let [{:keys [target live]} @(:stage r)
        [x0 x1 y0 y1] target
        [w h] @(:viewport r)
        [ax ay] (project r (three/Vector3. (/ (+ x0 x1) 2) (/ (+ y0 y1) 2) 0))
        ^js mesh @(:mesh r)
        ^js m (three/Matrix4.)
        pts (for [i live
                  :let [_ (.getMatrixAt mesh i m)]
                  [cx cy] [[-0.5 -0.5] [0.5 -0.5] [-0.5 0.5] [0.5 0.5]]]
              (project r (.applyMatrix4 (three/Vector3. cx cy 0) m)))
        mid (fn [vs] (/ (+ (apply min vs) (apply max vs)) 2))]
    #js {:x (if (seq pts) (mid (map first pts)) ax)
         :y (if (seq pts) (mid (map second pts)) ay)
         :aimX ax :aimY ay :width w :height h}))

(defn screen-point
  "Where world point (x, y, z) in em landed in the last frame, in CSS
  pixels of the canvas with y down, as #js {:x :y}."
  [r x y z]
  (let [[px py] (project r (three/Vector3. x y z))]
    #js {:x px :y py}))

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
  "Draws one :ha/glyph-frame laid out column-em wide, staged on that column
  rather than on the text, so the line stays put while it grows. still?
  (reduced motion) keeps the camera from easing after the column as lines
  are added: it moves straight there instead."
  [{:keys [^js renderer ^js scene ^js camera ^js canvas atlas ^js atlas-size ^js caret] :as r}
   {:keys [poses] :as frame}
   {:keys [still? column-em]}]
  (let [n (count poses)
        ^js mesh (ensure-capacity! r n)
        ^js geometry (.-geometry mesh)
        attr (fn [k] (.-array (.getAttribute geometry k)))
        rects (attr "glyphRect"), opacity (attr "glyphOpacity"), glow (attr "glyphGlow")
        wobble (attr "glyphWobble"), ghost (attr "glyphGhost")
        ^js o scratch]
    (fit-viewport! r canvas)
    (view! r canvas (follow! r frame still? column-em))
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
        camera (three/PerspectiveCamera. fov-deg 1 0.1 500)
        floor (floor-mesh)
        caret (three/Mesh. (three/PlaneGeometry. 0.06 1.1)
                           (three/MeshBasicNodeMaterial. #js {:color vermilion}))
        r {:renderer renderer :scene scene :camera camera :canvas canvas
           :atlas atlas :atlas-node atlas-node :atlas-size atlas-size
           :material material :caret caret :floor floor
           :stage (atom {})
           :mesh (atom (glyph-mesh 256 material))
           :drawn (atom 0)
           :viewport (atom nil)
           :backend (if (.. renderer -backend -isWebGPUBackend) "WebGPU" "WebGL2")}]
    (set! (.-background scene) (three/Color. bg))
    (set! (.-fog scene) (three/Fog. bg 20 50))
    (.add scene floor)
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
                 (draw! r {:t-ms 0 :poses [] :caret {:x 0.0 :y 0.0}} {:still? true :column-em line-em})
                 r
                 (catch :default e
                   (js/console.warn "WebGPU could not draw; falling back to WebGL2:" (.-message ^js e))
                   (.dispose ^js (:renderer r))
                   (init (fresh-canvas canvas) true)))))))
