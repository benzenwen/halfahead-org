(ns halfahead.lab.pose
  "The seam between the typing lab's draft and whatever draws it: given a
  draft and a time, where every letter stands, how it is turned, how big,
  bright and wobbly it is. Every frame is computed from scratch, never
  simulated, so a renderer may ask for any moment in any order, and every
  motion ends exactly on the layout within 2500 ms of its cause, in every
  style (plain births take 400 ms, cues 1500 ms, ghosts 600 ms; a style
  stretches or squeezes the first two by its tempo, see lab.style). No
  letter ever strays more than half an em sideways from its place, so a
  word's motion stays out of its neighbours' room: effects that gather or scatter use depth and height.
  The camera is part of every frame: it looks at the line being written,
  easing to a new line over 450 ms, and lines more than three above it
  recede up and back into the dark like an end-credits crawl, so a long
  draft uses depth instead of running off the screen. Units are em; angles
  are radians."
  (:require [clojure.string :as str]
            [halfahead.lab.style :as style]))

;; --- arithmetic --------------------------------------------------------------
;; Math/* reads the same in Clojure and ClojureScript, so no reader
;; conditionals are needed; args are doubles so the JVM never reflects.

(def ^:private tau (* 2.0 Math/PI))

(defn- sin [x] (Math/sin (double x)))
(defn- cos [x] (Math/cos (double x)))
(defn- exp [x] (Math/exp (double x)))

(defn- clamp [lo hi x] (max lo (min hi x)))

(defn- smoothstep [p] (* p p (- 3.0 (* 2.0 p))))

(defn- phase
  "A fixed angle per glyph id and salt, so jitter is deterministic and the
  same on both hosts. Small integers only: JavaScript has no 64-bit ints."
  [id salt]
  (let [h (mod (+ (* (mod id 65521) 7919) (* salt 104729)) 65521)]
    (* tau (/ h 65521.0))))

;; --- layout ------------------------------------------------------------------

(def ^:private line-height 1.35)

(defn default-advance
  "Every character 0.6 em wide: the fallback before a font is measured."
  [_c]
  0.6)

(defn- line-y [n] (if (zero? n) 0.0 (* -1.0 line-height n)))

(defn- tokens
  "Indexes of chars grouped into words (runs of non-whitespace) and single
  whitespace chars, in order."
  [chars]
  (->> (map-indexed vector chars)
       (partition-by (fn [[i c]] (if (str/blank? c) [:space i] :word)))
       (map #(mapv first %))))

(defn layout
  "One {:x :y} per char, left-aligned from x 0. A newline starts the next
  line; a word that would cross width-em moves whole to the next line, and
  a word wider than width-em breaks wherever it must. Line n has
  y = -1.35 n. advance is a fn char -> em width."
  [chars advance width-em]
  (let [chars (vec chars)
        width (double width-em)
        adv #(double (advance (nth chars %)))]
    (loop [toks (tokens chars), x 0.0, n 0, out (transient (vec (repeat (count chars) nil)))]
      (if-let [[i0 :as tok] (first toks)]
        (let [c (nth chars i0)]
          (cond
            (= "\n" c)
            (recur (rest toks) 0.0 (inc n) (assoc! out i0 {:x x :y (line-y n)}))

            (str/blank? c)
            (let [x' (+ x (adv i0))]
              (recur (rest toks) x' n (assoc! out i0 {:x x :y (line-y n)})))

            :else
            (let [w (reduce + (map adv tok))
                  [x n] (if (and (pos? x) (<= w width) (> (+ x w) width))
                          [0.0 (inc n)]
                          [x n])
                  [x n out] (reduce (fn [[x n out] i]
                                      (let [a (adv i)
                                            [x n] (if (and (pos? x) (> (+ x a) width))
                                                    [0.0 (inc n)]
                                                    [x n])]
                                        [(+ x a) n (assoc! out i {:x x :y (line-y n)})]))
                                    [x n out]
                                    tok)]
              (recur (rest toks) x n out))))
        (persistent! out)))))

(defn- end-position
  "Where the next character would go: after the last char, or at the start
  of the next line when the text ends with a newline."
  [chars positions advance]
  (if-let [{:keys [x y]} (peek positions)]
    (let [c (peek chars)]
      (if (= "\n" c)
        {:x 0.0 :y (- y line-height)}
        {:x (+ x (double (advance c))) :y y}))
    {:x 0.0 :y 0.0}))

;; --- motion ------------------------------------------------------------------
;; Each source of motion returns a delta map to add onto the base pose, or
;; nil when it is not moving at this instant.

(def ^:private birth-ms 400)

(def ^:private spring
  ;; damping 0.517 overshoots by ~15%; omega puts the envelope at ~1% of
  ;; rest by 400 ms, where the cutoff makes it exact.
  (let [zeta 0.517
        omega (/ 4.6 (* zeta birth-ms))]
    {:zeta zeta
     :decay (* zeta omega)
     :omega-d (* omega (Math/sqrt (- 1.0 (* zeta zeta))))
     :ratio (/ zeta (Math/sqrt (- 1.0 (* zeta zeta))))}))

;; --- style -------------------------------------------------------------------
;; A style is applied around the plain motion: real time is mapped onto the
;; plain shape's time, and the plain deltas are scaled, so :plain (tempo,
;; overshoot and amplitude 1, no sway, no steps) is the plain motion exactly.

(defn- plain-ms
  "How far into a plain motion d ms long a styled one is, u real ms after
  it began: slowed or hurried by tempo, and held in hard steps if any."
  [u d {:keys [tempo steps]}]
  (let [v (/ u tempo)]
    (if steps
      (* (Math/floor (/ (* v steps) d)) (/ (double d) steps))
      v)))

(def ^:private displacements #{:x :y :z :rx :ry :rz :scale})

(defn- amplified [delta amplitude]
  (reduce-kv (fn [m k v] (assoc m k (if (displacements k) (* amplitude v) v)))
             {} delta))

(defn- sway
  "A drift around the place of glyph id, u ms into a styled motion dur ms
  long, fading as (1-p)^2 so it is gone before the motion ends; nil for a
  style without one."
  [sway-params u dur id]
  (when-let [{:keys [amp period-ms]} sway-params]
    (let [f (- 1.0 (/ u dur))
          a (+ (/ (* tau u) period-ms) (phase id 5))]
      {:x (* amp f f (sin a))
       :y (* 0.6 amp f f (cos a))})))

(defn- performed
  "The deltas of a styled motion u real ms into its dur ms, given the plain
  delta for it."
  [delta {:keys [amplitude] :as ps} u dur id]
  (let [d (amplified delta amplitude)]
    (if-let [s (and (>= u 0) (sway (:sway ps) u dur id))]
      [d s]
      [d])))

;; --- births --------------------------------------------------------------------

(defn- spring-at [a]
  (if (neg? a)
    0.0
    (let [{:keys [decay omega-d ratio]} spring]
      (- 1.0 (* (exp (- (* decay a)))
                (+ (cos (* omega-d a)) (* ratio (sin (* omega-d a)))))))))

(defn- birth
  "The deltas of a glyph age ms after its birth in style params ps, or nil
  once it has landed. The spring's overshoot is kept in proportion to
  :overshoot (0 is a smoothstep glide)."
  [age {:keys [tempo overshoot] :as ps} id]
  (let [dur (* tempo birth-ms)]
    (when (< age dur)
      (let [a (plain-ms age birth-ms ps)
            sp (spring-at a)
            ease (smoothstep (clamp 0.0 1.0 (/ a birth-ms)))
            ;; written so overshoot 1 leaves the spring bit for bit
            s (+ sp (* (- overshoot 1.0) (- sp ease)))]
        (performed {:scale (- s 1.0)
                    :z (* 0.6 (- 1.0 s))
                    :opacity (- (clamp 0.0 1.0 (/ a 90.0)) 1.0)}
                   ps age dur id)))))

(def ^:private effect-ms
  "How long each effect moves a glyph; none exceeds 1400 ms."
  {:pop 400 :launch 1400 :shimmer 1400 :skitter 900 :quake 1000 :melt 1400
   :rewind 700 :fuse 900 :jitter 1400 :glow 1000 :wobble 1400})

(defn- effect-delta
  "The delta for one effect at local time u (0 <= u < its duration), at
  intensity k, for the glyph at position pos in the cue."
  [effect u k id pos]
  (let [d (double (effect-ms effect))
        p (/ u d)
        fade (- 1.0 p)]
    (case effect
      :pop {:scale (* 0.35 k (sin (* Math/PI p)))}

      :launch (if (< u 700)
                (let [q (/ u 700.0)
                      arc (* 4.0 q (- 1.0 q))]
                  {:y (* 1.2 k arc) :z (* 1.5 k arc)})
                (let [e (- 1.0 (/ (- u 700.0) 700.0))]
                  {:wobble (* k e e)}))

      :shimmer {:glow (* 0.8 k fade fade (Math/abs (sin (/ (* tau u) 350.0))))
                :y (* 0.05 k fade (sin (/ (* tau u) 700.0)))}

      :skitter {:x (* 0.12 k fade fade (sin (+ (/ (* tau u) 90.0) (phase id 1))))}

      :quake (let [a (+ (/ (* tau u) 70.0) (phase id 2))]
               {:x (* 0.15 k fade (sin a))
                :y (* 0.15 k fade (cos a))
                :glow (* k (max 0.0 (- 1.0 (/ u 600.0))))})

      :melt (let [droop (if (< u 600)
                          (sin (* 0.5 Math/PI (/ u 600.0)))
                          (let [q (/ (- u 600.0) 800.0)]
                            (* (- 1.0 q) (- 1.0 q) (cos (* 3.0 Math/PI q)))))]
              {:y (* -0.4 k droop) :rx (* 0.6 k droop)})

      :rewind {:rz (* (- tau) k (- 1.0 (smoothstep p)))}

      ;; the letters come in from a staggered layer in front of the line,
      ;; alternately above and below it; nothing moves sideways
      :fuse (let [apart (* k (- 1.0 (smoothstep p)))]
              {:z (* 0.6 apart (if (even? pos) 1.0 0.5))
               :y (* 0.2 apart (if (even? pos) 1.0 -1.0))})

      :jitter {:x (* 0.04 k fade (sin (+ (/ (* tau u) 60.0) (phase id 3))))
               :y (* 0.04 k fade (sin (+ (/ (* tau u) 75.0) (phase id 4))))}

      :glow {:glow (* 0.9 k (sin (* Math/PI p)))}

      :wobble {:wobble (* k fade fade)})))

(defn- stagger-step
  "25 ms between neighbours, compressed so the last glyph of any cue starts
  within 100 ms: 100 + the longest effect (1400) is the 1500 ms settle."
  [n]
  (if (> n 5) (/ 100.0 (dec n)) 25.0))

(def ^:private slowest-tempo
  (reduce max (map (comp :tempo style/params) style/styles)))

(def ^:private longest-cue-ms
  "The last glyph of a cue starts within 100 ms and moves at most 1400."
  (* slowest-tempo (+ 100 (reduce max (vals effect-ms)))))

(defn- cue-ms
  "The plain effect time for a styled cue u real ms into a glyph's part:
  below full overshoot its progress is eased, so elegant glides."
  [u d {:keys [overshoot] :as ps}]
  (let [v (plain-ms u d ps)
        glide (- 1.0 (min 1.0 overshoot))]
    (if (pos? glide)
      (let [p (/ v d)]
        (* d (+ p (* glide (- (smoothstep p) p)))))
      v)))

(defn- cue-deltas
  "id -> [delta ...] for every glyph a cue is moving at time t, each cue
  performed in the style style-of gives for its firing time."
  [cues t style-of]
  (reduce
   (fn [acc {:keys [glyph-ids cue at-ms]}]
     (let [since (- t (double at-ms))]
       (if (or (neg? since) (>= since longest-cue-ms))
         acc
         (let [{:keys [effect intensity]} cue
               {:keys [tempo] :as ps} (style/params (style-of at-ms))
               n (count glyph-ids)
               step (stagger-step n)
               d (effect-ms effect)
               dur (* d tempo)]
           (reduce
            (fn [acc [i id]]
              (let [pos (if (= :rewind effect) (- n 1 i) i)
                    u (- since (* step tempo pos))]
                (if (and (>= u 0) (< u dur))
                  (update acc id (fnil into [])
                          (performed (effect-delta effect (cue-ms u d ps) (double intensity) id i)
                                     ps u dur id))
                  acc)))
            acc
            ;; a glyph listed twice moves once, at its first position
            (vals (reduce (fn [m [i id]] (if (m id) m (assoc m id [i id])))
                          {} (map-indexed vector glyph-ids))))))))
   {}
   cues))

;; --- poses -------------------------------------------------------------------

(defn- line-of
  "The line number of a laid-out position (line n has y = -1.35 n)."
  [{:keys [y]}]
  (long (Math/round (/ (- (double y)) line-height))))

(def ^:private line-ease-ms 450)

(defn- line-progress
  "The current line (that of the last glyph, or 0) as a fractional line
  that eases up from the line before over 450 ms, timed from the birth of
  the first glyph on the current line. Exactly the current line once the
  ease is over, at line 0, and with reduced motion. Derived from the draft
  alone: deleting back to an earlier line has no line to ease from, so it
  snaps."
  [glyphs lines t reduced-motion?]
  (let [cur (if (seq lines) (peek lines) 0)]
    (if (or reduced-motion? (zero? cur))
      cur
      (let [first-born (some (fn [[g l]] (when (= l cur) (:born-ms g)))
                             (map vector glyphs lines))
            age (- t (double first-born))]
        (if (>= age line-ease-ms)
          cur
          (+ (dec cur) (smoothstep (clamp 0.0 1.0 (/ age line-ease-ms)))))))))

(def ^:private crawl-keep
  "Lines just above the current one that stay at full size and brightness."
  3)

(defn- recession
  "How a line recedes when it is more than crawl-keep lines above the
  current (fractional) line: back 0.9 em, dimmer by 0.18 (never below
  0.12) and tilted back 0.1 rad (never past 0.5) for each line beyond;
  nil when it is not receding."
  [current line]
  (let [k (- current line crawl-keep)]
    (when (pos? k)
      {:z (* -0.9 k)
       :opacity (max 0.12 (- 1.0 (* 0.18 k)))
       :rx (max -0.5 (* -0.1 k))})))

(defn- base-pose [current {:keys [id char]} {:keys [x y] :as position}]
  (merge {:id id :char char :ghost? false
          :x x :y y :z 0.0 :rx 0.0 :ry 0.0 :rz 0.0
          :scale 1.0 :opacity 1.0 :glow 0.0 :wobble 0.0}
         (recession current (line-of position))))

(def ^:private x-reach
  "How far, in em, motion may carry a letter sideways from its place: less
  than a space on either side, however many cues overlap on it."
  0.5)

(defn- apply-deltas
  "base plus every delta, with x held within x-reach of the base x."
  [{base-x :x :as base} deltas]
  (if (empty? deltas)
    base
    (-> (apply merge-with + base deltas)
        (update :x #(clamp (- base-x x-reach) (+ base-x x-reach) %))
        (update :scale #(max 0.0 %))
        (update :opacity #(clamp 0.0 1.0 %))
        (update :glow #(clamp 0.0 1.0 %))
        (update :wobble #(clamp 0.0 1.0 %)))))

(def ^:private ember-ms 600)

(defn- ember [{:keys [id char died-ms index]} t positions end]
  (let [age (- t (double died-ms))]
    (when (and (>= age 0) (< age ember-ms))
      (let [p (/ age ember-ms)
            {:keys [x y]} (get positions index end)]
        {:id id :char char :ghost? true
         :x x :y (+ y (* 1.5 p)) :z (* 0.3 p)
         :rx 0.0 :ry 0.0 :rz (* (if (even? id) 1.0 -1.0) tau p)
         :scale (- 1.0 (* 0.4 p)) :opacity (- 1.0 p) :glow (* 0.5 (- 1.0 p)) :wobble 0.0}))))

;; --- camera ------------------------------------------------------------------

(def ^:private fov-deg 40.0)

(def ^:private pitch
  "How far the camera looks down at the line, in radians (2 degrees): a
  steeper pitch shows more floor but lifts receded lines off the top."
  (* 2.0 (/ Math/PI 180.0)))

(def ^:private fill
  "The share of the view's width the column fills."
  0.75)

(defn- camera
  "Looking at the column's centre on the current (fractional) line, from
  in front and a little above, at the distance where the column fills 75%
  of the view's width for aspect (width / height)."
  [width-em aspect current]
  (let [width (double width-em)
        tx (/ width 2.0)
        ty (line-y current)
        half-fov (* 0.5 fov-deg (/ Math/PI 180.0))
        dist (/ width (* fill 2.0 (double aspect) (Math/tan half-fov)))]
    {:x tx :y (+ ty (* dist (sin pitch))) :z (* dist (cos pitch))
     :tx tx :ty ty :tz 0.0 :fov fov-deg}))

;; --- frame -------------------------------------------------------------------

(defn- style-of
  "fn from a time to the style in force then: the choice itself, or for
  :auto the style the rhythm of the glyphs born at or before it suggests."
  [style-choice glyphs]
  (if (= :auto style-choice)
    #(style/infer glyphs %)
    (constantly style-choice)))

(defn- birth-styles
  "born-ms -> params for the glyphs still being born at t: the style in
  force at each birth, read (for :auto) from the glyphs born strictly
  before it, so a later keystroke never restyles a letter in flight."
  [glyphs t style-choice]
  (let [longest (* slowest-tempo birth-ms)]
    (into {}
          (comp (map :born-ms)
                (filter #(< (- t %) longest))
                (distinct)
                (map (fn [b]
                       (let [before (filterv #(< (:born-ms %) b) glyphs)]
                         [b (style/params ((style-of style-choice before) b))]))))
          glyphs)))

(defn frame
  "The :ha/glyph-frame for draft at t-ms: one live pose per glyph, in
  order, then one ember per ghost younger than 600 ms, the caret and the
  camera, and the :style in force at t. opts: {:advance fn :width-em n
  :aspect n :reduced-motion? b :style-choice c}, aspect being the
  viewport's width over its height and c a :ha/style-choice (default
  :plain). Each birth and each cue is performed in the style in force when
  it happened, so a change of style never jerks a letter already moving.
  With reduced motion every live pose is its (possibly receded) resting
  place, there are no embers, and the camera is on the current line
  without easing."
  [draft t-ms {:keys [advance width-em aspect reduced-motion? style-choice]
               :or {advance default-advance width-em 40 aspect 1.6
                    style-choice :plain}}]
  (let [{:keys [glyphs ghosts cues]} draft
        chars (mapv :char glyphs)
        positions (layout chars advance width-em)
        end (end-position chars positions advance)
        t (double t-ms)
        current (line-progress glyphs (mapv line-of positions) t reduced-motion?)
        bases (mapv #(base-pose current %1 %2) glyphs positions)
        live (if reduced-motion?
               bases
               (let [moving (cue-deltas cues t (style-of style-choice glyphs))
                     born (birth-styles glyphs t style-choice)]
                 (mapv (fn [{:keys [id born-ms]} base]
                         (apply-deltas base (into (get moving id [])
                                                  (when-let [ps (born born-ms)]
                                                    (birth (- t (double born-ms)) ps id)))))
                       glyphs bases)))
        embers (when-not reduced-motion?
                 (keep #(ember % t positions end) ghosts))]
    {:t-ms t-ms
     :poses (into live embers)
     :caret end
     :camera (camera width-em aspect current)
     :style ((style-of style-choice glyphs) t)}))
