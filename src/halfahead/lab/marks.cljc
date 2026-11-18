(ns halfahead.lab.marks
  "The first tier of the score (docs/product/score.md): a :ha/mark says what
  a span of a message is doing for its reader. People never write marks, so
  read finds them in the tells of plain text; a style point then chooses
  the gesture a mark is performed with and the dress it leaves behind.
  Spans are in UTF-16 units. Pure, the same on both hosts."
  (:refer-clojure :exclude [read])
  (:require [clojure.string :as str]
            [halfahead.lab.style :as style]))

;; --- feelings ----------------------------------------------------------------

(def feelings
  "Named feelings as affect, [energy warmth]: shorthand for a point on the
  two axes a mark carries."
  {:tender  [-0.3 0.9]  :joyful  [0.6 0.8]   :giddy   [0.9 0.7]
   :calm    [-0.7 0.4]  :wistful [-0.5 0.2]  :awed    [0.3 0.6]
   :grave   [-0.4 -0.2] :anxious [0.6 -0.5]  :furious [0.9 -0.9]
   :cold    [-0.3 -0.8] :wry     [0.1 -0.2]  :urgent  [0.9 -0.1]})

(defn feel
  "{:energy e :warmth w} for a named feeling, or nil when there is none."
  [k]
  (when-let [[energy warmth] (feelings k)]
    {:energy energy :warmth warmth}))

;; --- scanning ----------------------------------------------------------------
;; The text is scanned as a vector of one-unit strings. Classes are fixed
;; sets and ranges of units, not regex classes, because \s and \p{L} differ
;; between the hosts. Every span edge sits beside a delimiter or at an end of
;; the text, and no delimiter is a surrogate, so no span can split a pair.

(def ^:private whitespace
  #{" " "\t" "\n" "\r" "\u000b" "\f" "\u00a0" "\u2028" "\u2029"})

(def ^:private enders #{"." "!" "?" "…"})
(def ^:private closers #{")" "\"" "”"})
(def ^:private continuers #{"," ";" ":"})

(def ^:private delimiter?
  (into whitespace ["." "," "!" "?" "…" "(" ")" "\"" "“" "”" "*" ";" ":"]))

(defn- within?
  "Whether the unit u lies in one of the [lo hi] ranges. Strings compare by
  UTF-16 unit on both hosts."
  [ranges u]
  (some (fn [[lo hi]] (and (<= (compare lo u) 0) (<= (compare u hi) 0)))
        ranges))

(def ^:private digits [["0" "9"]])

;; Letters with no case, in the Basic Multilingual Plane: Hebrew, Arabic,
;; the Indic scripts, Thai and Lao, Hangul, kana and Han. Letters beyond it
;; arrive as surrogate pairs, which cannot be told from emoji a unit at a
;; time, and do not count.
(def ^:private uncased-letters
  [["\u05d0" "\u05ea"] ["\u0620" "\u064a"] ["\u0671" "\u06d3"]
   ["\u0900" "\u0dff"] ["\u0e00" "\u0eff"] ["\u1100" "\u11ff"]
   ["\u3040" "\u30ff"] ["\u3400" "\u4dbf"] ["\u4e00" "\u9fff"]
   ["\uac00" "\ud7a3"] ["\uf900" "\ufaff"]])

(def ^:private combining-marks
  [["\u0300" "\u036f"] ["\u1ab0" "\u1aff"] ["\u1dc0" "\u1dff"]
   ["\u20d0" "\u20ff"] ["\ufe20" "\ufe2f"]])

;; Holds skip only the accents that sit on Latin letters: the wider ranges
;; above hold enclosing marks and emoji variation parts, which are no part
;; of a letter being drawn out.
(def ^:private combining-diacriticals [["\u0300" "\u036f"]])

(defn- cased?
  "Whether s is a letter with an upper and a lower case."
  [s]
  (not= (str/upper-case s) (str/lower-case s)))

(defn- letter? [u]
  (or (cased? u) (within? uncased-letters u)))

(defn- runs
  "The maximal runs [from to) of units that satisfy pred."
  [pred units]
  (let [n (count units)]
    (loop [i 0, from nil, out []]
      (let [in? (and (< i n) (pred (units i)))
            out (if (and from (not in?)) (conj out [from i]) out)]
        (if (< i n)
          (recur (inc i) (when in? (or from i)) out)
          out)))))

(defn- word-runs
  "The runs between delimiters that hold a letter or a digit. A run of
  emoji, dashes or symbols alone is not a word: it is never a sentence's
  last word, and is not counted."
  [units]
  (filter (fn [[from to]]
            (some #(or (letter? %) (within? digits %)) (subvec units from to)))
          (runs (complement delimiter?) units)))

(defn- words
  "The text's words, [{:from :to :text :lower}]. Apostrophes and hyphens
  stay inside a word."
  [text units]
  (mapv (fn [[from to]]
          (let [w (subs text from to)]
            {:from from :to to :text w :lower (str/lower-case w)}))
        (word-runs units)))

(defn- clauses
  "The text cut at each run of . ! ? … that closes a clause, keeping the
  clauses that have words: [{:words :end :continues?}], :end being the run
  (\"\" for a last clause left open). A run closes a clause unless a word
  follows it directly, closing brackets and quotes aside, so 'e.g' and
  '3.5' stay whole while 'Ship it!🚀' ends. :continues? says the sentence
  goes on past the run, as after the quoted question in '\"what?\", she
  said'."
  [text units ws]
  (let [n (count units)
        word-start? (into #{} (map :from) ws)
        ends (keep (fn [[from to]]
                     (let [after (loop [j to]
                                   (if (and (< j n) (closers (units j)))
                                     (recur (inc j))
                                     j))]
                       (when-not (word-start? after)
                         {:from from
                          :end (subs text from to)
                          :continues? (contains? continuers (get units after))})))
                   (runs enders units))]
    (loop [ws ws, ends ends, out []]
      (if-let [{:keys [from end continues?]} (first ends)]
        (let [[in after] (split-with #(<= (:to %) from) ws)]
          (recur after (rest ends)
                 (cond-> out
                   (seq in) (conj {:words (vec in) :end end :continues? continues?}))))
        (cond-> out
          (seq ws) (conj {:words (vec ws) :end "" :continues? false}))))))

(defn- sentences
  "The clauses joined into sentences, [{:words :end}]."
  [clauses]
  (reduce (fn [out {:keys [words] :as clause}]
            (if (:continues? (peek out))
              (conj (pop out) (update clause :words #(into (:words (peek out)) %)))
              (conj out clause)))
          []
          clauses))

(defn- trimmed
  "[from to) without the whitespace at its edges, or nil when nothing is
  left."
  [units from to]
  (let [from (loop [i from]
               (if (and (< i to) (whitespace (units i))) (recur (inc i)) i))
        to (loop [i to]
             (if (and (< from i) (whitespace (units (dec i)))) (recur (dec i)) i))]
    (when (< from to) [from to])))

(defn- tight
  "[from to) when it is not empty and has no whitespace at its edges."
  [units from to]
  (when (and (< from to)
             (not (whitespace (units from)))
             (not (whitespace (units (dec to)))))
    [from to]))

(defn- enclosed
  "The spans between an opening and the next closing unit, innermost, as
  (inside units from to) gives them (nil for none). With retry?, a closer
  whose inside gave nothing opens the next span, so '**bold**' is read."
  [units open? close? inside retry?]
  (let [n (count units)]
    (loop [i 0, open nil, out []]
      (if (= i n)
        out
        (let [u (units i)]
          (cond
            (and open (close? u))
            (let [span (inside units (inc open) i)]
              (recur (inc i)
                     (when (and retry? (nil? span)) i)
                     (cond-> out span (conj span))))

            (open? u) (recur (inc i) i out)
            :else (recur (inc i) open out)))))))

;; --- tells -------------------------------------------------------------------

(defn- mark
  ([kind [from to] amount] (mark kind from to amount))
  ([kind from to amount] {:span [from to] :kind kind :amount amount}))

(defn- base-units
  "The word's units in lower case, without the combining marks that sit on
  its letters, so an accented letter repeats like a bare one."
  [word]
  (->> (map str word)
       (remove #(within? combining-diacriticals %))
       (map str/lower-case)))

(defn- longest-letter-run
  "The length of the longest run of one letter among the base units."
  [base]
  (->> (partition-by identity base)
       (filter (comp letter? first))
       (map count)
       (reduce max 0)))

(def ^:private address-joints #{"." "/" "@" ":"})

(defn- in-address?
  "Whether the word is part of a URL, a path or an address: the run between
  whitespace around it has a . / @ or : with a letter or digit directly on
  both sides. The run is looked at, not the word, because '.' and ':' end
  words. Punctuation at a word's edge ('nooo.', 'sooo...good') is not a
  joint."
  [units {:keys [from to]}]
  (let [n (count units)
        lo (loop [i from]
             (if (and (pos? i) (not (whitespace (units (dec i))))) (recur (dec i)) i))
        hi (loop [i to]
             (if (and (< i n) (not (whitespace (units i)))) (recur (inc i)) i))
        alnum? #(or (letter? %) (within? digits %))]
    (some #(and (address-joints (units %))
                (alnum? (units (dec %)))
                (alnum? (units (inc %))))
          (range (inc lo) (dec hi)))))

(defn- elongations [units ws]
  (for [{:keys [from to text] :as w} ws
        :let [base (base-units text)
              run (longest-letter-run base)]
        :when (and (<= 3 run)
                   ;; 'www', 'III' and 'zzz' are spelt that way, not drawn
                   ;; out: a hold needs a second letter to be drawn out of.
                   ;; Letters only, so an emoji or a digit beside the word
                   ;; does not make it one.
                   (<= 2 (count (distinct (filter letter? base))))
                   (not (in-address? units w)))]
    ;; 0.5 + 0.1 (run - 2), as one division so 'loooove' is 0.7 exactly
    (assoc (mark :hold from to (min 1.0 (/ (+ 3 run) 10.0))) :warmth 0.3)))

(defn- capital? [{:keys [text]}]
  (and (= text (str/upper-case text))
       (<= 2 (count (filter cased? (map str text))))))

(defn- capitals [units ws]
  (->> (filter capital? ws)
       (reduce (fn [spans {:keys [from to]}]
                 (let [[start end] (peek spans)]
                   (if (and end (= from (inc end)) (= " " (units end)))
                     (conj (pop spans) [start to])
                     (conj spans [from to]))))
               [])
       (map #(assoc (mark :stress % 0.8) :energy 0.7))))

(defn- asterisks [units]
  (map #(mark :stress % 0.7)
       (enclosed units #{"*"} #{"*"} tight true)))

(defn- parentheses [units]
  (map #(mark :aside % 0.6)
       (enclosed units #{"("} #{")"} trimmed false)))

(defn- quotations [units]
  (map #(mark :voice % 0.5)
       (concat (enclosed units #{"\""} #{"\""} trimmed false)
               (enclosed units #{"“"} #{"”"} trimmed false))))

(defn- trails-off?
  "Whether an ellipsis starts at index i."
  [units i]
  (or (= "…" (get units i))
      (every? #(= "." (get units %)) (range i (+ i 3)))))

(defn- ellipses [units ws]
  (for [{:keys [from to]} ws
        :when (trails-off? units to)]
    (assoc (mark :hold from to 0.5) :energy -0.4)))

(def ^:private hedges
  [["maybe"] ["perhaps"] ["probably"] ["i" "guess"] ["sort" "of"] ["kind" "of"]])

(def ^:private turning-words
  [["but"] ["however"] ["yet"] ["although"] ["instead"] ["actually"]])

(defn- phrase-at
  "The span of phrase where it starts at the i-th word, its words apart by
  whitespace only; nil when it does not."
  [units ws i phrase]
  (let [hit (subvec ws i (min (count ws) (+ i (count phrase))))]
    (when (and (= phrase (mapv :lower hit))
               (every? (fn [[a b]]
                         (every? whitespace (subvec units (:to a) (:from b))))
                       (partition 2 1 hit)))
      [(:from (first hit)) (:to (peek hit))])))

(defn- phrases [units ws kind phrases]
  (for [i (range (count ws))
        phrase phrases
        :let [span (phrase-at units ws i phrase)]
        :when span]
    (mark kind span 0.5)))

(defn- sentence-ends
  "What the mark that closes a clause says of its last word. '?!' and
  '!?' go by their last character."
  [cs]
  (for [{:keys [words end]} cs
        :let [{:keys [from to]} (peek words)
              final (when (seq end) (subs end (dec (count end))))]
        :when (#{"?" "!"} final)]
    (if (= "?" final)
      (mark :doubt from to 0.4)
      (assoc (mark :stress from to
                   (if (<= 2 (count (filter #{"!"} (map str end)))) 0.9 0.6))
             :energy 0.8))))

(defn- over-words [kind ws amount]
  (mark kind (:from (first ws)) (:to (peek ws)) amount))

(defn- trailing-on?
  "Whether a clause's closing run is an ellipsis the sentence trails on
  past: '?' or '!' after it ends the sentence all the same."
  [end]
  (and (or (str/includes? end "…") (str/includes? end "..."))
       (not (#{"?" "!"} (subs end (dec (count end)))))))

(defn- landing
  "A short last sentence lands, unless it trails off: an ellipsis anywhere
  from its first word to the end of the text (inside it, at its end, or
  glued to a word as in 'sooo...good') is the opposite of landing."
  [units cs]
  (let [ss (sentences (map #(cond-> % (trailing-on? (:end %)) (assoc :continues? true))
                           cs))
        {:keys [words]} (peek ss)]
    (when (and (<= 2 (count ss))
               (<= (count words) 3)
               (not-any? #(trails-off? units %)
                         (range (:from (first words)) (count units))))
      [(over-words :land words 0.7)])))

(defn- comma-items
  "A sentence's words grouped into the items its commas separate; an item
  with nothing in it (', ,') is an empty vector. A comma separates when
  whitespace follows it, so '1,000,000' is no list."
  [units ws]
  (reduce (fn [items w]
            (let [commas (count (filter #(and (= "," (units %))
                                              (whitespace (units (inc %))))
                                        (range (:to (peek (peek items))) (:from w))))]
              (if (zero? commas)
                (conj (pop items) (conj (peek items) w))
                (-> items
                    (into (repeat (dec commas) []))
                    (conj [w])))))
          [[(first ws)]]
          (rest ws)))

(defn- builds [units ss]
  (for [{:keys [words]} ss
        items (partition-by #(<= 1 (count %) 3) (comma-items units words))
        :when (and (<= 3 (count items)) (<= 1 (count (first items)) 3))]
    (over-words :build (into [] cat items) 0.6)))

;; --- read --------------------------------------------------------------------

(def ^:private max-marks 16)

(def ^:private kind-order
  (zipmap [:stress :hold :aside :turn :doubt :build :land :voice] (range)))

(defn- strongest-first [{:keys [amount] [from to] :span}]
  [(- amount) from (- to)])

(defn- in-reading-order [{:keys [kind] [from to] :span}]
  [from (- to) (kind-order kind)])

(defn- clash?
  "Two marks of one kind whose spans overlap."
  [{ka :kind [a-from a-to] :span} {kb :kind [b-from b-to] :span}]
  (and (= ka kb) (< a-from b-to) (< b-from a-to)))

(defn read
  "The marks a message's plain text tells of: a vector of at most 16
  :ha/mark, ordered by span start, then longer span first. Where two marks
  of one kind overlap the stronger stays, and past 16 the strongest stay.
  Total: any string reads, most to no marks at all."
  [text]
  (let [units (mapv str text)
        ws (words text units)
        cs (clauses text units ws)
        ss (sentences cs)]
    (->> (concat (elongations units ws)
                 (capitals units ws)
                 (asterisks units)
                 (parentheses units)
                 (ellipses units ws)
                 (phrases units ws :doubt hedges)
                 (sentence-ends cs)
                 (phrases units ws :turn turning-words)
                 (quotations units)
                 ;; Landing joins sentences itself, from the clauses: to it
                 ;; an ellipsis trails on into the same sentence, so 'So...
                 ;; yes.' is one sentence that does not land. A list must
                 ;; still stop at an ellipsis, so builds keep the sentences
                 ;; every closing run ends.
                 (landing units cs)
                 (builds units ss))
         (sort-by strongest-first)
         (reduce (fn [kept m]
                   (cond
                     (= max-marks (count kept)) (reduced kept)
                     (some #(clash? % m) kept) kept
                     :else (conj kept m)))
                 [])
         (sort-by in-reading-order)
         vec)))

;; --- performing --------------------------------------------------------------

(defn- effect
  "The gesture a style at this point performs the mark with."
  [{E :energy S :softness} {:keys [kind energy warmth] :or {energy 0 warmth 0}}]
  (let [lively? (>= E 0.6)
        crisp? (< S 0.5)]
    (case kind
      :stress (cond
                (>= warmth 0.4) :bloom
                (or (<= warmth -0.4) (>= energy 0.6)) (if crisp? :slam :glow)
                :else (if crisp? :pop :glow))
      :hold   :swell
      :aside  :hush
      :turn   (if lively? :rewind :nod)
      :doubt  (cond
                (and (>= energy 0.5) (<= warmth -0.3)) :shiver
                lively? :wobble
                :else :lean)
      :build  (if lively? :launch :shimmer)
      :land   (if (and lively? crisp?) :slam :nod)
      :voice  :draw)))

(defn gesture-for
  "{:effect e :intensity k} for a mark as a style (a :ha/style-point, or a
  :ha/style) performs it, or nil when the style is too spare to: the more
  flourish, the fainter a mark it still performs. A mark that names its own
  :gesture is always performed, with that gesture. The intensity is the
  mark's amount, whatever the style's energy: the style's tempo and
  amplitude already slow and shrink a calm style's motion, and scaling
  here as well would halve it twice."
  [point {:keys [amount gesture] :as mark}]
  (let [{:keys [flourish] :as point} (style/as-point point)]
    ;; The threshold can round up (0.42000000000000004 at flourish 0.6); the
    ;; slack lets a mark exactly on it perform.
    (when (or gesture (>= amount (- 0.9 (* 0.8 flourish) 1e-9)))
      {:effect (or gesture (effect point mark))
       :intensity (max 0.1 (min 1.0 amount))})))

(def ^:private max-dress 8)

(defn- versal
  "The span of the first letter, with the combining marks on it, when the
  text opens with a letter and is long enough to be given an enlarged one."
  [text]
  (let [units (mapv str text)]
    (when (and (seq units)
               (letter? (units 0))
               (<= 12 (count (word-runs units))))
      [0 (inc (count (take-while #(within? combining-marks %) (rest units))))])))

(defn dress-for
  "What the marks leave behind on text once the motion ends, as a style (a
  :ha/style-point, or a :ha/style) dresses them: a vector of at most 8
  :ha/dress, ordered like marks. Asides are whispered; the strongest strong
  stress is gilded by a warm style and rubricated by a cool one, the other
  strong stresses are set heavier; a long message opens with a versal.
  Spare styles leave less. A span is dressed once, as the first of those
  that applies. Past 8, whispers go first, the faintest first, then the
  fainter stresses."
  [point text marks]
  (let [{:keys [flourish warmth]} (style/as-point point)
        of-kind (fn [kind] (sort-by strongest-first (filter #(= kind (:kind %)) marks)))
        stresses (when (>= flourish 0.3)
                   (filter #(>= (:amount %) 0.7) (of-kind :stress)))
        asides (when (>= flourish 0.2) (of-kind :aside))]
    (->> (concat (when-let [span (and (>= flourish 0.6) (versal text))]
                   [{:span span :as :versal}])
                 (map-indexed (fn [i {:keys [span]}]
                                {:span span
                                 :as (cond
                                       (pos? i) :strong
                                       (>= warmth 0.5) :gilt
                                       :else :rubric)})
                              stresses)
                 (map (fn [{:keys [span]}] {:span span :as :whisper}) asides))
         (reduce (fn [[spans kept :as acc] {:keys [span] :as dress}]
                   (if (spans span)
                     acc
                     [(conj spans span) (conj kept dress)]))
                 [#{} []])
         peek
         (take max-dress)
         (sort-by (fn [{[from to] :span}] [from (- to)]))
         vec)))
