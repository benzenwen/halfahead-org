(ns halfahead.dev.score-eval
  "Hypothesis H4 (docs/product/lab-notebook.md): an agent given one page of
  reference writes scores that are valid on the first try and that change
  with what it means. This asks, checks and counts. Everything but ask! and
  run is pure; `bb lab:score-eval` makes model calls and is never part of
  `bb test`."
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [halfahead.lab.marks :as marks]
            [halfahead.lab.score :as score]))

;; --- asking ------------------------------------------------------------------

(def ^:private opening
  "You are an agent in a conversation where text is performed, not only shown.")

(defn- axis-word
  "A number on one of a feeling's axes as a word: `low` below zero, `high`
  above, 'slightly' when it is faint."
  [low high n]
  (cond
    (zero? n) "neutral"
    (< (abs n) 0.25) (str "slightly " (if (neg? n) low high))
    :else (if (neg? n) low high)))

(defn- affect-words
  "A feeling's two numbers as words, like 'calm and warm'."
  [feeling]
  (let [[energy warmth] (marks/feelings feeling)]
    (str (axis-word "calm" "excited" energy) " and " (axis-word "harsh" "warm" warmth))))

(defn- fixed-text [text]
  (str "The text (use it unchanged as \"text\"):\n" text))

(defn prompt
  "{:system s :user u} that asks an agent for the score of one case, given
  the reference page."
  [{:keys [mode text feeling said brief]} reference]
  {:system (str opening "\n\n" reference)
   :user (case mode
           :mark (str (fixed-text text) "\n\n"
                      "What you mean by it: " (name feeling) ": " (affect-words feeling))
           :compose (str "Someone said:\n" said "\n\n"
                         "Reply in one to three sentences, as a score.")
           :patch (str (fixed-text text) "\n\n" brief))})

;; --- reading a reply ---------------------------------------------------------

(defn- closing-brace
  "The index of the } that closes the { at index open, braces inside JSON
  strings aside; nil when it never closes."
  [^String s open]
  (let [n (count s)]
    (loop [i open, depth 0, in-string? false]
      (when (< i n)
        (let [c (.charAt s i)]
          (cond
            (and in-string? (= \\ c)) (recur (+ i 2) depth true)
            in-string? (recur (inc i) depth (not= \" c))
            (= \" c) (recur (inc i) depth true)
            (= \{ c) (recur (inc i) (inc depth) false)
            (and (= \} c) (= 1 depth)) i
            (= \} c) (recur (inc i) (dec depth) false)
            :else (recur (inc i) depth false)))))))

(defn- object-at
  "The JSON object that starts at index open, or nil."
  [s open]
  (when-let [close (closing-brace s open)]
    (try
      (let [x (json/parse-string (subs s open (inc close)))]
        (when (map? x) x))
      (catch Exception _ nil))))

(defn parse-reply
  "The JSON object in a model's reply, as data with string keys: the first
  balanced {...} that parses, whatever fence or prose is around it.
  {:error :no-json} when there is none, {:error :no-reply} for no string."
  [raw]
  (if-not (string? raw)
    {:error :no-reply}
    (or (->> (iterate #(str/index-of raw "{" (inc %)) (str/index-of raw "{"))
             (take-while some?)
             (some #(object-at raw %)))
        {:error :no-json})))

(def ^:private enum-keys
  #{"kind" "gesture" "curve" "channel" "wave" "phase" "order" "each" "as" "shot" "style"})

(def ^:private renamed-keys {"alternate" :alternate? "abs" :abs?})

(defn- keywordized
  "JSON data with keyword keys, and keywords for the string values of the
  score's enums."
  [x]
  (cond
    (map? x) (into {}
                   (map (fn [[k v]]
                          [(if (string? k) (or (renamed-keys k) (keyword k)) k)
                           (if (and (enum-keys k) (string? v))
                             (keyword v)
                             (keywordized v))]))
                   x)
    (sequential? x) (mapv keywordized x)
    :else x))

(defn- spanned
  "{:items [...] :unfound [phrase]}: each item's :phrase replaced by the
  :span of its first occurrence in text. An item whose phrase is not in the
  text is left out; one with no phrase to look for is left as it is, for
  normalize to refuse."
  [text items]
  (reduce (fn [acc item]
            (let [phrase (when (map? item) (:phrase item))]
              (if-not (and (string? text) (string? phrase) (seq phrase))
                (update acc :items conj item)
                (if-let [i (str/index-of text phrase)]
                  (update acc :items conj
                          (-> item (dissoc :phrase) (assoc :span [i (+ i (count phrase))])))
                  (update acc :unfound conj phrase)))))
          {:items [] :unfound []}
          items))

(defn ->score
  "{:score x :unfound [phrase]}: a parsed reply as the data
  lab.score/normalize takes. Keys and enum values become keywords, and each
  mark's and dress's phrase becomes the span (in UTF-16 units) of its first
  occurrence in the text; a phrase that is not there is left out and
  listed. Never throws."
  [data]
  (let [x (keywordized data)]
    (if-not (map? x)
      {:score x :unfound []}
      (reduce (fn [acc k]
                (if (sequential? (get x k))
                  (let [{:keys [items unfound]} (spanned (:text x) (get x k))]
                    (-> acc
                        (assoc-in [:score k] items)
                        (update :unfound into unfound)))
                  acc))
              {:score x :unfound []}
              [:marks :dress]))))

;; --- measuring ---------------------------------------------------------------

(defn- mean [xs]
  (when (seq xs)
    (/ (double (reduce + xs)) (count xs))))

(defn measure
  "What one reply to one case amounts to, as a flat map of counts. A reply
  that is not a score (reply-text may be nil) is :parsed? or :valid? false,
  with zero counts."
  [{:keys [id mode text feeling]} reply-text]
  (let [data (parse-reply reply-text)
        parsed? (not (contains? data :error))
        {:keys [unfound] offered :score} (if parsed? (->score data) {:unfound []})
        {:keys [dropped] s :score} (if parsed? (score/normalize offered) {:dropped []})
        ms (:marks s)
        removed (filter #(= :patch-problems (:why %)) dropped)]
    (cond-> {:id id
             :mode mode
             :parsed? parsed?
             :valid? (some? s)
             :text-kept? (when (#{:mark :patch} mode) (and parsed? (= text (get data "text"))))
             :marks (count ms)
             :dropped dropped
             :unfound unfound
             :kinds (frequencies (map :kind ms))
             :energy (mean (map #(:energy % 0) ms))
             :warmth (mean (map #(:warmth % 0) ms))
             :gestures (frequencies (keep :gesture ms))
             :patches (count (filter :patch ms))
             :patch-problems (count removed)
             :problems (mapv :problems removed)
             :dress (count (:dress s))
             :style (:style s)
             :shot (:shot s)}
      feeling (assoc :feeling feeling)
      (not parsed?) (assoc :error (:error data)))))

(defn- rate
  "The share of xs that satisfy pred, or nil when there are none."
  [pred xs]
  (when (seq xs)
    (/ (double (count (filter pred xs))) (count xs))))

(defn- clean? [{:keys [valid? dropped unfound]}]
  (boolean (and valid? (empty? dropped) (empty? unfound))))

;; Only the feelings whose both signs are unmistakable are scored; wry sits
;; too near the middle for a sign to mean anything.
(def ^:private scored-feelings #{:tender :furious})

(defn- same-sign? [expected x]
  (and (some? x) (pos? (* expected x))))

(defn- feeling-summary [feeling results]
  (let [[energy warmth] (marks/feelings feeling)
        marked (filter #(pos? (:marks %)) results)
        scored? (scored-feelings feeling)]
    {:n (count results)
     :marked (count marked)
     :energy (mean (map :energy marked))
     :warmth (mean (map :warmth marked))
     :energy-sign (when scored? (rate #(same-sign? energy (:energy %)) results))
     :warmth-sign (when scored? (rate #(same-sign? warmth (:warmth %)) results))}))

(defn summary
  "The numbers for the notebook. Rates are shares from 0 to 1, nil over
  nothing: :parsed, :valid and :clean (valid, nothing dropped, nothing
  unfound) over all results; :text-kept over the :mark and :patch ones;
  :marks and :no-marks over the valid ones. :feelings is per feeling of
  the :mark cases: mean :energy and :warmth over the replies with marks,
  and the share of all its replies whose mean has the feeling's sign
  (nil for a feeling that is not scored). :patch counts the :patch cases:
  replies that :carried a patch, patches :written, and those :sound
  (no problems)."
  [results]
  (let [valid (filter :valid? results)
        counts (map :marks valid)
        patch (filter #(= :patch (:mode %)) results)
        written #(+ (:patches %) (:patch-problems %))]
    {:n (count results)
     :parsed (rate :parsed? results)
     :valid (rate :valid? results)
     :clean (rate clean? results)
     :text-kept (rate :text-kept? (filter #(#{:mark :patch} (:mode %)) results))
     :marks (when (seq counts)
              {:mean (mean counts) :min (reduce min counts) :max (reduce max counts)})
     :no-marks (rate zero? counts)
     :kinds (apply merge-with + {} (map :kinds results))
     :feelings (into (sorted-map)
                     (map (fn [[feeling rs]] [feeling (feeling-summary feeling rs)]))
                     (group-by :feeling (filter #(= :mark (:mode %)) results)))
     :patch {:n (count patch)
             :carried (count (filter #(pos? (written %)) patch))
             :written (reduce + (map written patch))
             :sound (reduce + (map :patches patch))}
     :cost-usd (reduce + 0 (keep :cost-usd results))}))

;; --- the edge ----------------------------------------------------------------

(def ^:private default-timeout-ms 180000)

(defn- run-process
  "Runs argv; -> {:exit n :out s :err s}, or {:timeout? true} past
  timeout-ms. As halfahead.worker.engine's, which is private to the worker."
  [argv timeout-ms]
  (let [p (process/process argv {:out :string :err :string})
        result (deref p timeout-ms ::timeout)]
    (if (= ::timeout result)
      (do (process/destroy-tree p)
          {:timeout? true})
      (select-keys result [:exit :out :err]))))

(defn ask!
  "One reply from the claude CLI with every tool disabled: {:raw text
  :cost-usd n}, or {:error kw :detail s}. Never throws; opts are
  {:timeout-ms n :process-fn f}."
  [{:keys [system user]} {:keys [timeout-ms process-fn]
                          :or {timeout-ms default-timeout-ms process-fn run-process}}]
  (try
    (let [argv ["claude" "-p" user "--system-prompt" system
                "--tools" "" "--disallowedTools" "mcp__*" "--output-format" "json"]
          {:keys [exit out err timeout?]} (process-fn argv timeout-ms)
          j (when (and (not timeout?) (= 0 exit))
              (try (json/parse-string out true)
                   (catch Exception e e)))]
      (cond
        timeout? {:error :timeout :detail (str "timed out after " timeout-ms " ms")}
        (not= 0 exit) {:error :exit
                       :detail (if (str/blank? err) (str "exit " exit) (str/trim err))}
        (instance? Exception j) {:error :unreadable-output :detail (ex-message j)}
        (not (and (map? j) (string? (:result j)))) {:error :no-result
                                                    :detail "output has no result"}
        :else {:raw (:result j) :cost-usd (:total_cost_usd j)}))
    (catch Exception e
      {:error :process-failed :detail (ex-message e)})))

(defn- fmt [x]
  (cond
    (nil? x) "-"
    (float? x) (format "%.2f" x)
    :else (str x)))

(defn- case-line [{:keys [id rep parsed? valid? marks kinds energy warmth error cost-usd] :as r}]
  (format "%-20s %-2s %-9s marks %-2s energy %-5s warmth %-5s %s%s"
          id rep
          (cond (clean? r) "clean" valid? "valid" parsed? "invalid" :else (name error))
          marks (fmt energy) (fmt warmth)
          (str/join " " (map (fn [[k n]] (str (name k) " " n)) kinds))
          (if cost-usd (format "  $%.4f" (double cost-usd)) "")))

(defn- table
  "The summary as lines of label and value."
  [{:keys [n parsed valid clean text-kept marks no-marks kinds feelings patch cost-usd]}]
  (let [row (fn [label & values] (format "%-22s %s" label (str/join "  " (map fmt values))))]
    (concat
     [(row "replies" n)
      (row "parsed" parsed)
      (row "valid" valid)
      (row "clean" clean)
      (row "text kept" text-kept)
      (row "marks mean/min/max" (:mean marks) (:min marks) (:max marks))
      (row "no marks" no-marks)
      (row "kinds" (str/join " " (map (fn [[k c]] (str (name k) " " c)) (sort-by val > kinds))))
      (row "feeling" "n" "marked" "energy" "warmth" "energy-sign" "warmth-sign")]
     (for [[feeling f] feelings]
       (row (str "  " (name feeling))
            (:n f) (:marked f) (:energy f) (:warmth f) (:energy-sign f) (:warmth-sign f)))
     [(row "patch replies" (:n patch))
      (row "  carried a patch" (:carried patch))
      (row "  patches written" (:written patch))
      (row "  without problems" (:sound patch))
      (row "cost (USD)" (double cost-usd))])))

(defn- resource [path]
  (slurp (io/resource path)))

(defn run
  "Asks every chosen case, one after another, prints a line for each and
  the summary, writes <out-dir>/report.edn and returns the report
  {:summary .. :results ..}. Makes model calls. opts: :only (an id prefix),
  :limit (cases), :repeat (times each), :out-dir, and ask!'s."
  [{:keys [only limit repeat out-dir] :or {repeat 1 out-dir "out/score-eval"} :as opts}]
  (let [reference (resource "agent/score-reference.md")
        cases (cond->> (edn/read-string (resource "eval/score-cases.edn"))
                only (filter #(str/starts-with? (:id %) only))
                limit (take limit))
        runs (for [c cases, rep (range 1 (inc repeat))] [c rep])
        _ (println (count runs) "model calls")
        results (mapv (fn [[c rep]]
                        (let [{:keys [raw cost-usd error detail]} (ask! (prompt c reference) opts)
                              result (cond-> (assoc (measure c raw) :rep rep :raw raw
                                                    :cost-usd cost-usd)
                                       error (assoc :error error :detail detail))]
                          (println (case-line result))
                          result))
                      runs)
        report {:summary (summary results) :results results}]
    (println)
    (run! println (table (:summary report)))
    (fs/create-dirs out-dir)
    (spit (str (fs/path out-dir "report.edn")) (pr-str report))
    report))

(defn- parse-args
  "Options from command-line args. An unknown flag or a count that is not
  a positive integer throws."
  [args]
  (let [bad (fn [flag value]
              (throw (ex-info (str "bad argument: " flag " " value)
                              {:type :ha/bad-argument :flag flag :value value})))
        positive (fn [flag value]
                   (let [n (parse-long (str value))]
                     (if (and n (pos? n)) n (bad flag value))))]
    (reduce (fn [opts [flag value]]
              (case flag
                "--only" (if value (assoc opts :only value) (bad flag value))
                "--limit" (assoc opts :limit (positive flag value))
                "--repeat" (assoc opts :repeat (positive flag value))
                (bad flag value)))
            {}
            (partition-all 2 args))))

(defn -main [& args]
  (run (parse-args args))
  nil)
