(ns halfahead.lab.director
  "The lab's director: reads each finished phrase of the draft and chooses
  how it is staged, as a :ha/direction on its glyphs. Most phrases stay in
  the line; a few take over the stage as a title card, or have the camera
  push in or pull back. Restraint is part of the rules: a spectacle budget
  keeps any two shots other than :line at least 10 s apart, so the big
  moments stay rare and land. Rules only, on the device; a local model can
  later sit behind the same function. Pure: time arrives as now-ms."
  (:require [clojure.string :as str]))

(def ^:private pause-ms
  "How long without a new glyph ends the phrase being written."
  1200)

(def ^:private budget-ms
  "The least time between two spectacles (shots other than :line)."
  10000)

(defn- terminator? [c] (contains? #{"." "!" "?" "\n"} c))

(defn- spectacle? [direction] (not= :line (:shot direction)))

(def ^:private wordy
  "Something other than whitespace and ASCII punctuation, so \"?!\" is no
  word. ASCII ranges only, so the pattern means the same on both hosts."
  #"[^\s!-/:-@\[-`{-~]")

(defn- kept-directions
  "directions with their ids cut down to the glyphs still present, and
  those left with none dropped, as cues are. Except: a spectacle whose
  glyphs have all gone stays, unperformed, until the budget it spent has
  passed, because it is the only memory of when the last spectacle was;
  without it, deleting a card and typing \"!\" again would fire a second
  card at once."
  [directions glyphs now-ms]
  (let [live (into #{} (map :id) glyphs)]
    (into []
          (keep (fn [d]
                  (let [ids (filterv live (:glyph-ids d))]
                    (cond
                      (seq ids) (assoc d :glyph-ids ids)
                      (and (spectacle? d) (< (- now-ms (:at-ms d)) budget-ms)) d))))
          directions)))

(defn- runs
  "glyphs cut into runs, in order, as {:glyphs [...] :closed? b}: a run
  closes after a terminator, or after the last glyph of an earlier
  direction, so a phrase that a pause ended stays ended when typing
  resumes. The last run is open unless it closed."
  [glyphs ends]
  (loop [gs (seq glyphs), run [], out []]
    (if-let [g (first gs)]
      (let [run (conj run g)]
        (if (or (terminator? (:char g)) (ends (:id g)))
          (recur (next gs) [] (conj out {:glyphs run :closed? true}))
          (recur (next gs) run out)))
      (cond-> out (seq run) (conj {:glyphs run :closed? false})))))

(defn- capitals? [word]
  (and (= word (str/upper-case word)) (not= word (str/lower-case word))))

(defn- shot-for
  "[shot energy] for a phrase's text by the rules, first match wins."
  [text]
  (let [words (filterv #(re-find wordy %) (str/split (str/trim text) #"\s+"))
        end (str (last (str/trimr text)))]
    (cond
      (or (= "!" end) (> (* 2 (count (filter capitals? words))) (count words))) [:card 0.9]
      (= "?" end) [:push 0.6]
      (and (= "." end) (>= (count words) 12)) [:pull 0.5]
      :else [:line 0.2])))

(defn- latest-spectacle-ms [directions]
  (some->> (seq (filter spectacle? directions)) (map :at-ms) (reduce max)))

(defn- within-budget? [directions now-ms]
  (let [at (latest-spectacle-ms directions)]
    (or (nil? at) (>= (- now-ms at) budget-ms))))

(defn- place
  "directions with one more for the phrase of glyphs, its shot downgraded
  to :line when the budget is spent."
  [directions glyphs now-ms]
  (let [[shot energy] (shot-for (apply str (map :char glyphs)))
        [shot energy] (if (or (= :line shot) (within-budget? directions now-ms))
                        [shot energy]
                        [:line 0.2])]
    (conj directions {:glyph-ids (into [] (comp (remove (comp str/blank? :char)) (map :id)) glyphs)
                      :shot shot :energy energy :at-ms now-ms :source :rules})))

(defn direct
  "draft with a :direction placed on every phrase it finished that is not
  directed yet. A phrase is the run of glyphs after the previous phrase's
  end up to a . ! ? or newline, or, once no glyph has been born for 1200 ms,
  up to the last glyph; its glyph ids are its non-whitespace glyphs, and a
  phrase without a word is not directed. Rules, first match: ending in !
  or with most words in CAPITALS, :card 0.9; ending in ?, :push 0.6; 12
  or more words ending in ., :pull 0.5; otherwise :line 0.2. A shot other
  than :line is placed only 10 s or more after the previous one, else the
  phrase gets :line. Directions keep only the ids of glyphs still present
  (see kept-directions for the one exception)."
  [draft now-ms]
  (let [glyphs (:glyphs draft)
        kept (kept-directions (:directions draft []) glyphs now-ms)
        directed (into #{} (mapcat :glyph-ids) kept)
        ends (into #{} (keep (comp peek :glyph-ids)) kept)
        paused? (and (seq glyphs)
                     (>= (- now-ms (reduce max (map :born-ms glyphs))) pause-ms))]
    (assoc draft :directions
           (reduce (fn [ds {run :glyphs closed? :closed?}]
                     (if (and (or closed? paused?)
                              (re-find wordy (apply str (map :char run)))
                              (not-any? (comp directed :id) run))
                       (place ds run now-ms)
                       ds))
                   kept
                   (runs glyphs ends)))))
