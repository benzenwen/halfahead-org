(ns halfahead.lab.score
  "A score (docs/product/score.md) is data an agent may write, so it is
  never trusted: normalize takes anything at all that is offered as one and
  returns a :ha/score that is safe to perform, with a list of what it had
  to drop and why. The lab's own rules go through the same door
  (from-text). Spans are in UTF-16 units. Pure, the same on both hosts."
  (:require [halfahead.lab.marks :as marks]
            [halfahead.lab.synth :as synth]
            [halfahead.schema :as schema]))

(def ^:private max-marks 16)
(def ^:private max-dress 8)

(def ^:private score-keys #{:text :marks :dress :style :shot})

;; The same set lab.marks scans with (its own is private), so a span this
;; namespace calls blank is one no tell of marks/read would ever cover.
(def ^:private whitespace
  #{" " "\t" "\n" "\r" "\u000b" "\f" " " " " " "})

;; The schema's order of kinds, which is also the order marks/read breaks
;; ties in, so a score read from text keeps its order through normalize.
(def ^:private kind-order
  (zipmap [:stress :hold :aside :turn :doubt :build :land :voice] (range)))

;; Built from code points, not written as literals: a lone surrogate in a
;; source file does not survive every reader.
(def ^:private high-surrogates [(str (char 0xD800)) (str (char 0xDBFF))])
(def ^:private low-surrogates [(str (char 0xDC00)) (str (char 0xDFFF))])

(defn- between?
  "Whether the unit u lies in [lo hi]. Strings compare by UTF-16 unit on
  both hosts."
  [[lo hi] u]
  (and (<= (compare lo u) 0) (<= (compare u hi) 0)))

(defn- splits-pair?
  "Whether index i falls between a high and a low surrogate."
  [units i]
  (and (< 0 i (count units))
       (between? high-surrogates (units (dec i)))
       (between? low-surrogates (units i))))

(defn- plain
  "A map as a plain hash map. Looking a keyword up in a sorted map of other
  keys throws; in a plain one it cannot."
  [x]
  (if (map? x) (into {} x) x))

(defn- span-problem
  "Why a schema-valid span cannot be performed on the text, or nil."
  [units [from to]]
  (cond
    (< (count units) to) :outside-text
    (every? whitespace (subvec units from to)) :blank
    (or (splits-pair? units from) (splits-pair? units to)) :splits-surrogate))

(defn- sift
  "The items that are `valid?` and whose span can be performed, as
  {:kept [[index item]] :dropped [...]}. Anything that is not a sequence of
  items is one :invalid."
  [what valid? units items]
  (if-not (sequential? items)
    {:kept [] :dropped [{:what (if (= :mark what) :marks :dress) :why :invalid}]}
    (reduce (fn [acc [i item]]
              (let [item (plain item)
                    why (if (and (map? item) (valid? item))
                          (span-problem units (:span item))
                          :invalid)]
                (if why
                  (update acc :dropped conj {:what what :why why :index i})
                  (update acc :kept conj [i item]))))
            {:kept [] :dropped []}
            (map-indexed vector items))))

(defn- without-unsafe-patch
  "{:kept [[index mark]] :dropped [...]}: every mark stays, but a patch
  with problems goes, and the style chooses the gesture instead."
  [indexed]
  (reduce (fn [acc [i mark]]
            (let [problems (when (contains? mark :patch)
                             (synth/problems (:patch mark)))]
              (if (seq problems)
                (-> acc
                    (update :kept conj [i (dissoc mark :patch)])
                    (update :dropped conj {:what :patch :why :patch-problems
                                           :index i :problems problems}))
                (update acc :kept conj [i mark]))))
          {:kept [] :dropped []}
          indexed))

(defn- strongest-first [[_ {:keys [amount] [from to] :span}]]
  [(- amount) from (- to)])

(defn- clash?
  "Two marks of one kind whose spans overlap."
  [{ka :kind [a-from a-to] :span} {kb :kind [b-from b-to] :span}]
  (and (= ka kb) (< a-from b-to) (< b-from a-to)))

(defn- strongest
  "{:kept [mark] :dropped [...]}: of two marks of one kind that overlap the
  stronger stays, and of more than 16 the strongest."
  [indexed]
  (reduce (fn [{:keys [kept] :as acc} [i mark]]
            (cond
              (some #(clash? % mark) kept)
              (update acc :dropped conj {:what :mark :why :overlap :index i})

              (= max-marks (count kept))
              (update acc :dropped conj {:what :mark :why :too-many :index i})

              :else (update acc :kept conj mark)))
          {:kept [] :dropped []}
          (sort-by strongest-first indexed)))

(defn- marks-of [units items]
  (let [sound (sift :mark #(schema/valid? :ha/mark (dissoc % :patch)) units items)
        safe (without-unsafe-patch (:kept sound))
        chosen (strongest (:kept safe))]
    {:kept (->> (:kept chosen)
                (sort-by (fn [{:keys [kind] [from to] :span}]
                           [from (- to) (kind-order kind)]))
                vec)
     :dropped (sort-by :index
                       (concat (:dropped sound) (:dropped safe) (:dropped chosen)))}))

(defn- dress-of
  "Dress has no strength to choose by, so past 8 the first 8 stay."
  [units items]
  (let [{:keys [kept dropped]} (sift :dress #(schema/valid? :ha/dress %) units items)
        [in over] (split-at max-dress kept)]
    {:kept (->> (map second in)
                (sort-by (fn [{[from to] :span}] [from (- to)]))
                vec)
     :dropped (concat dropped
                      (map (fn [[i _]] {:what :dress :why :too-many :index i}) over))}))

(defn- style? [x]
  (or (schema/valid? :ha/style x) (schema/valid? :ha/style-point x)))

(defn normalize
  "{:score s :dropped [{:what kw :why kw ...}]} for any data x offered as a
  score: s is a valid :ha/score, or nil when x has no text to perform
  (:why :no-text). Never throws.

  Kept: :text as it is; each mark and dress that is valid by the schema
  and whose span lies within the text, covers a non-blank character and
  splits no surrogate pair; a valid :style and :shot. A mark's :patch with
  any lab.synth/problems is removed and the mark kept. Of two marks of one
  kind that overlap the stronger stays; at most 16 marks (the strongest)
  and 8 dress (the first). Marks and dress come back ordered by span
  start, then longer first.

  Everything else is listed in :dropped: unknown keys ({:what :key :why
  :unknown-key :key k}), then marks and patches by their :index in x
  (:why :invalid :outside-text :blank :splits-surrogate :patch-problems
  :overlap :too-many), then dress, :style and :shot. A normalized score
  normalizes to itself with nothing dropped."
  [x]
  (let [x (plain x)
        text (when (map? x) (:text x))]
    (if-not (and (string? text) (schema/valid? :ha/message-text text))
      {:score nil :dropped [{:what :score :why :no-text}]}
      (let [units (mapv str text)
            marks (marks-of units (get x :marks []))
            dress (when (contains? x :dress) (dress-of units (:dress x)))
            style (plain (:style x))
            style? (and (contains? x :style) (style? style))
            shot? (and (contains? x :shot) (schema/valid? :ha/shot (:shot x)))]
        {:score (cond-> {:text text :marks (:kept marks)}
                  (sequential? (:dress x)) (assoc :dress (:kept dress))
                  style? (assoc :style style)
                  shot? (assoc :shot (:shot x)))
         :dropped (vec (concat
                        (for [k (keys x) :when (not (contains? score-keys k))]
                          {:what :key :why :unknown-key :key k})
                        (:dropped marks)
                        (:dropped dress)
                        (when (and (contains? x :style) (not style?))
                          [{:what :style :why :invalid}])
                        (when (and (contains? x :shot) (not shot?))
                          [{:what :shot :why :invalid}])))}))))

(defn from-text
  "The score the lab's own rules give a message as a style (a
  :ha/style-point, or a :ha/style) would have it: the marks its text tells
  of, the dress they leave, and the style. Always a valid :ha/score; nil
  for text that is not a :ha/message-text."
  [text point]
  (when (and (string? text) (schema/valid? :ha/message-text text))
    (let [ms (marks/read text)]
      (:score (normalize {:text text
                          :marks ms
                          :dress (marks/dress-for point text ms)
                          :style point})))))
