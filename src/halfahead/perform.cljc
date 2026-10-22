(ns halfahead.perform
  "Turns a message's text, delivery and emphasis into an :ha/timeline, and a
  timeline plus elapsed time into the frame to show. Views only play this
  data; every flourish is decided here, deterministically."
  (:require [halfahead.schema :as schema]))

;; JS and Java disagree about what \s matches, so name the class.
(def ^:private ws-chars " \\t\\n\\r\\f\\u000B\\u00A0\\u2028\\u2029")
(def ^:private word-re (re-pattern (str "[^" ws-chars "]+[" ws-chars "]*")))
(def ^:private leading-ws-re (re-pattern (str "^[" ws-chars "]*")))
(def ^:private bare-word-re (re-pattern (str "[^" ws-chars "]+")))

(def ^:private typing-ms-per-char 35)
(def ^:private slam-ms 300)
(def ^:private slam-stagger-ms 250)
(def ^:private settle-ms 200)
(def ^:private rise-ms 700)
(def ^:private rise-stagger-ms 400)

;; ---------------------------------------------------------------- splitting

(defn- spans-of-lengths
  "Contiguous spans starting at 0, one per length."
  [lengths]
  (second
   (reduce (fn [[at acc] len] [(+ at len) (conj acc [at (+ at len)])])
           [0 []] lengths)))

(defn- pieces->spans
  "Spans for pieces re-seq'd from text; anything before the first piece
  (leading whitespace) joins it. No pieces means one span over everything."
  [text pieces]
  (let [n (count text)]
    (if (empty? pieces)
      [[0 n]]
      (let [lengths (mapv count pieces)
            lead    (- n (reduce + lengths))]
        (spans-of-lengths (update lengths 0 + lead))))))

(defn- word-spans [text]
  (pieces->spans text (re-seq word-re text)))

(defn- line-spans
  "Lines keep their newline; a single line is split after sentence ends."
  [text]
  (let [lines (re-seq #"[^\n]*\n|[^\n]+" text)]
    (pieces->spans text (if (> (count lines) 1)
                          lines
                          (re-seq #"[\s\S]*?[.!?] |[\s\S]+" text)))))

(defn- clean-emphasis
  "Untrusted spans: clamp to the text, drop empty ones, merge overlaps."
  [text spans]
  (let [n (count text)
        clamp #(max 0 (min n %))]
    (->> spans
         (map (fn [[a b]] [(clamp a) (clamp b)]))
         (filter (fn [[a b]] (< a b)))
         sort
         (reduce (fn [acc [a b]]
                   (let [[pa pb] (peek acc)]
                     (if (and pb (<= a pb))
                       (conj (pop acc) [pa (max pb b)])
                       (conj acc [a b]))))
                 []))))

(defn- longest-word-span
  "First longest word, without its surrounding whitespace; nil if none."
  [text]
  (let [words (for [[from to] (word-spans text)
                    :let [piece (subs text from to)
                          start (+ from (count (re-find leading-ws-re piece)))
                          word  (re-find bare-word-re piece)]
                    :when word]
                [start (+ start (count word))])]
    (when (seq words)
      (reduce (fn [best w]
                (if (> (- (second w) (first w)) (- (second best) (first best))) w best))
              words))))

;; --------------------------------------------------------------------- prng

(def ^:private lcg-modulus 4294967296)

(defn- lcg-step
  "Numerical Recipes LCG. mod rather than bit-and: cljs bit ops are signed
  32-bit. The product stays under 2^53, so doubles are exact."
  [state]
  (mod (+ (* 1664525 state) 1013904223) lcg-modulus))

(defn- jitters
  "Endless factors in [0.8, 1.2]. The first draws are skipped because
  neighbouring seeds are still nearly equal after one step."
  [seed]
  (->> (iterate lcg-step (mod (or seed 0) lcg-modulus))
       (drop 3)
       (map #(+ 0.8 (* 0.4 (/ % lcg-modulus))))))

;; --------------------------------------------------------------- deliveries

(defn- beat [span effect at-ms ms]
  {:span span :effect effect :at-ms at-ms :ms ms})

(defn- whole [text effect ms]
  [(beat [0 (count text)] effect 0 ms)])

(defn- typed [text seed]
  (let [spans (word-spans text)
        ms    (map (fn [[from to] jitter]
                     (long (+ 0.5 (* typing-ms-per-char (- to from) jitter))))
                   spans (jitters seed))
        ats   (reductions + 0 ms)]
    (mapv (fn [span at ms] (beat span :type at ms)) spans ats ms)))

(defn- headline [text emphasis]
  (let [stressed (or (seq (clean-emphasis text emphasis))
                     (some-> (longest-word-span text) vector))
        gaps     (->> (concat [0] (mapcat identity stressed) [(count text)])
                      (partition 2)
                      (filter (fn [[a b]] (< a b))))
        settle-at (if (seq stressed)
                    (+ (* slam-stagger-ms (dec (count stressed))) slam-ms)
                    0)]
    (->> (concat
          (map-indexed (fn [i span] (beat span :slam (* i slam-stagger-ms) slam-ms))
                       stressed)
          (map (fn [span] (beat (vec span) :appear settle-at settle-ms)) gaps))
         (sort-by (comp first :span))
         vec)))

(defn- credits [text]
  (vec (map-indexed (fn [i span] (beat span :rise (* i rise-stagger-ms) rise-ms))
                    (line-spans text))))

;; ------------------------------------------------------------------ the cap

(defn- end-ms [{:keys [at-ms ms]}] (+ at-ms ms))

(defn- compress
  "Scales every start and end proportionally so the whole fits the cap.
  Beats shrink or bunch up but are never dropped."
  [beats]
  (let [duration (reduce max 0 (map end-ms beats))
        cap      schema/max-performance-ms]
    (if (<= duration cap)
      beats
      (let [scale #(quot (* % cap) duration)]
        (mapv (fn [{:keys [at-ms] :as b}]
                (assoc b :at-ms (scale at-ms) :ms (- (scale (end-ms b)) (scale at-ms))))
              beats)))))

;; --------------------------------------------------------------- public API

(defn perform
  "The :ha/timeline for a message. Equal inputs give equal timelines;
  reduced motion shows everything at once with zero duration."
  [{:keys [text delivery emphasis seed reduced-motion?]}]
  (let [beats (compress
               (if reduced-motion?
                 (whole text :appear 0)
                 (case delivery
                   :typed    (typed text seed)
                   :headline (headline text emphasis)
                   :aside    (whole text :whisper 600)
                   :credits  (credits text)
                   (whole text :appear 150))))]
    {:duration-ms (reduce max 0 (map end-ms beats))
     :beats       beats}))

(defn- progress [{:keys [at-ms ms]} t-ms]
  (cond
    (< t-ms at-ms)        0.0
    (>= t-ms (+ at-ms ms)) 1.0
    :else                 (/ (double (- t-ms at-ms)) ms)))

(defn frame-at
  "One segment per beat, in text order, at elapsed t-ms. Segments not yet
  started are included at progress 0.0 so the view lays out the full text
  and nothing reflows."
  [{:keys [beats]} text t-ms]
  (mapv (fn [{[from to] :span :keys [effect] :as b}]
          {:text (subs text from to) :effect effect :progress (progress b t-ms)})
        beats))
