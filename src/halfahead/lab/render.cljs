(ns halfahead.lab.render
  "The lab's reference renderer, and the only namespace that touches
  three.js or the GPU: it draws :ha/glyph-frames as SDF glyphs on instanced
  quads, seen from each frame's :camera, and decides nothing, not even
  where to look. WebGPU where the browser has it, WebGL2
  otherwise (three's WebGPURenderer picks), with one TSL material for both.
  Everything a pose can vary (stretch, slant, weight, focus, fill, ink) is
  a per-instance attribute of the one instanced mesh, so a frame of any
  mix of them is still one draw call.
  The frame's :reveal sets the house lights: ink on paper with them up, the
  dark stage with them down. What stands behind the letters (a floor, a
  storm) is the stage's (lab.stage): the renderer hands it each frame and
  each of the frame's moments, and draws the scene it keeps."
  (:require ["@mapbox/tiny-sdf" :default TinySDF]
            ["three/tsl" :as tsl]
            ["three/webgpu" :as three]
            [halfahead.lab.ink :as ink]))

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

(def ^:private vermilion "#ff7a55")
(def ^:private ember "#ff8a3d")

;; The field is 0.75 on the outline and changes by 1/12 per atlas pixel: it
;; reaches 1 three pixels inside (about the middle of a stem) and is about
;; 0.08 at the quad's border, 8 pixels outside. The edge may move only
;; between those, or a bold letter is clipped square and a light one is gone.
(def ^:private outline 0.75)
(def ^:private bolder
  "How far :weight +1 moves the edge out, in field units (2.6 atlas pixels):
  black, with the counters of a and e still open."
  0.22)
(def ^:private lighter
  "How far :weight -1 moves it in (1.3 pixels): thin strokes keep a core."
  0.11)
(def ^:private crisp
  "Half the width of the edge's soft band at :blur 0."
  0.06)
(def ^:private soft
  "What :blur 1 adds to that half width."
  0.24)
(def ^:private band-floor
  "The band never starts below this, so the quad's border stays clear."
  0.10)
(def ^:private unfilled
  "The alpha of the part of a letter that :fill has not reached."
  0.35)
(def ^:private fill-edge
  "The width of the fill's soft boundary, in the quad's own 0..1 width."
  0.08)

(defn- glyph-material
  "Alpha from the distance field with a smooth edge, which :weight moves and
  :blur widens; :fill dims the quad right of it; the letter in its own ink
  (glyphInk), ghosts in ember orange; glow as a wider, softer halo from the
  same field, in vermilion; wobble as a jelly sway of the quad's corners."
  [^js atlas-texture ^js atlas-size]
  (let [rect (tsl/attribute "glyphRect" "vec4")
        opacity (tsl/attribute "glyphOpacity" "float")
        glow (tsl/attribute "glyphGlow" "float")
        wobble (tsl/attribute "glyphWobble" "float")
        ghost (tsl/attribute "glyphGhost" "float")
        ^js shape (tsl/attribute "glyphShape" "vec3")
        ^js weight (.-x shape), ^js blur (.-y shape), ^js filled (.-z shape)
        glyph-ink (tsl/attribute "glyphInk" "vec3")
        ^js uv (tsl/uv)
        ;; PlaneGeometry's v runs bottom-up; the atlas rows run top-down.
        texel (.div (.add (.-xy rect) (.mul (tsl/vec2 (.-x uv) (.oneMinus (.-y uv))) (.-zw rect)))
                    atlas-size)
        ^js atlas-node (tsl/texture atlas-texture texel)
        ^js d (.-r atlas-node)
        ^js edge (.sub (tsl/float outline) (.mul weight (tsl/select (.greaterThan weight 0.0) bolder lighter)))
        ^js band (.add (.mul blur soft) crisp)
        fill (tsl/smoothstep (tsl/max (.sub edge band) band-floor)
                             (tsl/min (.add edge band) 1.0)
                             d)
        halo (.mul (.mul (tsl/smoothstep 0.15 edge d) glow) 0.85)
        ;; 0 over the inked part of the quad, 1 over the rest; at :fill 0 the
        ;; boundary starts left of the quad and at 1 it ends right of it
        ^js wipe (.sub (.mul filled (+ 1.0 fill-edge)) fill-edge)
        dim (tsl/smoothstep wipe (.add wipe fill-edge) (.-x uv))
        base (tsl/mix glyph-ink (tsl/color ember) ghost)
        ;; the letter lies over its halo, so vermilion shows only where the
        ;; halo does: a soft or coloured edge keeps the letter's own ink
        rgb (tsl/mix (tsl/color vermilion) base
                     (.div fill (tsl/max (tsl/max fill halo) 0.0001)))
        ^js g tsl/positionGeometry
        sway (tsl/vec3 (.mul (tsl/sin (.add (.mul tsl/time 11.0) (.mul (.-y g) 5.0))) 0.18)
                       (.mul (tsl/sin (.add (.mul tsl/time 9.0) (.mul (.-x g) 6.0))) 0.14)
                       0.0)
        ^js m (three/MeshBasicNodeMaterial.)]
    (set! (.-transparent m) true)
    (set! (.-depthWrite m) false)
    (set! (.-side m) three/DoubleSide)
    (set! (.-colorNode m) (tsl/vec4 rgb 1.0))
    (set! (.-opacityNode m) (.mul (.mul (tsl/max fill halo) opacity)
                                  (tsl/mix 1.0 unfilled dim)))
    (set! (.-positionNode m) (.add tsl/positionLocal (.mul (.mul sway wobble) (.length (.-xy g)))))
    {:material m :atlas-node atlas-node}))

;; --- instances ---------------------------------------------------------------

(def ^:private per-instance
  {"glyphRect" 4 "glyphOpacity" 1 "glyphGlow" 1 "glyphWobble" 1 "glyphGhost" 1
   ;; weight, blur, fill; and the letter's colour, in linear light
   "glyphShape" 3 "glyphInk" 3})

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

;; --- house -------------------------------------------------------------------

(def ^:private bg "#141210")
(def ^:private paper "#f6f1e7")

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

(defn- view!
  "Puts the camera where the frame says, looking at its target, and sets
  the fog just past it, so letters at rest keep full ink and those pushed
  back into the scene (receded lines among them) dim into the dark."
  [{:keys [^js camera ^js scene]} {:keys [x y z tx ty tz fov]}]
  (let [^js fog (.-fog scene)
        dist (js/Math.hypot (- x tx) (- y ty) (- z tz))]
    (when (not= fov (.-fov camera))
      (set! (.-fov camera) fov)
      (.updateProjectionMatrix camera))
    (.set (.-position camera) x y z)
    (.lookAt camera tx ty tz)
    (set! (.-near fog) (+ dist 0.5))
    (set! (.-far fog) (+ dist 12))))

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

(defn- corners
  "The four corners of each of the drawn glyphs indexes, as placed in the
  last frame, in CSS pixels of the canvas with y down."
  [r indexes]
  (let [^js mesh @(:mesh r)
        ^js m (three/Matrix4.)]
    (doall
     (for [i indexes
           :let [_ (.getMatrixAt mesh i m)]
           [cx cy] [[-0.5 -0.5] [0.5 -0.5] [-0.5 0.5] [0.5 0.5]]]
       (project r (.applyMatrix4 (three/Vector3. cx cy 0) m))))))

(defn glyph-box
  "[x0 y0 x1 y1], the box in CSS pixels of the canvas (y down) around the
  quads of the drawn glyphs indexes as placed in the last frame, stretch
  and slant included; the shader's jelly sway is not."
  [r indexes]
  (let [n (.-count ^js @(:mesh r))]
    (when-not (and (seq indexes) (every? #(and (integer? %) (< -1 % n)) indexes))
      (throw (ex-info "No such glyphs" {:type :lab/no-such-glyph :indexes indexes :count n})))
    (let [pts (corners r indexes)
          xs (map first pts), ys (map second pts)]
      [(apply min xs) (apply min ys) (apply max xs) (apply max ys)])))

(defn block-centre
  "Where the last frame's text block lands on the canvas, in CSS pixels with
  y down, as #js {:x :y :aimX :aimY :width :height}. x and y are the centre
  of the bounding box of the live glyphs' quads as drawn; aimX and aimY are
  where the frame's camera target lands."
  [r]
  (let [{:keys [camera live]} @(:last r)
        {:keys [tx ty tz]} camera
        [w h] @(:viewport r)
        [ax ay] (project r (three/Vector3. tx ty tz))
        pts (corners r live)
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

(defn plane-point
  "Where the client point (client-x, client-y), as a pointer event gives
  it, lies on the text plane (z 0) as the last frame's camera saw it:
  {:x :y} in em, or nil when the point is off the canvas, nothing has been
  drawn yet, or the camera does not look at the plane there."
  [{:keys [^js camera ^js canvas viewport]} client-x client-y]
  (when @viewport
    (let [^js box (.getBoundingClientRect canvas)
          u (/ (- client-x (.-left box)) (max 1 (.-width box)))
          v (/ (- client-y (.-top box)) (max 1 (.-height box)))]
      (when (and (<= 0 u 1) (<= 0 v 1))
        (let [^js eye (.-position camera)
              ^js far (.unproject (three/Vector3. (dec (* 2 u)) (- 1 (* 2 v)) 0.5) camera)
              dz (- (.-z far) (.-z eye))
              k (when-not (zero? dz) (/ (- (.-z eye)) dz))]
          (when (and k (pos? k))
            {:x (+ (.-x eye) (* k (- (.-x far) (.-x eye))))
             :y (+ (.-y eye) (* k (- (.-y far) (.-y eye))))}))))))

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
(def ^:private scratch-shape (three/Matrix4.))
(def ^:private scratch-matrix (three/Matrix4.))
(def ^:private scratch-colour (three/Color.))

(def ^:private lights-up (three/Color. paper))
(def ^:private lights-down (three/Color. bg))

(defn- linear!
  "scratch-colour set to the sRGB colour [r g b], in the linear light the
  renderer works in."
  ^js [[r g b]]
  (.setRGB scratch-colour r g b three/SRGBColorSpace))

(defn- light!
  "Background, fog and the :text ink (which the stage draws in) for the
  frame's lights: paper and ink at 0, the dark stage at 1, in between as
  they go down."
  [{:keys [^js scene ^js ink-node]} lights]
  (.lerpColors (.-background scene) lights-up lights-down lights)
  (.copy (.-color (.-fog scene)) (.-background scene))
  (.copy (.-value ink-node) (linear! (ink/colour :text lights))))

(defn- pose-colour
  "The colour of a pose's letter: the :text ink, :tint of the way towards
  the pose's own :ink (all the way when it names an ink and no :tint)."
  [{:keys [tint] :as p} lights]
  (let [text (ink/colour :text lights)]
    (if-let [k (:ink p)]
      (let [t (if (nil? tint) 1 tint)]
        (mapv (fn [a b] (+ a (* t (- b a)))) text (ink/colour k lights)))
      text)))

(defn- stage!
  "The stage told of each moment, in order, then brought to the frame."
  [{:keys [stage stage-state]} frame moments]
  (swap! stage-state
         (fn [s]
           ((:update stage) (reduce (:moment stage) s moments) frame (:t-ms frame)))))

(defn stage-state
  "The stage's state as plain data (its numbers, keywords and strings),
  for tests and the debug overlay."
  [r]
  (into {} (filter (comp (some-fn number? keyword? string?) val)) @(:stage-state r)))

(defn draw!
  "Draws one :ha/glyph-frame, seen from its :camera, with the stage behind
  it told of moments (lab.moments, each given once) first."
  [{:keys [^js renderer ^js scene ^js camera ^js canvas atlas ^js atlas-size ^js caret] :as r}
   {:keys [poses] :as frame} moments]
  (let [n (count poses)
        ^js mesh (ensure-capacity! r n)
        ^js geometry (.-geometry mesh)
        attr (fn [k] (.-array (.getAttribute geometry k)))
        rects (attr "glyphRect"), opacity (attr "glyphOpacity"), glow (attr "glyphGlow")
        wobble (attr "glyphWobble"), ghost (attr "glyphGhost")
        shape (attr "glyphShape"), inks (attr "glyphInk")
        lights (get-in frame [:reveal :lights] 1)
        ;; nearly every letter is in the :text ink: convert it once
        text (.clone (linear! (ink/colour :text lights)))
        ^js o scratch]
    (fit-viewport! r canvas)
    (light! r lights)
    (view! r (:camera frame))
    (stage! r frame moments)
    (reset! (:last r) {:camera (:camera frame)
                       :live (keep-indexed (fn [i p] (when-not (:ghost? p) i)) poses)})
    (dotimes [i n]
      (let [p (nth poses i)
            {:keys [rect size centre]} (glyph r (:char p))
            [rx ry rw rh] rect, [w h] size, [cx cy] centre
            s (:scale p)
            qw (* s (:sx p 1) w), sy (* s (:sy p 1)), qh (* sy h)
            k (:skew p 0)
            ^js c (if (:ink p) (linear! (pose-colour p lights)) text)]
        (.set (.-position o) (+ (:x p) cx) (+ (:y p) cy) (:z p))
        (.set (.-rotation o) (:rx p) (:ry p) (:rz p))
        (.updateMatrix o)
        ;; The unit quad stretched to the glyph's size, then sheared along x
        ;; by its height above the glyph's baseline, which is sy * cy below
        ;; the quad's centre; then placed and turned as before.
        (.set scratch-shape
              qw (* k qh) 0 (* k sy cy)
              0  qh       0 0
              0  0        1 0
              0  0        0 1)
        (.setMatrixAt mesh i (.multiplyMatrices scratch-matrix (.-matrix o) scratch-shape))
        (.set rects #js [rx ry rw rh] (* 4 i))
        (aset opacity i (:opacity p))
        (aset glow i (:glow p))
        (aset wobble i (:wobble p))
        (aset ghost i (if (:ghost? p) 1 0))
        (.set shape #js [(:weight p 0) (:blur p 0) (:fill p 1)] (* 3 i))
        (.set inks #js [(.-r c) (.-g c) (.-b c)] (* 3 i))))
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
  "Everything drawn, around a renderer that has finished init, with stage
  set up behind the letters."
  [^js renderer ^js canvas stage]
  (let [atlas (atom (new-atlas))
        ^js atlas-size (tsl/uniform (three/Vector2. atlas-width (:height @atlas)))
        ^js ink-node (tsl/uniform (three/Color.))
        {:keys [material atlas-node]} (glyph-material (:texture @atlas) atlas-size)
        scene (three/Scene.)
        camera (three/PerspectiveCamera. 40 1 0.1 500)
        caret (three/Mesh. (three/PlaneGeometry. 0.06 1.1)
                           (three/MeshBasicNodeMaterial. #js {:color vermilion}))
        r {:renderer renderer :scene scene :camera camera :canvas canvas
           :atlas atlas :atlas-node atlas-node :atlas-size atlas-size
           :material material :caret caret
           :ink-node ink-node :stage stage
           :last (atom {})
           :mesh (atom (glyph-mesh 256 material))
           :drawn (atom 0)
           :viewport (atom nil)
           :backend (if (.. renderer -backend -isWebGPUBackend) "WebGPU" "WebGL2")}]
    (set! (.-background scene) (three/Color. bg))
    (set! (.-fog scene) (three/Fog. bg 20 50))
    (.add scene @(:mesh r))
    (.add scene caret)
    (doseq [c (map char (range 32 127))] (glyph r (str c)))
    (assoc r :stage-state (atom ((:init stage) scene {:ink ink-node})))))

(defn- init [^js canvas webgl? stage]
  (let [^js renderer (three/WebGPURenderer. #js {:canvas canvas :antialias true
                                                  :forceWebGL webgl?})]
    (.then (.init renderer) #(build renderer canvas stage))))

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
  shows it, hence the probe, drawn from probe-frame (an :ha/glyph-frame
  the shell supplies, so the camera comes from the core here too). stage
  (a lab.stage) draws behind the letters."
  [^js canvas probe-frame stage]
  (-> (init canvas false stage)
      (.then (fn [r]
               (try
                 (draw! r probe-frame [])
                 r
                 (catch :default e
                   (js/console.warn "WebGPU could not draw; falling back to WebGL2:" (.-message ^js e))
                   ((:dispose stage) @(:stage-state r))
                   (.dispose ^js (:renderer r))
                   (init (fresh-canvas canvas) true stage)))))))
