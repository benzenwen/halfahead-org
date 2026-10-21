(ns halfahead.app.core
  "The app's whole behaviour as data: (handle state action) -> {:state :effects}.
  Nothing here reads a clock, draws a random number or touches the DOM; the
  time and ids an action needs travel in the action. Views render state,
  interpreters run effects.

  State (a plain map):
    :me          member id of the reader
    :conversation the one :ha/conversation
    :members     {member-id :ha/member}
    :messages    {message-id :ha/message}
    :presence    {member-id :ha/presence}
    :composer    {:text s}
    :now-ms      latest time seen, in ms on the shell's clock
    :performing  {message-id started-at-ms}; absent means settled
    :prefs       {:reduced-motion? b :sound? b}

  Actions:
    [:session/started {:me :conversation :members :now-ms :reduced-motion?}]
    [:messages/received {:messages :initial? :now-ms}]
    [:presence/received {:presence}]
    [:composer/edited {:text}]
    [:composer/sent {:id :now}]
    [:clock/ticked {:now-ms}]
    [:message/skipped {:id}]
    [:prefs/changed {:reduced-motion? :sound?}]
    [:replay/requested {:id :now-ms}]
  Anything else leaves the state unchanged.

  Effects: [:fx/write-message :ha/message]"
  (:require [clojure.string :as str]
            [halfahead.perform :as perform]
            [halfahead.schema :as schema]))

(def initial-state
  {:me          nil
   :conversation nil
   :members     {}
   :messages    {}
   :presence    {}
   :composer    {:text ""}
   :now-ms      0
   :performing  {}
   :prefs       {:reduced-motion? false :sound? false}})

;; ------------------------------------------------------------------- seeds

(def ^:private seed-modulus 4294967296)

(defn- char-code [c]
  #?(:clj (int c) :cljs (.charCodeAt c 0)))

(defn seed
  "A number derived from id alone, the same on every host. Not
  clojure.core/hash, which differs between JVM and JS. mod keeps each step
  under 2^53, so doubles are exact."
  [id]
  (reduce (fn [h c] (mod (+ (* 31 h) (char-code c)) seed-modulus))
          7
          (str id)))

;; --------------------------------------------------------------- timelines

(defn- timeline-for [state {:keys [id text delivery emphasis]}]
  (perform/perform {:text text
                    :delivery delivery
                    :emphasis emphasis
                    :seed (seed id)
                    :reduced-motion? (get-in state [:prefs :reduced-motion?])}))

(defn frames
  "{message-id frame} for every message: the frame at the elapsed time for
  one that is performing, the final frame for the rest."
  [state]
  (into {}
        (map (fn [[id {:keys [text] :as message}]]
               (let [tl      (timeline-for state message)
                     started (get-in state [:performing id])
                     elapsed (if started
                               (max 0 (- (:now-ms state) started))
                               (:duration-ms tl))]
                 [id (perform/frame-at tl text elapsed)])))
        (:messages state)))

(defn awaiting-agent?
  "True when the latest message is a human's and an agent is thinking."
  [{:keys [messages members presence]}]
  (let [latest (last (sort-by (juxt :sent-at :id) (vals messages)))
        kind   #(get-in members [% :kind])]
    (boolean
     (and latest
          (= :human (kind (:author-id latest)))
          (some (fn [[member-id p]]
                  (and (= :thinking (:state p)) (= :agent (kind member-id))))
                presence)))))

;; ----------------------------------------------------------------- actions

(defn- done [state & effects]
  {:state state :effects (vec effects)})

(defn- index-by [k coll]
  (into {} (map (juxt k identity)) coll))

(defn- session-started [state {:keys [me conversation members now-ms reduced-motion?]}]
  (done (-> state
            (assoc :me me :conversation conversation
                   :members (index-by :id members) :now-ms now-ms)
            (assoc-in [:prefs :reduced-motion?] (boolean reduced-motion?)))))

(defn- messages-received [state {:keys [messages initial? now-ms]}]
  (let [fresh (when-not initial?
                (remove #(contains? (:messages state) (:id %)) messages))]
    (done (-> state
              (update :messages into (index-by :id messages))
              (update :performing into (map (fn [m] [(:id m) now-ms])) fresh)
              (assoc :now-ms now-ms)))))

(defn- composer-sent [{:keys [me conversation composer] :as state} {:keys [id now]}]
  (let [text    (str/trim (str (:text composer)))
        message {:id id
                 :conversation-id (:id conversation)
                 :author-id me
                 :text text
                 :delivery :plain
                 :emphasis []
                 :sent-at now}]
    ;; An unsendable draft (blank, or over the schema's limit) is kept, so
    ;; the reader can fix it rather than lose it.
    (if (schema/valid? :ha/message message)
      (done (assoc-in state [:composer :text] "")
            [:fx/write-message message])
      (done state))))

(defn- clock-ticked [state {:keys [now-ms]}]
  (let [finished? (fn [[id started]]
                    (> now-ms (+ started
                                 (:duration-ms (timeline-for state (get-in state [:messages id]))))))]
    (done (-> state
              (assoc :now-ms now-ms)
              (update :performing #(into {} (remove finished?) %))))))

(defn- replay-requested [state {:keys [id now-ms]}]
  (if (contains? (:messages state) id)
    (done (-> state (assoc-in [:performing id] now-ms) (assoc :now-ms now-ms)))
    (done state)))

(defn handle
  "Applies action to state. Returns {:state s :effects [[:fx/name & args]]}."
  [state action]
  (let [[kind args] (when (vector? action) action)]
    (case kind
      :session/started    (session-started state args)
      :messages/received  (messages-received state args)
      :presence/received  (done (update state :presence into
                                        (index-by :member-id (:presence args))))
      :composer/edited    (done (assoc-in state [:composer :text] (:text args)))
      :composer/sent      (composer-sent state args)
      :clock/ticked       (clock-ticked state args)
      :message/skipped    (done (update state :performing dissoc (:id args)))
      :prefs/changed      (done (update state :prefs merge
                                        (select-keys args [:reduced-motion? :sound?])))
      :replay/requested   (replay-requested state args)
      (done state))))
