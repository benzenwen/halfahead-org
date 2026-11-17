(ns halfahead.lab.draft
  "The draft being typed in the lab, as glyphs that keep their identity
  across edits. An edit is just the new text: the draft works out what
  changed, keeps the glyphs that survived, turns the removed ones into
  ghosts that linger briefly, and asks cue-fn for a cue on every word the
  edit finished. Pure: time arrives as now-ms, lookups arrive as cue-fn."
  (:require [halfahead.schema :as schema]))

(def ^:private ghost-life-ms 1500)

;; Surrogates are recognised by comparing one-unit strings, which orders
;; by UTF-16 code unit on both hosts, so no host interop is needed here.
(defn- between? [lo hi u]
  (and (<= (compare lo u) 0) (<= (compare u hi) 0)))

(defn- high-surrogate? [u] (between? "\uD800" "\uDBFF" u))
(defn- low-surrogate? [u] (between? "\uDC00" "\uDFFF" u))

(defn- glyph-chars
  "s split into glyph chars: single UTF-16 units, except that a high
  surrogate followed by a low one stays whole. A lone surrogate is its own
  char, so the chars always join back to exactly s."
  [s]
  (let [n (count s)]
    (loop [i 0, out (transient [])]
      (if (< i n)
        (let [w (if (and (< (inc i) n)
                         (high-surrogate? (subs s i (inc i)))
                         (low-surrogate? (subs s (inc i) (+ i 2))))
                  2 1)]
          (recur (+ i w) (conj! out (subs s i (+ i w)))))
        (persistent! out)))))

#?(:cljs (def ^:private letter-or-digit (js/RegExp. "^[\\p{L}\\p{Nd}]$" "u")))

(defn- word-char?
  "Letters, decimal digits, apostrophes and hyphens make words."
  [c]
  (or (= c "'") (= c "’") (= c "-")
      #?(:clj  (Character/isLetterOrDigit (.codePointAt ^String c 0))
         :cljs (.test letter-or-digit c))))

(defn- common-prefix-count [as bs]
  (let [n (min (count as) (count bs))]
    (loop [i 0]
      (if (and (< i n) (= (nth as i) (nth bs i))) (recur (inc i)) i))))

(defn- common-suffix-count
  "Matching chars at the ends of as and bs, never reaching into the first
  `taken` of either (the common prefix), so the two never overlap."
  [as bs taken]
  (let [na (count as), nb (count bs), n (- (min na nb) taken)]
    (loop [k 0]
      (if (and (< k n) (= (nth as (- na k 1)) (nth bs (- nb k 1))))
        (recur (inc k))
        k))))

(defn- prune-ghosts [ghosts now-ms]
  (filterv #(<= (- now-ms (:died-ms %)) ghost-life-ms) ghosts))

(defn- keep-live-cues [cues glyphs]
  (let [live (into #{} (map :id) glyphs)]
    (into []
          (keep (fn [cue]
                  (let [ids (filterv live (:glyph-ids cue))]
                    (when (seq ids) (assoc cue :glyph-ids ids)))))
          cues)))

(defn- finished-words
  "Every maximal run of word glyphs followed by a non-word glyph, in text
  order, as {:ids :word :following}. A run at the very end is unfinished."
  [glyphs]
  (let [wordy (mapv (comp word-char? :char) glyphs)
        n (count glyphs)]
    (loop [i 0, start nil, out []]
      (cond
        (= i n) out
        (nth wordy i) (recur (inc i) (or start i) out)
        start (let [run (subvec glyphs start i)]
                (recur (inc i) nil
                       (conj out {:ids (mapv :id run)
                                  :word (apply str (map :char run))
                                  :following (:char (nth glyphs i))})))
        :else (recur (inc i) nil out)))))

(defn- checked-cue [cue word]
  (when-let [explain (schema/explain :ha/word-cue cue)]
    (throw (ex-info "cue-fn returned an invalid :ha/word-cue"
                    {:type :ha.lab/invalid-cue :word word :cue cue
                     :explain explain})))
  cue)

(defn- cue-finished-words [cues glyphs now-ms cue-fn]
  (let [cued (mapv (comp set :glyph-ids) cues)
        already? (fn [ids] (some (fn [s] (every? s ids)) cued))]
    (reduce (fn [cues {:keys [ids word following]}]
              (if (already? ids)
                cues
                (conj cues {:glyph-ids ids
                            :cue (checked-cue (cue-fn word following) word)
                            :at-ms now-ms})))
            cues
            (finished-words glyphs))))

(defn empty-draft
  "A draft with nothing typed yet."
  []
  {:text "" :glyphs [] :ghosts [] :cues [] :next-id 0})

(defn prune
  "draft without the ghosts that have lingered past their time at now-ms;
  for frames between edits."
  [draft now-ms]
  (update draft :ghosts prune-ghosts now-ms))

(defn edit
  "draft after its text became new-text at now-ms. Glyphs in the unchanged
  start and end keep their ids and birth times; removed glyphs become
  ghosts; inserted chars become glyphs with fresh ids. Every finished word
  not already under one cue gets (cue-fn word following), which must
  return a :ha/word-cue, placed on that word's glyphs."
  [{:keys [text glyphs ghosts cues next-id] :as draft} new-text now-ms cue-fn]
  (if (= text new-text)
    (prune draft now-ms)
    (let [old-chars (mapv :char glyphs)
          new-chars (glyph-chars new-text)
          pre (common-prefix-count old-chars new-chars)
          suf (common-suffix-count old-chars new-chars pre)
          removed-to (- (count glyphs) suf)
          inserted (subvec new-chars pre (- (count new-chars) suf))
          born (map-indexed (fn [k c] {:id (+ next-id k) :char c :born-ms now-ms})
                            inserted)
          glyphs' (-> (subvec glyphs 0 pre)
                      (into born)
                      (into (subvec glyphs removed-to)))
          died (map (fn [i {:keys [id char]}]
                      {:id id :char char :died-ms now-ms :index i})
                    (range pre removed-to)
                    (subvec glyphs pre removed-to))]
      {:text new-text
       :glyphs glyphs'
       :ghosts (into (prune-ghosts ghosts now-ms) died)
       :cues (cue-finished-words (keep-live-cues cues glyphs') glyphs' now-ms cue-fn)
       :next-id (+ next-id (count inserted))})))
