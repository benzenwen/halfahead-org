(ns halfahead.lab.synth
  "The text synthesizer of docs/product/score.md: plays a :ha/patch (curves
  on a letter's channels, optionally multiplied by an oscillator, staggered
  across the letters of a cue) and checks that a patch is safe to accept
  from anyone. Pure, and the same on both hosts."
  (:require [halfahead.schema :as schema]))

(def ^:private tau (* 2.0 Math/PI))

(defn- smoothstep [p] (* p p (- 3.0 (* 2.0 p))))

(defn- clamp01 [p] (max 0.0 (min 1.0 p)))

;; --- curves ------------------------------------------------------------------

;; The spring lab.pose uses for births, restated: damping 0.517 overshoots
;; by about 15%, and the envelope e^(-4.6 p) is at 1% of rest when p is 1.
(def ^:private spring-ratio
  (let [zeta 0.517] (/ zeta (Math/sqrt (- 1.0 (* zeta zeta))))))

(def ^:private spring-wd (/ 4.6 spring-ratio))

(defn- residual
  "How far the spring still is from rest at p: 1 at 0, exactly 0 at 1. The
  last fifth is faded out, because the bare spring is still 1% away at 1
  and a letter must land, not snap."
  [p]
  (let [a (* spring-wd p)
        r (* (Math/exp (* -4.6 p))
             (+ (Math/cos a) (* spring-ratio (Math/sin a))))]
    (if (> p 0.8)
      ;; 1 - smoothstep((p - 0.8) / 0.2), written from the far end so that
      ;; it is exactly 0 at p = 1
      (* r (smoothstep (* 5.0 (- 1.0 p))))
      r)))

(defn curve
  "The value of :ha/curve k at p, p clamped to [0 1]."
  [k p]
  (let [p (clamp01 (double p))]
    (case k
      :one 1.0
      :rise (smoothstep p)
      :fall (- 1.0 (smoothstep p))
      :fade (- 1.0 p)
      :fade2 (* (- 1.0 p) (- 1.0 p))
      :arch (Math/sin (* Math/PI p))
      :arc (* 4.0 p (- 1.0 p))
      :rise-sine (Math/sin (* 0.5 Math/PI p))
      :spring (- 1.0 (residual p))
      :spring-fall (residual p))))

;; --- oscillators -------------------------------------------------------------

(defn phase
  "A fixed angle per glyph id and salt, the one lab.pose gives, so jitter
  is deterministic and the same on both hosts. Small integers only:
  JavaScript has no 64-bit ints."
  [id salt]
  (let [h (mod (+ (* (mod id 65521) 7919) (* salt 104729)) 65521)]
    (* tau (/ h 65521.0))))

(defn- noise-step
  "The noise's value in [-1 1] for whole step number `step`."
  [step id salt]
  (let [h (mod (+ (* (mod step 65521) 7919)
                  (* (mod id 65521) 104729)
                  (* salt 12553)
                  1)
               65521)
        ;; a linear hash alone would climb in equal steps: scramble it
        h (mod (* h (+ h 3571)) 65521)
        h (mod (* h (+ h 9973)) 65521)]
    (- (/ h 32760.0) 1.0)))

(defn- noise
  "Deterministic value noise in [-1 1] at x steps (x = t / period): a hash
  of the step number floor(x), id and salt, each step eased (smoothstep)
  into the next. The same on both hosts because every product stays far
  below 2^53: the step number and the id are each reduced mod 65521
  before they are multiplied, and every intermediate hash is reduced mod
  65521 before it is multiplied again. The salt is NOT reduced; it is
  multiplied as given (the schema keeps it within 0 to 64)."
  [x id salt]
  (let [step (Math/floor x)
        a (noise-step step id salt)
        b (noise-step (+ step 1.0) id salt)]
    (+ a (* (- b a) (smoothstep (- x step))))))

(defn- wave-at
  "A unit wave at angle theta. The triangle has the cosine's period and
  phase: 1 at angle 0, -1 half a turn later."
  [wave theta]
  (case wave
    :sine (Math/sin theta)
    :cosine (Math/cos theta)
    :triangle (let [x (/ theta tau)]
                (- 1.0 (* 4.0 (Math/abs (- x (Math/floor (+ x 0.5)))))))))

(defn- phi
  "The phase a letter adds to an oscillator's angle."
  [rule pos id salt]
  (case rule
    :index (* pos 0.5 Math/PI)
    :id (phase id salt)
    0.0))

(defn- osc-at
  "The oscillator's value t ms after its modulation's window opened; 1
  when the modulation has none."
  [{:keys [wave period-ms salt abs?] rule :phase :as osc} t pos id]
  (if-not osc
    1.0
    (let [salt (or salt 0)
          v (if (= wave :noise)
              (noise (/ t period-ms) id salt)
              (wave-at wave (+ (/ (* tau t) period-ms) (phi rule pos id salt))))]
      (if abs? (Math/abs v) v))))

;; --- stagger -----------------------------------------------------------------

(defn- largest-rank [order n]
  (if (= order :centre-out) (quot (dec n) 2) (dec n)))

(defn- stagger-step
  "The ms between one rank and the next: :ms-per-glyph, compressed so the
  last letter to start does so within :max-ms."
  [{:keys [ms-per-glyph max-ms order]} n]
  (let [largest (largest-rank order n)
        max-ms (or max-ms 900)]
    (if (> (* largest ms-per-glyph) max-ms)
      (/ max-ms (double largest))
      ms-per-glyph)))

(defn start-ms
  "When the letter at position pos (0 based) of an n-letter cue starts,
  in ms after the cue: its rank times the stagger's step. The rank is pos
  for :forward, counted from the end for :reverse, and the distance from
  the middle for :centre-out (the middle letter or pair starts first). 0
  without a :stagger or when n <= 1."
  [{:keys [stagger]} pos n]
  (if (or (nil? stagger) (<= n 1))
    0
    (let [rank (case (:order stagger)
                 :reverse (- (dec n) pos)
                 :centre-out (quot (Math/abs (- (* 2 pos) (dec n))) 2)
                 pos)]
      (* rank (stagger-step stagger n)))))

(defn total-ms
  "How long the patch plays over an n-letter cue: :ms plus the latest
  start."
  [{:keys [ms stagger] :as patch} n]
  (if (or (nil? stagger) (<= n 1))
    ms
    (+ ms (* (largest-rank (:order stagger) n) (stagger-step stagger n)))))

;; --- playing -----------------------------------------------------------------

(defn- in-window?
  "Whether the modulation is on at x, the fraction of the patch's time."
  [{:keys [from to]} x]
  (and (<= (or from 0) x) (< x (or to 1))))

(defn- admits? [{:keys [each]} pos]
  (case each
    :even (even? pos)
    :odd (odd? pos)
    true))

(defn- signed-amount [{:keys [amount alternate?]} pos]
  (if (and alternate? (odd? pos)) (- amount) amount))

(defn delta
  "{channel delta} for the letter at position pos, with glyph id `id`, u
  ms after ITS OWN start (see start-ms), at intensity k. {} before the
  letter starts and from :ms on. Modulations on one channel add."
  [{:keys [ms mods]} u pos _n id k]
  (if (or (< u 0) (>= u ms))
    {}
    (let [x (/ u (double ms))]
      (reduce
       (fn [acc {:keys [channel from to osc] :as m}]
         (if (and (in-window? m x) (admits? m pos))
           (let [from (or from 0)
                 q (/ (- x from) (- (or to 1) from))
                 v (* (signed-amount m pos) k
                      (curve (:curve m) q)
                      (osc-at osc (- u (* from ms)) pos id))]
             (update acc channel (fnil + 0.0) v))
           acc))
       {}
       mods))))

;; --- the check ---------------------------------------------------------------
;; Intervals [lo hi] that contain every value a quantity takes over a span
;; of time, for every letter. Sampling instants would miss a 20 ms
;; oscillator's crest between two whole ms.

(def ^:private channels
  "The schema's order, which is the order problems are reported in."
  [:x :y :z :rx :ry :rz :scale :sx :sy :skew :weight
   :blur :opacity :glow :wobble :tint :fill])

(def bounds
  "channel -> [lo hi]: the delta a patch may reach at intensity 1."
  {:x [-0.5 0.5] :y [-2 2] :z [-3 3]
   :rx [-6.3 6.3] :ry [-6.3 6.3] :rz [-6.3 6.3]
   :scale [-0.9 3] :sx [-0.9 3] :sy [-0.9 3]
   :skew [-1 1] :weight [-1 1]
   :blur [0 1] :opacity [-0.95 0] :glow [0 1] :wobble [0 1]
   :tint [-1 1] :fill [-1 0]})

(defn- i+ [[a b] [c d]] [(+ a c) (+ b d)])

(defn- i* [[a b] [c d]]
  (let [p (* a c) q (* a d) r (* b c) s (* b d)]
    [(min p q r s) (max p q r s)]))

(defn- curve-range
  "An interval holding the curve over [q0 q1], both within [0 1]."
  [k q0 q1]
  (let [a (curve k q0)
        b (curve k q1)
        lo (min a b)
        hi (max a b)]
    (case k
      ;; one hump, 1 at the middle
      (:arch :arc) [lo (if (<= q0 0.5 q1) 1.0 hi)]
      ;; The spring rings, so its endpoints are widened by how far it can
      ;; stray between them: its slope is at most 20 e^(-4.6 q) (10.4 for
      ;; the residual, 8.8 more where the fade multiplies it).
      (:spring :spring-fall)
      (let [stray (* 0.5 (- q1 q0) 20.0 (Math/exp (* -4.6 q0)))]
        [(- lo stray) (+ hi stray)])
      ;; the rest are constant or monotone
      [lo hi])))

(defn- wave-range
  "The interval a unit wave covers between angles th0 <= th1."
  [wave th0 th1]
  (let [crest (if (= wave :sine) (* 0.5 Math/PI) 0.0)
        holds? (fn [c] (>= (Math/floor (/ (- th1 c) tau)) (/ (- th0 c) tau)))
        a (wave-at wave th0)
        b (wave-at wave th1)]
    [(if (holds? (+ crest Math/PI)) -1.0 (min a b))
     (if (holds? crest) 1.0 (max a b))]))

(defn- osc-range
  "An interval holding the oscillator for the letter at pos over window
  times [t0 t1]. Noise and :id phases differ for every glyph, so they are
  taken as their whole envelope."
  [{:keys [wave period-ms abs?] rule :phase :as osc} t0 t1 pos]
  (if-not osc
    [1.0 1.0]
    (let [[lo hi :as r] (if (or (= wave :noise) (= rule :id))
                          [-1.0 1.0]
                          (let [p (phi rule pos 0 0)]
                            (wave-range wave
                                        (+ (/ (* tau t0) period-ms) p)
                                        (+ (/ (* tau t1) period-ms) p))))]
      (cond
        (not abs?) r
        (>= lo 0.0) r
        (<= hi 0.0) [(- hi) (- lo)]
        :else [0.0 (max (- lo) hi)]))))

(defn- mod-range
  "An interval holding one modulation's delta at intensity 1 for the
  letter at pos, over patch times [a b] inside its window."
  [{:keys [from to osc] :as m} ms a b pos]
  (let [from (or from 0)
        width (- (or to 1) from)
        q (fn [u] (clamp01 (/ (- (/ u ms) from) width)))
        start (* from ms)
        amount (signed-amount m pos)]
    (i* [amount amount]
        (i* (curve-range (:curve m) (q a) (q b))
            (osc-range osc (- a start) (- b start) pos)))))

(defn- positions
  "Positions that stand for every letter: a quarter turn per position
  repeats every four, and parity (:each, :alternate?) every two."
  [mods]
  (cond
    (some (fn [{:keys [osc]}]
            (and (= :index (:phase osc)) (not= :noise (:wave osc))))
          mods)
    [0 1 2 3]

    (some (fn [{:keys [each alternate?]}]
            (or alternate? (= each :even) (= each :odd)))
          mods)
    [0 1]

    :else [0]))

(defn- cuts
  "Every whole ms of the patch and every window edge, ascending: between
  two neighbours the same modulations are on throughout."
  [ms mods]
  (->> mods
       (mapcat (fn [{:keys [from to]}] [(* (or from 0) ms) (* (or to 1) ms)]))
       (concat (range (inc ms)))
       (map double)
       distinct
       sort))

(defn- excursion
  "[excess value] when the interval reaches beyond the bounds, else nil."
  [[lo hi] [slo shi]]
  (let [below (- lo slo)
        above (- shi hi)
        [excess value] (if (> below above) [below slo] [above shi])]
    ;; written so that a NaN (infinite amounts cancelling) is an excursion
    (when-not (<= excess 1e-9)
      [excess value])))

(defn- out-of-bounds
  "channel -> [excess value ms] for the worst excursion of each channel
  that can leave its bounds."
  [{:keys [ms mods]}]
  (let [ps (positions mods)]
    (reduce
     (fn [worst [channel excess value at]]
       (if (some-> (worst channel) first (>= excess))
         worst
         (assoc worst channel [excess value at])))
     {}
     (for [[a b] (partition 2 1 (cuts ms mods))
           :let [x (/ (* 0.5 (+ a b)) ms)
                 live (filterv #(in-window? % x) mods)]
           pos ps
           [channel reach] (reduce (fn [acc m]
                                     (if (admits? m pos)
                                       (update acc (:channel m) (fnil i+ [0.0 0.0])
                                               (mod-range m ms a b pos))
                                       acc))
                                   {}
                                   live)
           :let [[excess value] (excursion (bounds channel) reach)]
           :when excess]
       [channel excess value (long a)]))))

(defn- not-at-rest
  "channel -> the delta farthest from 0 that the channel can have as u
  approaches :ms from below, for channels that do not come to rest. Only
  modulations whose window runs to the end are still on there, each at
  its curve's value at 1."
  [{:keys [ms mods]}]
  (let [ending (filter #(== 1 (or (:to %) 1)) mods)]
    (reduce
     (fn [worst [channel [lo hi]]]
       (let [far (if (> (Math/abs lo) (Math/abs hi)) lo hi)]
         (if (or (<= (Math/abs far) 1e-6)
                 (some-> (worst channel) Math/abs (>= (Math/abs far))))
           worst
           (assoc worst channel far))))
     {}
     (for [pos (positions mods)
           entry (reduce (fn [acc {:keys [from osc] :as m}]
                           (if (admits? m pos)
                             (let [t (- ms (* (or from 0) ms))
                                   amount (* (signed-amount m pos) (curve (:curve m) 1.0))]
                               (update acc (:channel m) (fnil i+ [0.0 0.0])
                                       (i* [amount amount] (osc-range osc t t pos))))
                             acc))
                         {}
                         ending)]
       entry))))

(defn problems
  "[] for a patch that is safe to play, else a vector of problems:

    {:problem :invalid :explanation e}   not a :ha/patch; nothing else is
                                         checked
    {:problem :not-at-rest :channel c :value v}
                                         c is still v from rest as the
                                         patch ends (the limit from below)
    {:problem :out-of-bounds :channel c :value v :ms u :bounds [lo hi]}
                                         c can reach v, outside `bounds`,
                                         in the ms that starts at u (the
                                         worst such v)

  All :not-at-rest come first, then all :out-of-bounds, each in the
  schema's channel order. A patch with no problems ends at rest and never
  leaves its bounds, at intensity 1 or less, for every letter of every
  cue and at every instant, not only at the ones sampled: positions 0 to
  3 stand for every position, and time is covered ms by ms with
  intervals. An oscillator with :phase :id or :wave :noise differs for
  every glyph, so it is taken as its envelope ([-1 1], or [0 1] with
  :abs?), and modulations on one channel are summed as intervals.

  The price of that soundness is that a patch may be refused although no
  real letter would leave bounds (two :id-phase modulations that would
  cancel, or a peak within rounding of a bound). :value is therefore a
  bound on what a letter can reach, not always a value some letter takes.

  Never throws, whatever it is given."
  [patch]
  (try
    (if-let [explanation (schema/explain :ha/patch patch)]
      [{:problem :invalid :explanation explanation}]
      (let [displaced (not-at-rest patch)
            beyond (out-of-bounds patch)]
        (into (vec (for [c channels :when (contains? displaced c)]
                     {:problem :not-at-rest :channel c :value (displaced c)}))
              (for [c channels
                    :let [[_ value at] (beyond c)]
                    :when (contains? beyond c)]
                {:problem :out-of-bounds :channel c :value value
                 :ms at :bounds (bounds c)}))))
    (catch #?(:clj Throwable :cljs :default) e
      [{:problem :invalid :explanation (str e)}])))
