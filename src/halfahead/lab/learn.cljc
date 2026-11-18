(ns halfahead.lab.learn
  "A style learned from flicks, like an optometrist's exam for motion
  (docs/product/score.md): the writer is shown two performances, 'better
  like this, or like this?', and the style point moves toward the one they
  prefer. Also a simulation that measures how many flicks that takes
  (H5 in docs/product/lab-notebook.md). Pure, the same on both hosts.")

(def ^:private axes [:energy :flourish :softness :warmth])

;; Steps are powers of two, so halving and moving are exact in doubles and
;; the two hosts agree to the last bit.
(def ^:private first-step 0.25)
(def ^:private smallest-step 0.015625)                       ; 1/64

(def ^:private centre (zipmap axes (repeat 0.5)))

(defn start
  "The learner's state before any flick, at the centre or at `point`."
  ([] (start centre))
  ([point]
   {:point  point
    :step   (zipmap axes (repeat first-step))
    :dir    (zipmap axes (repeat 1))
    :missed (zipmap axes (repeat 0))
    :axis   (first axes)
    :flicks 0}))

(defn- moved [point axis delta]
  (update point axis #(min 1.0 (max 0.0 (+ % delta)))))

(defn- offer
  "The direction actually offered along the state's axis, and the point it
  leads to. At a wall the stored direction would offer the current point
  again, so the other one is used; the step never exceeds the width of the
  interval, so one of the two always moves."
  [{:keys [point axis] :as state}]
  (let [dir  (get-in state [:dir axis])
        step (get-in state [:step axis])
        b    (moved point axis (* dir step))]
    (if (= b point)
      {:dir (- dir) :b (moved point axis (* (- dir) step))}
      {:dir dir :b b})))

(defn pair
  "The two points to perform next: `:a` is the current point and `:b` a
  step away from it along `:axis`. They always differ."
  [state]
  {:axis (:axis state) :a (:point state) :b (:b (offer state))})

(defn- next-axis [axis]
  (nth axes (mod (inc (.indexOf axes axis)) (count axes))))

(defn skip
  "The state asking about the next axis, with nothing else changed: no
  flick is counted, because the writer was shown nothing (lab.flick skips
  an axis whose pair would look the same)."
  [state]
  (update state :axis next-axis))

(defn choose
  "The state after the writer prefers `:a` or `:b` of `(pair state)`."
  [{:keys [axis] :as state} choice]
  (let [{:keys [dir b]} (offer state)
        chosen
        (case choice
          :b (-> state
                 (assoc :point b)
                 (assoc-in [:dir axis] dir)
                 (assoc-in [:missed axis] 0))
          ;; The flip is away from the direction offered, not the stored
          ;; one: at a wall they differ, and flipping the stored one would
          ;; lose count of which side was just refused.
          :a (let [both? (= 2 (inc (get-in state [:missed axis])))]
               (cond-> (-> state
                           (assoc-in [:dir axis] (- dir))
                           (update-in [:missed axis] inc))
                 both? (-> (update-in [:step axis] #(max smallest-step (/ % 2)))
                           (assoc-in [:missed axis] 0))))
          (throw (ex-info "A choice is :a or :b"
                          {:type :ha.learn/unknown-choice :choice choice})))]
    (-> chosen
        (update :flicks inc)
        (assoc :axis (next-axis axis)))))

(defn distance
  "The root mean square difference between two points over the four axes."
  [p q]
  (Math/sqrt (/ (reduce + (map (fn [k] (let [d (- (get p k) (get q k))] (* d d)))
                               axes))
                (count axes))))

;; --- a random source, the same on both hosts --------------------------------
;; The minimal standard Lehmer generator. Its products stay under 2^53, so
;; they are exact in JavaScript's doubles as well as in longs, and it needs
;; no bit operations (32-bit in JavaScript).

(def ^:private modulus 2147483647)                           ; 2^31 - 1
(def ^:private multiplier 16807)

(defn- advance [g] (mod (* multiplier g) modulus))

(defn- generator
  "A generator state for any integer seed. A few draws are discarded
  because neighbouring seeds start with neighbouring outputs."
  [seed]
  (nth (iterate advance (inc (mod seed (dec modulus)))) 4))

(defn- draw
  "[u g'], with u uniform in [0 1)."
  [g]
  (let [g' (advance g)]
    [(/ (double g') modulus) g']))

;; --- the simulation ----------------------------------------------------------

(defn simulate
  "The distances from the learner's point to `:hidden`, before any flick and
  after each of `:flicks`. The simulated reader picks whichever option of
  each pair is nearer to `:hidden`, and with probability `:noise` the other."
  [{:keys [hidden noise flicks seed]}]
  (loop [state (start)
         g     (generator seed)
         out   [(distance centre hidden)]]
    (if (= flicks (:flicks state))
      out
      (let [{:keys [a b]} (pair state)
            nearer (if (< (distance b hidden) (distance a hidden)) :b :a)
            ;; drawn on every flick, so one seed gives one stream of
            ;; mistakes whatever the noise level
            [u g'] (draw g)
            choice (if (< u noise) ({:a :b :b :a} nearer) nearer)
            state' (choose state choice)]
        (recur state' g' (conj out (distance (:point state') hidden)))))))

(defn- draw-readers
  "`n` simulate arguments with hidden points uniform in the unit cube, each
  with its own seed, all drawn from one generator."
  [n g opts]
  (loop [g g, out []]
    (if (= n (count out))
      out
      (let [[us g'] (reduce (fn [[us g] _] (let [[u g'] (draw g)] [(conj us u) g']))
                            [[] g] axes)
            g''     (advance g')]
        (recur g'' (conj out (assoc opts :hidden (zipmap axes us) :seed g'')))))))

(defn experiment
  "The median and 80th percentile, over `:readers` simulated readers, of the
  distance to the hidden point at each flick count from 0 to `:flicks`.
  Percentiles are by nearest rank (the smallest value with at least that
  share of readers at or below it), with no interpolation."
  [{:keys [readers flicks noise seed]}]
  (let [runs    (mapv simulate (draw-readers
                                readers (generator seed) {:noise noise :flicks flicks}))
        columns (for [i (range (inc flicks))] (vec (sort (map #(nth % i) runs))))
        ;; integer ranks: ceil(r / 2) and ceil(4r / 5)
        at-rank (fn [rank] (mapv #(nth % (dec rank)) columns))]
    {:median (at-rank (quot (+ readers 1) 2))
     :p80    (at-rank (quot (+ (* 4 readers) 4) 5))}))
