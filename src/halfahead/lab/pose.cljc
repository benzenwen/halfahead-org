(ns halfahead.lab.pose
  "The seam between the typing lab's draft and whatever draws it: given a
  draft and a time, where every letter stands, how it is turned, how big,
  bright and wobbly it is. Every frame is computed from scratch, never
  simulated, so a renderer may ask for any moment in any order, and every
  motion ends exactly on the layout within 5000 ms of its cause, in every
  style and at any style point (plain births take 400 ms, ghosts 600 ms, and a cue as long as its
  gesture's patch plays, at most 2400 ms plus a 900 ms stagger, see
  lab.gestures; a style stretches or squeezes births and cues by its tempo,
  at most 1.5, see lab.style, and a cue's own :tempo only hurries it), and
  within 5000 ms of a direction (see lab.director) for its shot. Apart from
  a :card, which takes a phrase out of the line on purpose, no letter ever
  strays more than half an em sideways from its place, so a word's motion
  stays out of its neighbours' room: effects that gather or scatter use
  depth and height.
  The camera is part of every frame: it looks at the line being written,
  easing to a new line over 450 ms, and lines more than three above it
  recede up and back into the dark like an end-credits crawl, so a long
  draft uses depth instead of running off the screen. Units are em; angles
  are radians.
  Every frame is performed at the reveal level in force (see lab.reveal):
  at 0 only a third of each birth's pop, in the plane, and no cues; at 1
  cues in the plane; at 2 everything in depth, as the house lights go down
  and the letters peel off the page; at 3 shots and the crawl too. Each
  birth, cue and shot is performed at the level in force when it happened,
  so reaching a level never jerks a letter already moving; what a level
  adds (the peel, the crawl) is eased in and settled within 900 ms of it.
  From level 1 a frame may be given the draft's dress (lab.dress): what
  its letters wear at rest. Dress changes the resting place and look that
  every motion above starts from and returns to, so all of it holds with
  dress: a sized letter takes the room it needs in the layout, and cues
  and shots perform on top of the dressed rest."
  (:require [clojure.string :as str]
            [halfahead.lab.dress :as dress]
            [halfahead.lab.gestures :as gestures]
            [halfahead.lab.reveal :as reveal]
            [halfahead.lab.style :as style]
            [halfahead.lab.synth :as synth]))

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

(defn- layout-widths
  "layout, given the em width of each char (widths, a vector) in place of
  a fn of the char: a dressed letter may be wider or narrower than its
  character is."
  [chars widths width-em]
  (let [width (double width-em)
        adv #(double (nth widths %))]
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

(defn layout
  "One {:x :y} per char, left-aligned from x 0. A newline starts the next
  line; a word that would cross width-em moves whole to the next line, and
  a word wider than width-em breaks wherever it must. Line n has
  y = -1.35 n. advance is a fn char -> em width."
  [chars advance width-em]
  (let [chars (vec chars)]
    (layout-widths chars (mapv advance chars) width-em)))

(defn- end-position
  "Where the next character would go: after the last char (widths gives
  how wide each is), or at the start of the next line when the text ends
  with a newline."
  [chars positions widths]
  (if-let [{:keys [x y]} (peek positions)]
    (if (= "\n" (peek chars))
      {:x 0.0 :y (- y line-height)}
      {:x (+ x (double (peek widths))) :y y})
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

(def ^:private displacements #{:x :y :z :rx :ry :rz :scale :sx :sy :skew})

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

(def ^:private slowest-tempo
  "The slowest any style plays: the named styles' and, by lab.style's
  curve, any point's (from-point never exceeds Elegant's 1.5)."
  (reduce max (map (comp :tempo style/params) style/styles)))

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
  "id -> [delta ...] for every glyph a cue is moving at time t: the cue's
  gesture played by lab.synth, each cue performed with the params params-of
  gives for its firing time, which also stretches the stagger. A cue's
  own :tempo multiplies the style's, for the stagger, the duration and the
  progress alike, so a hurried gesture is the same motion played faster."
  [cues t params-of]
  (reduce
   (fn [acc {:keys [glyph-ids cue at-ms] hurry :tempo}]
     (let [since (- t (double at-ms))
           {:keys [effect intensity]} cue
           patch (gestures/patch effect)
           n (count glyph-ids)
           total (synth/total-ms patch n)]
       ;; the slowest tempo first: finding the style in force (for :auto, a
       ;; reading of the rhythm) is only worth it for a cue that may be live
       ;; (a cue's own :tempo is at most 1, so it only ends sooner)
       (if (or (neg? since) (>= since (* slowest-tempo total)))
         acc
         (let [{:keys [tempo] :as ps} (cond-> (params-of at-ms)
                                        hurry (update :tempo * hurry))
               ms (:ms patch)
               dur (* ms tempo)]
           (reduce
            (fn [acc [i id]]
              (let [u (- since (* tempo (synth/start-ms patch i n)))]
                (if (and (>= u 0) (< u dur))
                  (update acc id (fnil into [])
                          (performed (synth/delta patch (cue-ms u ms ps) i n id (double intensity))
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

(defn- eased-recession
  "recession weighted w of the way in from none (w 1 is it exactly)."
  [w current line]
  (when (pos? w)
    (when-let [{:keys [z opacity rx] :as r} (recession current line)]
      (if (= 1.0 w)
        r
        {:z (* w z) :opacity (+ 1.0 (* w (- opacity 1.0))) :rx (* w rx)}))))

(defn- base-pose [current crawl {:keys [id char]} {:keys [x y] :as position}]
  (merge {:id id :char char :ghost? false
          :x x :y y :z 0.0 :rx 0.0 :ry 0.0 :rz 0.0
          :scale 1.0 :opacity 1.0 :glow 0.0 :wobble 0.0}
         (eased-recession crawl current (line-of position))))

(def ^:private x-reach
  "How far, in em, motion may carry a letter sideways from its place: less
  than a space on either side, however many cues overlap on it."
  0.5)

(def ^:private score-channels
  "channel -> [rest lo hi] for the channels that came with the score. A
  pose carries one only while it is away from rest, so a frame in which
  none is moved is the frame it always was."
  {:sx [1.0 0.0 ##Inf]
   :sy [1.0 0.0 ##Inf]
   :skew [0.0 ##-Inf ##Inf]
   :weight [0.0 -1.0 1.0]
   :blur [0.0 0.0 1.0]
   :fill [1.0 0.0 1.0]})

(defn- off-rest
  "pose, whose score channels hold summed deltas so far, with each at its
  value (rest plus the delta, kept in range) or absent when that is rest."
  [pose]
  (reduce-kv (fn [p channel [at-rest lo hi]]
               (if-let [delta (get p channel)]
                 (let [v (clamp lo hi (+ at-rest delta))]
                   (if (== v at-rest) (dissoc p channel) (assoc p channel v)))
                 p))
             pose
             score-channels))

(defn- apply-deltas
  "base plus every delta, with x held within x-reach of the base x and
  every other channel within its range."
  [{base-x :x :as base} deltas]
  (if (empty? deltas)
    base
    (-> (apply merge-with + base deltas)
        (update :x #(clamp (- base-x x-reach) (+ base-x x-reach) %))
        (update :scale #(max 0.0 %))
        (update :opacity #(clamp 0.0 1.0 %))
        (update :glow #(clamp 0.0 1.0 %))
        (update :wobble #(clamp 0.0 1.0 %))
        off-rest
        ;; a tint mixes towards the letter's ink, which only dress gives
        (as-> p (if (:ink p)
                  (update p :tint #(clamp 0.0 1.0 %))
                  (dissoc p :tint))))))

(def ^:private ember-ms 600)

(defn- ember
  "A ghost drifting up and away as it fades; in the plane below level 2,
  and without its spin at level 0."
  [{:keys [id char died-ms index]} t positions end level]
  (let [age (- t (double died-ms))]
    (when (and (>= age 0) (< age ember-ms))
      (let [p (/ age ember-ms)
            {:keys [x y]} (get positions index end)]
        {:id id :char char :ghost? true
         :x x :y (+ y (* 1.5 p)) :z (if (< level 2) 0.0 (* 0.3 p))
         :rx 0.0 :ry 0.0 :rz (if (zero? level) 0.0 (* (if (even? id) 1.0 -1.0) tau p))
         :scale (- 1.0 (* 0.4 p)) :opacity (- 1.0 p) :glow (* 0.5 (- 1.0 p)) :wobble 0.0}))))

;; --- dress -------------------------------------------------------------------
;; Dress is applied to the letter at rest, before any motion, so everything
;; that moves a letter starts from and returns to its dressed pose.

(def ^:private dress-ease-ms 500)

(defn- wearing
  "[[placed-dress w] ...]: how far in each placed dress is at t, 0 to 1,
  easing over 500 ms from when it was first seen; all the way with reduced
  motion, and for dress nobody has kept a time for."
  [{:keys [placed seen]} t reduced-motion?]
  (mapv (fn [{:keys [as glyph-ids] :as d}]
          (let [since (get seen [as (first glyph-ids)])]
            [d (if (or reduced-motion? (nil? since))
                 1.0
                 (smoothstep (clamp 0.0 1.0 (/ (- t since) dress-ease-ms))))]))
        placed))

(defn- with-markers
  "worn, and after it a whisper of the same weight on the :markers of each
  dress that has them: the asterisks of a dressed stress have done their
  work, so they recede with it. A marker inside a placed dress keeps that
  dress instead, and one shared by two stresses recedes with the first."
  [worn]
  (first
   (reduce (fn [[out taken] [{:keys [markers]} w]]
             (if-let [ids (seq (remove taken markers))]
               [(conj out [{:as :whisper :glyph-ids (vec ids)} w]) (into taken ids)]
               [out taken]))
           [worn (into #{} (mapcat (comp :glyph-ids first)) worn)]
           worn)))

(defn- eased-sizes
  "{glyph-id size} at this instant: each sized dress w of the way from 1
  to its size, a glyph in two taking the one further from 1."
  [worn glyphs]
  (reduce (fn [m [d w]]
            (reduce-kv (fn [m id size]
                         (let [s (+ 1.0 (* w (- size 1.0)))]
                           (if (> (abs (- s 1.0)) (abs (- (get m id 1.0) 1.0)))
                             (assoc m id s)
                             m)))
                       m
                       (dress/sizes [d] glyphs)))
          {}
          worn))

(defn- sized
  "pose scaled to size and kept on its baseline at its left edge, given
  that a glyph scales about the middle of its advance and 0.35 em above
  its baseline (as a :card assumes)."
  [pose size advance]
  (-> pose
      (assoc :scale size)
      (update :x + (* 0.5 (- size 1.0) advance))
      (update :y + (* 0.35 (- size 1.0)))))

(defn- dressed
  "bases with each glyph's size and the look of every dress it wears."
  [bases worn sizes advance light]
  (let [looks-of (reduce (fn [m [{:keys [as glyph-ids]} w]]
                           (reduce #(update %1 %2 (fnil conj []) [(dress/looks as) w])
                                   m glyph-ids))
                         {}
                         worn)]
    (mapv (fn [{:keys [id char] :as base}]
            (reduce (fn [p [look w]] (dress/at-rest p look w light))
                    (if-let [size (sizes id)]
                      (sized base size (double (advance char)))
                      base)
                    (looks-of id)))
          bases)))

;; --- camera ------------------------------------------------------------------

(def ^:private fov-deg 40.0)

(def ^:private stage-pitch
  "How far the camera looks down at the line on the dark stage, in radians
  (2 degrees): a steeper pitch shows more floor but lifts receded lines off
  the top. With the house lights up it looks straight on, like a page."
  (* 2.0 (/ Math/PI 180.0)))

(def ^:private fill
  "The share of the view's width the column fills."
  0.75)

(defn- camera
  "Looking at the column's centre on the current (fractional) line, from
  in front and pitch radians above, at the distance where the column fills
  75% of the view's width for aspect (width / height)."
  [width-em aspect current pitch]
  (let [width (double width-em)
        tx (/ width 2.0)
        ty (line-y current)
        half-fov (* 0.5 fov-deg (/ Math/PI 180.0))
        dist (/ width (* fill 2.0 (double aspect) (Math/tan half-fov)))]
    {:x tx :y (+ ty (* dist (sin pitch))) :z (* dist (cos pitch))
     :tx tx :ty ty :tz 0.0 :fov fov-deg}))

;; --- shots -------------------------------------------------------------------
;; A direction's shot is performed on top of everything else, as a blend
;; from the frame without it towards the shot's pose, so at weight 0 the
;; frame is exactly what it would have been. At most one shot plays at a
;; time: the latest one begun.

(def ^:private card-hold-ms 1200)
(def ^:private card-fold-ms 400)
(def ^:private card-beat-ms 220)

(def ^:private card-reveal-ms
  "The most time a card's words take to arrive, so a card of any length
  ends within 4400 ms: the beat shortens for long phrases."
  (- 4400 card-hold-ms card-fold-ms))

(def ^:private card-span
  "The share of the view's width a card's phrase spans."
  0.7)

(def ^:private card-z
  "How far in front of the line a card stands. Glyph quads are transparent,
  so this only orders the card ahead of the line: the rest of the line
  would still show between its strokes, which is why it steps back."
  0.3)

(def ^:private card-backdrop
  "Where the rest of the line steps back to while a card holds the stage:
  this faint at most, and this many em behind where it would be, so
  perspective shrinks it towards the vanishing point and the card owns the
  frame."
  {:opacity 0.08 :recede 2.0})

(def ^:private camera-moves
  "Rise, hold and fall in ms, and how far the camera's distance changes."
  {:push {:rise 500 :hold 700 :fall 500 :by -0.35}
   :pull {:rise 600 :hold 900 :fall 600 :by 0.4}})

(defn- envelope
  "0 before start, a smoothstep up over rise ms to 1, held until hold-end,
  a smoothstep down to 0 over fall ms; exactly 0 from then on."
  [u start rise hold-end fall]
  (cond
    (or (< u start) (>= u (+ hold-end fall))) 0.0
    (< u (+ start rise)) (smoothstep (/ (- u start) (double rise)))
    (< u hold-end) 1.0
    :else (smoothstep (- 1.0 (/ (- u hold-end) (double fall))))))

(defn- card-words
  "The present glyphs of a card, in draft order, as [[index glyph] ...]
  per word: glyphs next to each other in the draft share a word."
  [glyphs ids]
  (let [in (set ids)]
    (->> (keep-indexed (fn [i g] (when (in (:id g)) [i g])) glyphs)
         (reduce (fn [words [i :as ig]]
                   (if (= (dec i) (first (peek (peek words))))
                     (conj (pop words) (conj (peek words) ig))
                     (conj words [ig])))
                 []))))

(defn- card-beat [n-words] (min card-beat-ms (/ card-reveal-ms (double n-words))))

(defn- card-ms [n-words] (+ (* n-words (card-beat n-words)) card-hold-ms card-fold-ms))

(defn- shot-ms
  "How long direction d's shot plays, given its present glyphs; nil for a
  shot that does nothing."
  [{:keys [shot glyph-ids]} glyphs]
  (case shot
    :card (let [n (count (card-words glyphs glyph-ids))]
            (when (pos? n) (card-ms n)))
    (:push :pull) (let [{:keys [rise hold fall]} (camera-moves shot)] (+ rise hold fall))
    :line nil))

(defn- active-shot
  "The direction whose shot plays at t, if any: the latest begun, with a
  glyph still present, while it lasts."
  [directions glyphs t]
  (let [live (into #{} (map :id) glyphs)
        d (->> directions
               (filter #(and (not= :line (:shot %)) (<= (:at-ms %) t) (some live (:glyph-ids %))))
               (reduce (fn [a b] (if (and a (> (:at-ms a) (:at-ms b))) a b)) nil))
        dur (when d (shot-ms d glyphs))]
    (when (and dur (< (- t (:at-ms d)) dur))
      d)))

(defn- lerp [a b w] (+ a (* w (- b a))))

(defn- blend
  "pose moved weight w of the way to the values in target."
  [pose target w]
  (if (zero? w)
    pose
    (reduce-kv (fn [p k v] (assoc p k (lerp (get p k) v w))) pose target)))

(defn- card
  "Live poses with the card of direction d performed u ms after it began:
  each word, a beat after the one before, leaves the line for the centre
  of the view (the camera's target), set large enough that the phrase
  spans 70% of the view's width; held 1200 ms, folded back over 400 ms,
  while every other glyph fades to at most 0.08 and recedes 2 em behind
  where it would be, by the same envelope, then returns exactly. Positions assume a glyph scales
  about the middle of its advance and 0.35 em above its baseline."
  [poses glyphs d u {:keys [tx ty tz]} {:keys [advance width-em]}]
  (let [words (card-words glyphs (:glyph-ids d))
        n (count words)
        beat (card-beat n)
        hold-end (+ (* n beat) card-hold-ms)
        adv #(double (advance (:char %)))
        space (double (advance " "))
        ;; [index offset advance word], offsets in em at scale 1
        laid (first (reduce (fn [[out x] [k word]]
                              (let [x (if (pos? k) (+ x space) x)]
                                (reduce (fn [[out x] [i g]]
                                          [(conj out [i x (adv g) k]) (+ x (adv g))])
                                        [out x] word)))
                            [[] 0.0]
                            (map-indexed vector words)))
        width (let [[_ x a] (peek laid)] (+ x a))
        view (/ (double width-em) fill)
        s (if (pos? width) (clamp 0.05 8.0 (/ (* card-span view) width)) 1.0)
        left (- tx (* 0.5 s width))
        in-card (into {} (map (fn [[i x a k]] [i [x a k]])) laid)
        dim (envelope u 0 card-beat-ms hold-end card-fold-ms)]
    (vec (map-indexed
          (fn [i p]
            (if-let [[x a k] (in-card i)]
              (blend p
                     {:x (+ left (* s x) (* 0.5 (- s 1.0) a)) :y (- ty 0.35) :z (+ tz card-z)
                      :rx 0.0 :ry 0.0 :rz 0.0 :scale s :opacity 1.0}
                     (envelope u (* k beat) beat hold-end card-fold-ms))
              (blend p {:opacity (min (:opacity p) (:opacity card-backdrop))
                        :z (- (:z p) (:recede card-backdrop))}
                     dim)))
          poses))))

(defn- moved-camera
  "cam during a push (35% nearer, looking at the phrase's centre) or a
  pull (40% further back) u ms after it began."
  [{:keys [x y z tx ty tz] :as cam} shot u centre]
  (let [{:keys [rise hold fall by]} (camera-moves shot)
        w (envelope u 0 rise (+ rise hold) fall)]
    (if (zero? w)
      cam
      (let [[cx cy cz] (if (= :push shot) centre [tx ty tz])
            tx' (lerp tx cx w), ty' (lerp ty cy w), tz' (lerp tz cz w)
            k (+ 1.0 (* by w))]
        (assoc cam
               :tx tx' :ty ty' :tz tz'
               :x (+ tx' (* k (- x tx))) :y (+ ty' (* k (- y ty))) :z (+ tz' (* k (- z tz))))))))

(defn- phrase-centre
  "The mean of the resting places of d's present glyphs, on the text plane."
  [glyphs positions d]
  (let [in (set (:glyph-ids d))
        ps (keep (fn [[g p]] (when (in (:id g)) p)) (map vector glyphs positions))
        n (double (count ps))]
    [(/ (reduce + (map :x ps)) n) (/ (reduce + (map :y ps)) n) 0.0]))

(defn- perform-shot
  "[live-poses camera] with the shot playing at t performed, if any."
  [live cam {:keys [directions glyphs]} positions t opts]
  (if-let [d (active-shot directions glyphs t)]
    (let [u (- t (double (:at-ms d)))]
      (case (:shot d)
        :card [(card live glyphs d u cam opts) cam]
        (:push :pull) [live (moved-camera cam (:shot d) u (phrase-centre glyphs positions d))]))
    [live cam]))

;; --- frame -------------------------------------------------------------------

(defn- style-of
  "fn from a time to the style in force then: the choice itself, or for
  :auto the style the rhythm of the glyphs born at or before it suggests."
  [style-choice glyphs]
  (if (= :auto style-choice)
    #(style/infer glyphs %)
    (constantly style-choice)))

(defn- params-of
  "fn from a time to the motion parameters in force then: a point's own
  (lab.style/from-point) when the choice is a :ha/style-point, else those
  of the named style in force."
  [style-choice glyphs]
  (if (map? style-choice)
    (constantly (style/from-point style-choice))
    (comp style/params (style-of style-choice glyphs))))

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
                         [b ((params-of style-choice before) b)]))))
          glyphs)))

(def ^:private flat-births
  "How much of a birth's displacement level 0 keeps: the gentlest pop."
  (/ 1.0 3.0))

(defn- at-level
  "delta as a level performs it: at 0 a third of its displacement, in the
  plane and unturned; at 1 in the plane (turning in it only); from 2 as it
  is."
  [level delta]
  (case level
    0 (dissoc (amplified delta flat-births) :z :rx :ry :rz)
    1 (dissoc delta :z :rx :ry)
    delta))

(def ^:private peel-ms 700)
(def ^:private peel-em 0.6)

(defn- peel
  "The lift of a letter born by t2 (when level 2 was reached by use or a
  wand) off the flat page, t ms in: up 0.6 em and back down within 700 ms;
  nil outside it."
  [t2 born-ms t]
  (when t2
    (let [u (- t t2)]
      (when (and (<= born-ms t2) (>= u 0) (< u peel-ms))
        {:z (* peel-em (sin (* Math/PI (/ u peel-ms))))}))))

(def ^:private crawl-ease-ms 900)

(defn- crawl-weight
  "How far the crawl has come in at level, 0 to 1: eased over 900 ms from
  reaching level 3, unless the lab started there or motion is reduced."
  [level t3 started-there? reduced-motion? t]
  (cond
    (< level 3) 0.0
    (or started-there? reduced-motion?) 1.0
    :else (smoothstep (clamp 0.0 1.0 (/ (- t t3) crawl-ease-ms)))))

(defn- inked
  "live with the reading head's ink: each pose whose glyph fills gives
  less than 1 gets that :fill, times any a cue gave it."
  [live fills]
  (mapv (fn [{:keys [id] :as pose}]
          (let [v (get fills id 1.0)]
            (if (< v 1)
              (assoc pose :fill (* v (:fill pose 1.0)))
              pose)))
        live))

(defn frame
  "The :ha/glyph-frame for draft at t-ms: one live pose per glyph, in
  order, then one ember per ghost younger than 600 ms, the caret and the
  camera, the :style in force at t, and the :reveal level and house
  lights. opts: {:advance fn :width-em n :aspect n :reduced-motion? b
  :style-choice c :reveal-opts r :fills m :dress d}, aspect being the
  viewport's width over its height, c a :ha/style-choice (default :plain)
  or a :ha/style-point (then births and cues move by lab.style/from-point
  of it, and the frame has no :style),
  r the opts for lab.reveal (default {:start-level 3}, the full stage), m
  a map glyph-id -> how much of the glyph a reading head has inked (see
  lab.reading; absent or nil when nothing is being read) and d the
  draft's dress, {:placed [...] :seen {...} :light {:x :y} or nil} as
  lab.dress places and remembers it, the light being where the pointer is
  on the text plane, in em. From level 1 each dress eases in over 500 ms
  from when it was first seen: a sized letter's advance is multiplied by
  its size, so the layout, the wrapping and the caret make room for it,
  and it is scaled on its baseline from its left edge; inks, weights and
  the glint are those of lab.dress/at-rest. The :markers of a placed
  dress (the asterisks around a stress) wear :whisper at that dress's
  weight, unless they lie inside another placed dress. At level 0, and with no
  :dress, frames are exactly as without it. Births, cues and
  shots are performed at the level in force when they happened (see the
  namespace doc); on reaching level 2 by use or a wand every letter then
  present peels up 0.6 em off the page and back over 700 ms; the camera
  pitches down with the lights; the crawl eases in over 900 ms at level
  3. Each birth and each cue is performed in the style in force when
  it happened, so a change of style never jerks a letter already moving.
  The draft's :directions are performed as shots (a :card takes the
  phrase centre stage, a :push or :pull moves the camera), each over by
  4500 ms after its :at-ms. With reduced motion every live pose is its
  (possibly receded, fully dressed) resting place, there are no embers, shots or fills, and the
  camera is on the current line without easing; levels still advance, but
  the lights are at the level's value at once and nothing peels."
  [draft t-ms {:keys [advance width-em aspect reduced-motion? style-choice reveal-opts fills]
               draft-dress :dress
               :or {advance default-advance width-em 40 aspect 1.6
                    style-choice :plain reveal-opts {:start-level 3}}}]
  (let [{:keys [glyphs ghosts cues directions]} draft
        chars (mapv :char glyphs)
        t (double t-ms)
        {:keys [level lights reached]} (reveal/state draft t reveal-opts)
        worn (when (and draft-dress (>= level 1))
               (with-markers (wearing draft-dress t reduced-motion?)))
        sizes (if worn (eased-sizes worn glyphs) {})
        widths (mapv (fn [{:keys [id char]}]
                       (let [a (advance char)]
                         (if-let [size (sizes id)] (* size a) a)))
                     glyphs)
        positions (layout-widths chars widths width-em)
        end (end-position chars positions widths)
        lights (if reduced-motion? (if (>= level 2) 1.0 0.0) lights)
        level-when #(reveal/level-at reached reveal-opts %)
        start-level (level-when ##-Inf)
        current (line-progress glyphs (mapv line-of positions) t reduced-motion?)
        crawl (crawl-weight level (reached 3) (>= start-level 3) reduced-motion? t)
        bases (cond-> (mapv #(base-pose current crawl %1 %2) glyphs positions)
                worn (dressed worn sizes advance (:light draft-dress)))
        live (if reduced-motion?
               bases
               (let [cue-style (params-of style-choice glyphs)
                     cues-at (group-by #(min 2 (level-when (:at-ms %))) cues)
                     moving (merge-with into
                                        (update-vals (cue-deltas (cues-at 1 []) t cue-style)
                                                     #(mapv (partial at-level 1) %))
                                        (cue-deltas (cues-at 2 []) t cue-style))
                     born (birth-styles glyphs t style-choice)
                     t2 (when (< start-level 2) (reached 2))]
                 (mapv (fn [{:keys [id born-ms]} base]
                         (apply-deltas base (cond-> (get moving id [])
                                              (born born-ms)
                                              (into (map (partial at-level (level-when born-ms)))
                                                    (birth (- t (double born-ms)) (born born-ms) id))
                                              true
                                              (into (keep identity) [(peel t2 born-ms t)]))))
                       glyphs bases)))
        embers (when-not reduced-motion?
                 (keep #(ember % t positions end level) ghosts))
        cam (camera width-em aspect current (* lights stage-pitch))
        staged (assoc draft :directions (filterv #(>= (level-when (:at-ms %)) 3) directions))
        [live cam] (if reduced-motion?
                     [live cam]
                     (perform-shot live cam staged positions t {:advance advance :width-em width-em}))
        live (if (and fills (not reduced-motion?)) (inked live fills) live)]
    (cond-> {:t-ms t-ms
             :poses (into live embers)
             :caret end
             :camera cam
             :reveal {:level level :lights lights}}
      ;; a point has no name to report
      (not (map? style-choice)) (assoc :style ((style-of style-choice glyphs) t)))))
