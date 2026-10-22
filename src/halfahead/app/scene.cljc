(ns halfahead.app.scene
  "Re-performing a whole conversation in compressed time. Pure: the schedule
  is data computed from the messages, and the scene clock is arithmetic on
  the times the shell hands in."
  (:require [halfahead.perform :as perform]))

(def ^:private budget-ms 30000)
(def ^:private min-pause-ms 300)
(def ^:private max-pause-ms 2500)

(defn- millis [t]
  (if (number? t) t (.getTime t)))

(defn- performance-ms [{:keys [id text delivery emphasis]} {:keys [seed-fn reduced-motion?]}]
  (:duration-ms (perform/perform {:text text
                                  :delivery delivery
                                  :emphasis emphasis
                                  :seed (seed-fn id)
                                  :reduced-motion? reduced-motion?})))

(defn- real-pauses
  "The gap between consecutive sent-at times, clamped so a pause is neither
  a blink nor a wait."
  [sorted]
  (mapv (fn [a b]
          (-> (- (millis (:sent-at b)) (millis (:sent-at a)))
              (max min-pause-ms)
              (min max-pause-ms)))
        sorted (rest sorted)))

(defn plan
  "{:schedule [{:id :at-ms}] :scale s :end-ms n} for messages in sent-at
  order. opts is {:seed-fn f :reduced-motion? b}; seed-fn is the app's seed
  rule, passed in so a performance here lasts as long as it does in the
  transcript. When the scene would exceed 30 s, pauses shrink first, then
  (only if performances alone are too long) everything is scaled by :scale,
  the factor a performance's timeline time is multiplied by in scene time."
  [messages {:keys [reduced-motion?] :as opts}]
  (let [sorted (vec (sort-by (juxt :sent-at :id) messages))]
    (if reduced-motion?
      {:schedule (mapv (fn [m] {:id (:id m) :at-ms 0}) sorted)
       :scale 1.0
       :end-ms 0}
      (let [durations (mapv #(performance-ms % opts) sorted)
            pauses    (real-pauses sorted)
            total-d   (reduce + durations)
            total-p   (reduce + pauses)
            target    (dec budget-ms)
            [pauses scale]
            (cond
              (<= (+ total-d total-p) budget-ms) [pauses 1.0]
              (< total-d target) [(mapv #(long (* % (/ (- target total-d) total-p))) pauses)
                                  1.0]
              :else [(mapv (constantly 0) pauses) (/ (double target) total-d)])
            starts (reductions + 0.0 (map (fn [d p] (+ (* d scale) p)) durations pauses))
            ends   (map (fn [s d] (+ s (* d scale))) starts durations)]
        {:schedule (mapv (fn [m s] {:id (:id m) :at-ms (long s)}) sorted starts)
         :scale scale
         :end-ms (if (seq ends) (long #?(:clj (Math/ceil (double (apply max ends)))
                                         :cljs (js/Math.ceil (apply max ends))))
                     0)}))))

(defn schedule
  "[{:id :at-ms}] in sent-at order; the first message is at 0."
  [messages opts]
  (:schedule (plan messages opts)))

(defn scene-ms
  "Time on the scene clock, which stands still while paused."
  [{:keys [started-at paused-at]} now-ms]
  (max 0 (- (or paused-at now-ms) started-at)))

(defn progress
  "Whole percent of the scene that has played."
  [{:keys [end-ms] :as scene} now-ms]
  (if (pos? end-ms)
    (min 100 (long (/ (* 100 (scene-ms scene now-ms)) end-ms)))
    100))
