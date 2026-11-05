(ns halfahead.lab.style
  "How the whole draft moves: a :ha/style names a set of physics parameters
  that every motion in lab.pose is tuned by, and :auto infers one from the
  writer's own typing rhythm. The rhythm is read from the glyphs' birth
  times in the draft already in memory; nothing is recorded, kept or sent.
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
