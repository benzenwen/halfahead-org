(ns halfahead.lab.style
  "How the whole draft moves: a :ha/style names a set of physics parameters
  that every motion in lab.pose is tuned by, and :auto infers one from the
  writer's own typing rhythm. The rhythm is read from the glyphs' birth
  times in the draft already in memory; nothing is recorded, kept or sent.
  A style also sits at a :ha/style-point, four axes that the score's marks
  are performed by; any point has motion parameters of its own.
  Pure, the same on both hosts.")

(def styles
  "Every :ha/style."
  [:plain :elegant :caffeinated :drunken :coarse])

(def ^:private by-style
  ;; :tempo     multiplies every duration (time runs 1/tempo as fast)
  ;; :overshoot how much of the birth spring's overshoot is kept; below 1
  ;;            a cue's progress is also eased, so 0 is a pure glide
  ;; :amplitude multiplies every displacement
  ;; :sway      {:amp em :period-ms n}: drift while moving, fading to rest
  ;; :steps     motion in that many hard steps, no easing; nil is smooth
  {:plain       {:tempo 1.0 :overshoot 1.0 :amplitude 1.0 :sway nil :steps nil}
   :elegant     {:tempo 1.5 :overshoot 0.0 :amplitude 0.8 :sway nil :steps nil}
   :caffeinated {:tempo 0.6 :overshoot 1.8 :amplitude 1.15
                 :sway {:amp 0.03 :period-ms 45} :steps nil}
   :drunken     {:tempo 1.4 :overshoot 1.6 :amplitude 1.1
                 :sway {:amp 0.12 :period-ms 900} :steps nil}
   :coarse      {:tempo 0.5 :overshoot 1.0 :amplitude 1.25 :sway nil :steps 3}})

(defn params
  "{:tempo :overshoot :amplitude :sway :steps} for a :ha/style. :plain is
  the lab's unstyled motion exactly."
  [style]
  (or (by-style style)
      (throw (ex-info (str "Unknown style " (pr-str style))
                      {:type :ha/unknown-style :style style}))))

;; --- the style space -------------------------------------------------------

(def ^:private points
  {:plain       {:energy 0.5  :flourish 0.35 :softness 0.5 :warmth 0.5}
   :elegant     {:energy 0.2  :flourish 0.45 :softness 0.9 :warmth 0.7}
   :caffeinated {:energy 0.95 :flourish 0.75 :softness 0.2 :warmth 0.5}
   :drunken     {:energy 0.4  :flourish 0.65 :softness 0.8 :warmth 0.6}
   :coarse      {:energy 0.8  :flourish 0.55 :softness 0.0 :warmth 0.15}})

(defn point
  "The :ha/style-point where a :ha/style sits."
  [style]
  (or (points style)
      (throw (ex-info (str "Unknown style " (pr-str style))
                      {:type :ha/unknown-style :style style}))))

(defn as-point
  "x when it is a :ha/style-point, the point of the :ha/style x otherwise."
  [x]
  (if (map? x) x (point x)))

(defn- through
  "y at x on the straight lines through knots [[x y] ...], x kept inside
  them. A knot is checked first so it gives back its own y, not a rounding
  of it: the middle of the space has to be :plain exactly."
  [knots x]
  (let [x (max (ffirst knots) (min (first (peek knots)) x))]
    (or (some (fn [[kx ky]] (when (== x kx) ky)) knots)
        (some (fn [[[x0 y0] [x1 y1]]]
                (when (< x0 x x1)
                  (+ y0 (* (/ (- x x0) (- x1 x0)) (- y1 y0)))))
              (partition 2 1 knots)))))

(defn from-point
  "Motion parameters, in the shape params returns, for any
  :ha/style-point: energy sets tempo and amplitude, softness takes away
  overshoot. The middle of the space is (params :plain) exactly; the named
  styles keep their own params, which have traits no axis covers."
  [{:keys [energy softness]}]
  {:tempo     (through [[0.0 1.6] [0.5 1.0] [1.0 0.5]] energy)
   :overshoot (* 2.0 (- 1 softness))
   :amplitude (through [[0.0 0.75] [0.5 1.0] [1.0 1.25]] energy)
   :sway      nil
   :steps     nil})

;; --- inference ---------------------------------------------------------------

(def ^:private window-ms 6000)
(def ^:private window-glyphs 12)
(def ^:private min-intervals 4)
(def ^:private fast-ms 110)
(def ^:private slow-ms 320)
(def ^:private steady-cv 0.5)

(defn- median [xs]
  (let [v (vec (sort xs))
        n (count v)
        mid (quot n 2)]
    (if (odd? n)
      (double (nth v mid))
      (/ (+ (nth v (dec mid)) (nth v mid)) 2.0))))

(defn- variation
  "The coefficient of variation: standard deviation over the mean."
  [xs]
  (let [n (count xs)
        mean (/ (double (reduce + xs)) n)
        var (/ (reduce + (map #(let [d (- % mean)] (* d d)) xs)) n)]
    (/ (Math/sqrt var) mean)))

(defn infer
  "The :ha/style that the rhythm of the last 12 glyphs born at or before t
  (and no more than 6 s before it) suggests. Fast (median interval under
  110 ms) and steady (coefficient of variation under 0.5) is :caffeinated,
  fast and erratic :coarse; slow (over 320 ms) and steady :elegant, slow and
  erratic :drunken; anything between, or fewer than 4 intervals, :plain.
  Glyphs born in the same millisecond (a paste) are one keystroke."
  [glyphs t]
  (let [borns (->> glyphs
                   (map :born-ms)
                   (filter #(<= (- t window-ms) % t))
                   sort
                   (take-last window-glyphs))
        intervals (remove zero? (map - (rest borns) borns))]
    (if (< (count intervals) min-intervals)
      :plain
      (let [m (median intervals)
            steady? (< (variation intervals) steady-cv)]
        (cond
          (< m fast-ms) (if steady? :caffeinated :coarse)
          (> m slow-ms) (if steady? :elegant :drunken)
          :else :plain)))))
