(ns halfahead.worker.core
  "The pure decisions behind the agent's turn: whether a conversation waits
  on the agent, what an engine is sent, how its answer is read, and the
  message written back. The I/O worker only fetches, calls, and stores."
  (:require [cheshire.core :as json]
            [clojure.string :as str]))

(def ^:private max-history 200)
(def ^:private max-text 4000)
(def ^:private max-phrase 200)
(def ^:private max-emphasis 8)

(def ^:private deliveries [:plain :typed :headline :aside :credits])

(defn awaiting-reply
  "The human's latest message when the agent has not answered after it,
  else nil. Derived from the messages, never stored. At equal :sent-at the
  agent's message counts as later, so a tie never asks for a second reply."
  [agent-id messages]
  (when-let [latest (last (sort-by (juxt :sent-at #(= agent-id (:author-id %))) messages))]
    (when (not= agent-id (:author-id latest))
      latest)))

(defn engine-request
  "The :ha/engine-request for a conversation: the persona and the most
  recent turns in time order, roles taken from the authors' member kinds."
  [persona members messages]
  (let [kind-by-id (into {} (map (juxt :id :kind)) members)]
    {:persona persona
     :history (->> messages
                   (sort-by (juxt :sent-at :id))
                   (take-last max-history)
                   (mapv (fn [m]
                           {:role (if (= :agent (kind-by-id (:author-id m)))
                                    :agent
                                    :human)
                            :text (:text m)})))}))

(def ^:private delivery-line
  {:plain    "the default; most replies."
   :typed    "when the pause itself means something."
   :headline "one striking point, with 1-3 emphasis phrases copied exactly from the text."
   :aside    "a quiet remark or caveat."
   :credits  "thanks, attributions, or a short list."})

(defn engine-system-prompt
  "The persona and the instructions for answering."
  [{:keys [persona]}]
  (str/join
   "\n"
   (concat
    [persona
     ""
     "Answer with ONE JSON object and nothing else:"
     "{\"text\": ..., \"delivery\": ..., \"emphasis\": [...]}"
     ""
     "\"delivery\" is one of:"]
    (for [d deliveries]
      (str "- " (name d) ": " (delivery-line d)))
    [""
     "Prefer plain; flair must serve the meaning."])))

(defn engine-transcript
  "The conversation as Human:/You: lines, ending with the agent's turn."
  [{:keys [history]}]
  (str/join
   "\n"
   (concat
    (for [{:keys [role text]} history]
      (str (if (= role :agent) "You: " "Human: ") text))
    ["You:"])))

(defn engine-prompt
  "The system prompt, a blank line, then the transcript: one string for
  engines that take no separate system prompt."
  [request]
  (str (engine-system-prompt request) "\n\n" (engine-transcript request)))

(defn- balanced-object-from
  "The {...} substring of s that opens at index start, ignoring braces
  inside JSON strings; nil when it never closes."
  [^String s start]
  (loop [i start, depth 0, in-str? false, esc? false]
    (when (< i (count s))
      (let [c (.charAt s i)]
        (cond
          in-str?  (recur (inc i) depth (or esc? (not= c \")) (and (not esc?) (= c \\)))
          (= c \") (recur (inc i) depth true false)
          (= c \{) (recur (inc i) (inc depth) false false)
          (= c \}) (if (= 1 depth)
                     (subs s start (inc i))
                     (recur (inc i) (dec depth) false false))
          :else    (recur (inc i) depth false false))))))

(defn- json-object-candidates
  "Balanced {...} substrings, one per opening brace, so a stray brace in
  surrounding prose cannot hide the real object."
  [s]
  (->> (range (count s))
       (filter #(= \{ (.charAt ^String s (int %))))
       (keep #(balanced-object-from s %))))

(defn- usable-object [candidate]
  (let [m (try (json/parse-string candidate true) (catch Exception _ nil))]
    (when (and (map? m) (string? (:text m)) (not (str/blank? (:text m))))
      m)))

(defn- truncate [n s]
  (if (> (count s) n) (subs s 0 n) s))

(defn- normalize [{:keys [text delivery emphasis]}]
  {:text     (truncate max-text (str/trim text))
   :delivery (or (some #{(keyword (str delivery))} deliveries) :plain)
   :emphasis (if (sequential? emphasis)
               (->> emphasis
                    (filter #(and (string? %) (not (str/blank? %))))
                    (map #(truncate max-phrase %))
                    (take max-emphasis)
                    vec)
               [])})

(defn parse-reply
  "Reads an engine's answer tolerantly into an :ha/engine-reply. A JSON
  object with text is used wherever it sits in s; otherwise s itself is the
  text. Blank s is a real error."
  [s]
  (when (str/blank? s)
    (throw (ex-info "empty reply" {:type :ha/empty-reply})))
  (if-let [obj (some usable-object (json-object-candidates s))]
    (normalize obj)
    {:text (truncate max-text (str/trim s)) :delivery :plain :emphasis []}))

(defn resolve-emphasis
  "Spans [from to] for the first occurrence of each phrase in text. Phrases
  not found, or overlapping an earlier kept span, are dropped."
  [text phrases]
  (->> phrases
       (keep (fn [p]
               (when-not (str/blank? p)
                 (when-let [i (str/index-of text p)]
                   [i (+ i (count p))]))))
       (reduce (fn [kept [from to :as span]]
                 (if (some (fn [[f t]] (and (< from t) (< f to))) kept)
                   kept
                   (conj kept span)))
               [])
       (sort-by first)
       (take max-emphasis)
       vec))

(defn reply-message
  "The :ha/message that carries reply, written by the agent in answer to
  the message `to`."
  [{:keys [reply to agent-id id now]}]
  {:id              id
   :conversation-id (:conversation-id to)
   :author-id       agent-id
   :text            (:text reply)
   :delivery        (:delivery reply)
   :emphasis        (resolve-emphasis (:text reply) (:emphasis reply))
   :sent-at         now
   :in-reply-to     (:id to)})
