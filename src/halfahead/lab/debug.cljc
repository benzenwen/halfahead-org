(ns halfahead.lab.debug
  "The numbers behind the lab's ?debug overlay: frame timing over the last
  second, the cues an edit just placed, and a short log of recent cues, so
  a screen recording read frame by frame ties what was felt to what the
  code did. Pure: the shell passes the clock in as now-ms. Display only;
  nothing here is logged or stored anywhere."
  (:require [clojure.string :as str]))

(def ^:private window-ms 1000)
(def ^:private log-size 6)
(def ^:private log-age-ms 8000)

(defn record
  "stats with a frame at now-ms added: a running frame count and the
  timestamps of the frames in the last second. nil is empty stats."
  [stats now-ms]
  (let [since (- now-ms window-ms)]
    {:frames (inc (:frames stats 0))
     :times (into [] (filter #(>= % since)) (conj (or (:times stats) []) now-ms))}))

(defn- gaps [times]
  (map - (rest times) times))

(defn summary
  "{:frame :fps :mean-ms :worst-ms} for stats. fps counts the frames in the
  last second over the span they cover, so a window that is not yet full
  still reads true; mean and worst are of the gaps between consecutive
  frames. With fewer than two frames there are no gaps, and all three
  numbers are 0."
  [stats]
  (let [times (:times stats)
        n (count times)
        span (if (< n 2) 0 (- (peek times) (first times)))]
    (if (or (< n 2) (<= span 0))
      {:frame (:frames stats 0) :fps 0 :mean-ms 0 :worst-ms 0}
      {:frame (:frames stats)
       :fps (/ (* 1000.0 (dec n)) span)
       :mean-ms (/ (double span) (dec n))
       :worst-ms (apply max (gaps times))})))

(defn- same-cue?
  "Whether new-cue is old-cue, perhaps shrunk because some of its letters
  were deleted since. A word typed again has fresh glyph ids, so it is not."
  [old-cue new-cue]
  (and (= (:at-ms old-cue) (:at-ms new-cue))
       (= (:cue old-cue) (:cue new-cue))
       (every? (set (:glyph-ids old-cue)) (:glyph-ids new-cue))))

(defn new-cues
  "The cues placed in new-draft that were not in old-draft, in draft order,
  as [{:word :effect :intensity :source :at-ms} ...]."
  [old-draft new-draft]
  (let [olds (:cues old-draft)]
    (into []
          (comp (remove (fn [c] (some #(same-cue? % c) olds)))
                (map (fn [{:keys [cue at-ms]}]
                       (assoc (select-keys cue [:word :effect :intensity :source])
                              :at-ms at-ms))))
          (:cues new-draft))))

(defn cue-log
  "log with cues (as from new-cues) added: newest first, at most six
  entries, none older than eight seconds at now-ms."
  [log cues now-ms]
  (into []
        (comp (remove #(> (- now-ms (:at-ms %)) log-age-ms))
              (take log-size))
        (concat (reverse cues) log)))

(defn- fixed
  "x rounded to places decimals, as a string; cljs has no format."
  [x places]
  (let [scale (reduce * 1 (repeat places 10))
        n (Math/round (* (double x) scale))
        frac (str (rem n scale))]
    (if (zero? places)
      (str n)
      (str (quot n scale) "."
           (apply str (repeat (- places (count frac)) "0")) frac))))

(defn- style-text
  "The style in force, and (auto) when the rhythm chose it."
  [style style-choice]
  (str "style " (name style) (when (= :auto style-choice) " (auto)")))

(defn overlay-text
  "The overlay: a timing line, a backend line naming the motion style in
  force, then one line per log entry, newest first."
  [{:keys [summary t-ms backend glyphs style style-choice log]}]
  (let [{:keys [frame fps mean-ms worst-ms]} summary]
    (str/join "\n"
              (into [(str "frame " frame
                          " · t " (fixed (/ t-ms 1000.0) 3) " s"
                          " · " (fixed fps 0) " fps"
                          " · mean " (fixed mean-ms 1) " ms"
                          " · worst " (fixed worst-ms 1) " ms")
                     (str backend " · " glyphs " glyphs · " (style-text style style-choice))]
                    (map (fn [{:keys [word effect intensity source]}]
                           (str word " -> " (name effect) " " (fixed intensity 1)
                                " (" (name source) ")")))
                    log))))
