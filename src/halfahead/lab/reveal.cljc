(ns halfahead.lab.reveal
  "How much of the stage the lab has revealed (experiment 1 of the product
  outline): it starts as a familiar flat chat box with the house lights up
  and opens up as it is used, or when the writer waves a wand. Level 0 is
  flat and familiar; 1 adds small in-line word effects; 2 adds depth and
  takes the house lights down to the dark stage; 3 is the full stage, shots
  and the crawl included. Levels are reached by use (3 finished words; 4
  cues that are not the quiet default; a phrase directed with a shot other
  than :line on letters written since level 2) or by a wand, each wand
  raising the level by one, and they never go down. Pure: time arrives as
  t-ms, the wands as their times.")

(def ^:private top 3)

(defn- start-of [opts] (min top (max 0 (:start-level opts 0))))

(def ^:private words-for-1 3)
(def ^:private flair-for-2 4)

(def ^:private lights-ms
  "How long the house lights take to go down."
  900)

(def ^:private flick-px 600)
(def ^:private flick-ms 250)

(defn- nth-time
  "The time of the nth (1-based) of times, or nil when there are fewer."
  [times n]
  (first (drop (dec n) (sort times))))

(defn- spectacle-after
  "When the first shot other than :line was placed at or after t2 on a
  phrase with a letter born at or after t2: a phrase finished before level
  2, directed on the director's first pass, does not count."
  [{:keys [glyphs directions]} t2]
  (let [born (into {} (map (juxt :id :born-ms)) glyphs)]
    (->> directions
         (filter (fn [{:keys [shot at-ms glyph-ids]}]
                   (and (not= :line shot) (>= at-ms t2)
                        (some #(when-let [b (born %)] (>= b t2)) glyph-ids))))
         (map :at-ms)
         (reduce (fn [a b] (if a (min a b) b)) nil))))

(defn- reached-at
  "reached with level n and every level below it reached by time at."
  [reached n at]
  (reduce (fn [r i] (assoc r i (if-let [x (r i)] (min x at) at))) reached (range (inc n))))

(defn- level-by
  "The highest level whose time in reached passes (before? time t-ms), never
  below the start level."
  [reached opts before? t-ms]
  (reduce (fn [l i] (if (some-> (reached i) (before? t-ms)) (max l i) l))
          (start-of opts)
          (range (count reached))))

(defn level-at
  "The level in force at time t-ms, given the vector levels returned and
  opts as given to it: never below the start level, even before time 0."
  [reached opts t-ms]
  (level-by reached opts <= t-ms))

(defn- merged
  "The earlier time for each level of a and b, keeping only times up to t,
  with every level below a reached one reached no later."
  [a b t]
  (let [early (fn [x y] (let [xs (filter #(and % (<= % t)) [x y])]
                          (when (seq xs) (reduce min xs))))
        r (mapv early a (concat b (repeat nil)))]
    (reduce (fn [r i]
              (if-let [hi (r (inc i))]
                (assoc r i (if (r i) (min (r i) hi) hi))
                r))
            r
            (range (dec top) -1 -1))))

(defn- walk
  "The times each level is first reached by the start level, the levels
  already reached, the wands and use, walking every event up to t in time
  order. Level 3's use depends on when level 2 was reached, so it joins
  the queue once no earlier event is left to change that."
  [draft t {:keys [wands reached] :or {wands []} :as opts}]
  (let [cue-times (map :at-ms (:cues draft))
        flair-times (map :at-ms (remove #(= :default (get-in % [:cue :source])) (:cues draft)))
        usage (fn [n at] (when (and at (<= at t)) [at 1 [:usage n]]))
        ;; at the same instant, use before wands, so a wand always adds one
        events (concat (keep identity [(usage 1 (nth-time cue-times words-for-1))
                                       (usage 2 (nth-time flair-times flair-for-2))])
                       (keep #(when (<= % t) [% 2 [:wand]]) wands))]
    ;; Earlier calls' levels count only strictly before each event: a wand
    ;; at that instant is already in them, and must not be counted twice.
    (loop [queue (sort events)
           walked (reached-at (vec (repeat (inc top) nil)) (start-of opts) 0)
           level-3-queued? false]
      (let [t2 ((merged walked reached t) 2)
            queue-3? (and (not level-3-queued?) t2
                          (or (empty? queue) (>= (ffirst queue) t2)))
            queue (if queue-3?
                    (sort (into queue (keep identity [(usage 3 (spectacle-after draft t2))])))
                    queue)]
        (if-let [[at _ [kind n]] (first queue)]
          (let [level (max (level-at walked opts at) (level-by (vec reached) opts < at))
                target (min top (if (= :wand kind) (inc level) (max level n)))]
            (recur (rest queue) (reached-at walked target at) (or level-3-queued? queue-3?)))
          (merged walked reached t))))))

(defn levels
  "[t0 t1 t2 t3]: the time each level was first reached by t-ms, or nil if
  it has not been. opts: {:start-level 0..3 (default 0), :wands [t ...],
  :reached the vector an earlier call returned}. Every level up to
  :start-level is reached at 0. Level 1 is reached when the third word is
  finished (the third cue), level 2 when the fourth cue that is not the
  quiet default fires, level 3 when a shot other than :line is placed at or
  after level 2 on a phrase with a letter written since then. Each wand at
  or before t-ms raises the level by one from the level in force then.
  :reached keeps levels from going down when deleting words takes their
  cues away, and wands raise from it."
  [draft t-ms opts]
  (walk draft t-ms opts))

(defn- lights-target [level] (if (>= level 2) 1.0 0.0))

(defn- smoothstep [p] (* p p (- 3.0 (* 2.0 p))))

(defn state
  "{:level n :lights u :entered-ms t0 :reached [t0 t1 t2 t3]} at t-ms: the
  level in force, when it was entered, and the house lights, 0 up (paper)
  to 1 down (the dark stage). Levels 0 and 1 have the lights up, 2 and 3
  down; on entering level 2 they go down over 900 ms, eased, unless the
  lab started there (:start-level), when they are down from the start."
  [draft t-ms opts]
  (let [reached (levels draft t-ms opts)
        level (level-at reached opts t-ms)
        t2 (reached 2)
        lights (cond
                 (< level 2) 0.0
                 (>= (start-of opts) 2) (lights-target level)
                 :else (smoothstep (max 0.0 (min 1.0 (/ (- t-ms t2) (double lights-ms))))))]
    {:level level :lights lights :entered-ms (reached level) :reached reached}))

(defn flick?
  "True when pointer samples [{:x :y :t} ...] (px, ms) hold two that are
  at least 600 px apart and under 250 ms apart: the wand by mouse, a quick
  sweep across the stage, which slow pointing never is."
  [samples]
  (let [v (vec samples)
        n (count v)]
    (boolean
     (some (fn [[i j]]
             (let [a (v i), b (v j)
                   dx (- (:x b) (:x a)), dy (- (:y b) (:y a))]
               (and (< (Math/abs (double (- (:t b) (:t a)))) flick-ms)
                    (>= (Math/sqrt (double (+ (* dx dx) (* dy dy)))) flick-px))))
           (for [i (range n), j (range (inc i) n)] [i j])))))
