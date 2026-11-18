(ns halfahead.lab.pace
  "The clock of the score (docs/product/score.md, 'The clock'): a lyric is
  paced by the music, a message by the eye. A reading lays a score out in
  time for one reader, in beats of that reader's pace: a reading head
  stops on each word, a :hold stretches its stay, a mark performs when the
  head reaches it, and nothing lasts beyond the cap (5 s unless a reader
  asked for the reading). Spans are in UTF-16 units. Pure, the same on both hosts."
  (:require [halfahead.schema :as schema]))

;; The head may take the cap less this long to cross a message: the last
;; second is left for the gesture of a mark on the last word.
(def ^:private tail-ms 1000)

;; A message too long to pace shows only this many marks, this far apart:
;; restraint is what makes skimming work.
(def ^:private highlight-count 4)
(def ^:private highlight-gap-ms 400)

;; A gesture squeezed below this share of its natural length reads as a
;; glitch, so it is not played at all.
(def ^:private least-share 0.4)

(def ^:private default-natural-ms 1500)

;; The set lab.marks and lab.score scan with (theirs are private): fixed,
;; because \s differs between the hosts.
(def ^:private whitespace
  #{" " "\t" "\n" "\r" "\u000b" "\f" " " " " " "})

(def ^:private enders #{"." "!" "?" "…"})
(def ^:private continuers #{"," ";" ":"})

(defn words
  "The spans [from to) of the text's words, in order: its runs of
  non-whitespace, punctuation and all."
  [text]
  (let [units (mapv str text)
        n (count units)]
    (loop [i 0, from nil, out []]
      (let [in? (and (< i n) (not (whitespace (units i))))
            out (if (and from (not in?)) (conj out [from i]) out)]
        (if (< i n)
          (recur (inc i) (when in? (or from i)) out)
          out)))))

(defn beat-ms
  "The time a reader at this :ha/pace takes for one word."
  [{:keys [wpm]}]
  (/ 60000.0 wpm))

(defn- round [x] (Math/round (double x)))

(defn- touches? [[a-from a-to] [b-from b-to]]
  (and (< a-from b-to) (< b-from a-to)))

(defn- stay-beats
  "How long the head stays on a word, in beats: longer on a long word and
  at a pause, the whole of it stretched by the strongest hold on the word
  (a fermata)."
  [text holds [from to :as word]]
  (let [last-unit (subs text (dec to) to)
        hold (reduce max 0 (keep #(when (touches? (:span %) word) (:amount %)) holds))]
    (* (+ (if (< 8 (- to from)) 1.3 1.0)
          (cond (enders last-unit) 0.5
                (continuers last-unit) 0.25
                :else 0.0))
       (+ 1 hold))))

(defn- edges
  "The whole ms at which the head reaches each word, and last the ms at
  which it leaves the last one. Each is rounded from the beats so far, not
  summed from rounded stays, so a faster pace never reaches a word later."
  [text marks beat ws]
  (let [holds (filter #(= :hold (:kind %)) marks)]
    (mapv #(round (* % beat))
          (reductions + 0.0 (map #(stay-beats text holds %) ws)))))

(defn- entry
  "A mark's entry at at-ms, cut short where it would pass the cap."
  [natural-ms cap i at-ms]
  {:mark i :at-ms at-ms :ms (min (natural-ms i) (- cap at-ms))})

(defn- end-ms [{:keys [at-ms ms]}] (+ at-ms ms))

(defn- paced
  "Every word gets its stop, and every mark performs when the head reaches
  the first word it touches, unless it would be squeezed too far."
  [marks natural-ms cap beat ws edges]
  (let [entries (for [i (range (count marks))
                      :let [at (some (fn [[word at]]
                                       (when (touches? (:span (marks i)) word) at))
                                     (map vector ws edges))]
                      :when (and at (<= (* least-share (natural-ms i))
                                        (- cap at)))]
                  (entry natural-ms cap i at))]
    {:duration-ms (reduce max (peek edges) (map end-ms entries))
     :beat-ms beat
     :stops (mapv (fn [word at end] {:span word :at-ms at :ms (- end at)})
                  ws edges (rest edges))
     :entries (vec (sort-by (juxt :at-ms :mark) entries))}))

(defn- highlights
  "The message is too long to hold a reader: it appears at once, and only
  its strongest marks perform, in reading order."
  [marks natural-ms cap beat]
  (let [entries (->> (range (count marks))
                     (sort-by (fn [i] [(- (:amount (marks i))) i]))
                     (take highlight-count)
                     sort
                     (map-indexed (fn [n i] (entry natural-ms cap i (* n highlight-gap-ms))))
                     vec)]
    {:duration-ms (reduce max 0 (map end-ms entries))
     :beat-ms beat
     :stops []
     :entries entries}))

(defn reading
  "A :ha/reading of a normalized score for a reader at this :ha/pace.
  opts: {:ms-of (fn [mark] ms) :cap-ms n}: how long each mark's gesture
  lasts when unhurried (default 1500 for every mark), and the cap, the
  longest the reading may last (default 5000, the rule for a performance
  that starts by itself; one a reader asked for and can stop may pass up
  to 30000).

  The head stays on each word for 1 beat; 1.3 for a word longer than 8
  units; plus 0.5 when the word ends with . ! ? … and 0.25 with , ; :; all
  multiplied by (1 + amount) for the strongest :hold touching the word.

  When the head crosses the text within the cap less 1000 ms the reading
  is paced:
  :stops holds every word, contiguous from 0, and each mark has an entry
  at the stop of the first word it touches, given its natural length or
  what is left before the cap; a mark given less than 0.4 of its
  natural length is left out. Otherwise :stops is empty and the four
  strongest marks (the earlier on a tie) enter in reading order, 400 ms
  apart. :entries are ordered by :at-ms, then mark index; :duration-ms is
  when the last stop or entry ends."
  ([score pace] (reading score pace {}))
  ([{:keys [text marks]} pace {:keys [ms-of cap-ms]
                               :or {ms-of (constantly default-natural-ms)
                                    cap-ms schema/max-performance-ms}}]
   (let [marks (vec marks)
         natural-ms (fn [i] (max 0 (round (ms-of (marks i)))))
         beat (beat-ms pace)
         ws (words text)
         edges (edges text marks beat ws)]
     (if (<= (peek edges) (- cap-ms tail-ms))
       (paced marks natural-ms cap-ms beat ws edges)
       (highlights marks natural-ms cap-ms beat)))))

(defn head
  "Where the reading head is at t-ms: a fractional index into the text, in
  UTF-16 units. It moves linearly through each word during the word's
  stop and is at the next word's start as that stop begins; before the
  first stop it waits at the first word's start, and after the last it
  rests at the last word's end. nil for a reading with no stops."
  [{:keys [stops]} t-ms]
  (when (seq stops)
    (or (some (fn [{[from to] :span :keys [at-ms ms]}]
                (when (< t-ms (+ at-ms ms))
                  (+ from (* (- to from) (/ (max 0 (- t-ms at-ms)) (double ms))))))
              stops)
        (second (:span (peek stops))))))
