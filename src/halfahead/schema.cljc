(ns halfahead.schema
  "Every shape Half Ahead persists or passes between processes, as Malli
  schemas in one registry, addressed by :ha/* keyword. docs/SCHEMA.md is
  the human-readable mirror; the two must agree. Entity maps are closed:
  an unknown key is invalid. A change here is a design change: make it
  deliberately, in its own work order."
  (:require [clojure.string :as str]
            [malli.core :as m]
            [malli.error :as me]))

(defn- text [min max]
  [:and [:string {:min min :max max}]
   [:fn {:error/message "must not be blank"} #(not (str/blank? %))]])

(def ^:private optional {:optional true})

(def max-performance-ms
  "No flourish may hold a reader longer than this. Five seconds, because
  WCAG 2.2.2 (Level A) exempts motion that starts automatically and stops
  within five seconds from needing a pause control (DECISIONS.md H14)."
  5000)

(def ^:private registry
  (merge
   (m/default-schemas)
   {;; --- value types ------------------------------------------------------
    :ha/id          [:re #"^[A-Za-z0-9_-]{1,128}$"]
    :ha/timestamp   [:fn {:error/message "must be an inst"} inst?]
    :ha/message-text (text 1 4000)

    ;; How a line is delivered. Closed on purpose: the agent picks one of
    ;; these and never authors motion itself. Unknown values decode to
    ;; :plain (see halfahead.codec).
    ;;   :plain     appears at once
    ;;   :typed     arrives at a readable typing pace
    ;;   :headline  the emphasised words land first, large, then the line
    ;;              settles around them (the game-show card)
    ;;   :aside     arrives small and quiet, as from the wings
    ;;   :credits   lines roll up into place, one after another
    :ha/delivery    [:enum :plain :typed :headline :aside :credits]

    ;; What a beat of a timeline does to its span of text while it plays.
    :ha/effect      [:enum :appear :type :slam :whisper :rise]

    ;; A half-open character range [from to) within a message's text.
    :ha/span        [:and [:tuple [:int {:min 0}] [:int {:min 0}]]
                     [:fn {:error/message "from must be < to"}
                      (fn [[from to]] (< from to))]]

    ;; --- entities (persisted) ---------------------------------------------
    :ha/member
    [:and
     [:map {:closed true}
      [:id :ha/id]
      [:kind [:enum :human :agent]]
      [:display-name (text 1 80)]
      ;; for an agent, the human it belongs to
      [:owner-id optional :ha/id]
      [:joined-at :ha/timestamp]]
     [:fn {:error/message "an agent needs an owner-id; a human has none"}
      (fn [{:keys [kind owner-id]}]
        (= (= kind :agent) (some? owner-id)))]]

    :ha/conversation
    [:map {:closed true}
     [:id :ha/id]
     [:title (text 1 120)]
     [:member-ids [:vector {:min 1 :max 32} :ha/id]]
     [:created-by :ha/id]
     [:created-at :ha/timestamp]]

    :ha/message
    [:map {:closed true}
     [:id :ha/id]
     [:conversation-id :ha/id]
     [:author-id :ha/id]
     [:text :ha/message-text]
     [:delivery :ha/delivery]
     ;; character spans the delivery may stress (used by :headline)
     [:emphasis [:vector {:max 8} :ha/span]]
     [:sent-at :ha/timestamp]
     [:in-reply-to optional :ha/id]]

    ;; Whether a member is working on a reply in a conversation, so nobody
    ;; is left in silence. One document per member per conversation.
    :ha/presence
    [:map {:closed true}
     [:member-id :ha/id]
     [:conversation-id :ha/id]
     [:state [:enum :idle :thinking]]
     [:since :ha/timestamp]]

    ;; --- performance (derived, never persisted) ----------------------------
    ;; halfahead.perform builds a timeline from text, delivery, emphasis
    ;; and a seed. Invariants beyond this shape, checked by tests: the
    ;; beats' spans partition [0, (count text)), and every beat ends by
    ;; :duration-ms, so the final frame shows exactly the text.
    :ha/beat
    [:map {:closed true}
     [:span :ha/span]
     [:effect :ha/effect]
     [:at-ms [:int {:min 0}]]
     [:ms [:int {:min 0}]]]

    :ha/timeline
    [:map {:closed true}
     [:duration-ms [:int {:min 0 :max max-performance-ms}]]
     [:beats [:vector {:min 1} :ha/beat]]]

    ;; --- the worker's engine contract --------------------------------------
    :ha/turn
    [:map {:closed true}
     [:role [:enum :human :agent]]
     [:text :ha/message-text]]

    :ha/engine-request
    [:map {:closed true}
     [:persona (text 1 8000)]
     [:history [:vector {:max 200} :ha/turn]]]

    ;; What an engine must return. :emphasis holds phrases, which the worker
    ;; resolves to spans in :text; a phrase not found is dropped.
    :ha/engine-reply
    [:map {:closed true}
     [:text :ha/message-text]
     [:delivery :ha/delivery]
     [:emphasis [:vector {:max 8} (text 1 200)]]
     [:cost-usd optional [:and number? [:>= 0]]]]}))

(def ^:private compiled
  (memoize (fn [k] (m/schema k {:registry registry}))))

(defn schema
  "The compiled Malli schema for :ha/* keyword k. For codecs that walk an
  entity's keys; callers checking data use valid? and explain."
  [k]
  (compiled k))

(defn valid?
  "True when x conforms to the shape named by k."
  [k x]
  (m/validate (schema k) x))

(defn explain
  "nil when x is a valid k, otherwise a humanized error map."
  [k x]
  (some-> (m/explain (schema k) x) me/humanize))
