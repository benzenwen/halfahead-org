(ns halfahead.app.views
  "The conversation as hiccup. Pure: state in, data out. Motion appears only
  as each segment's --p (its frame progress); the stylesheet decides what
  progress looks like. Events are data without times or ids; the shell adds
  those before handling."
  (:require [clojure.string :as str]
            [halfahead.app.core :as core]))

(defn- display-name [state member-id]
  (or (get-in state [:members member-id :display-name]) member-id))

(defn- owner-line [state {:keys [kind owner-id]}]
  (when (= :agent kind)
    [:span.owner "agent of " (display-name state owner-id)]))

(defn- member-chip [state {:keys [id kind display-name] :as member}]
  [:li.chip {:key id :class [(name kind)]}
   [:span.name display-name]
   (owner-line state member)])

(defn- header [state]
  (let [{:keys [title member-ids]} (:conversation state)
        members (->> (or (seq member-ids) (keys (:members state)))
                     (keep (:members state)))]
    [:header
     [:h1 title]
     [:ul.members (map #(member-chip state %) members)]]))

(defn- segment [{:keys [text effect progress]}]
  [:span {:class ["seg" (str "fx-" (name effect))]
          :style {"--p" (str progress)}}
   text])

(defn- message [state performing frames {:keys [id author-id text]}]
  (let [author (get-in state [:members author-id])]
    [:li {:key id}
     [:article (cond-> {}
                 (contains? performing id)
                 (assoc :on {:click [[:message/skipped {:id id}]]}))
      [:p.author
       [:span.name (display-name state author-id)]
       (when author (owner-line state author))]
      [:p.body
       [:span.sr-only text]
       (into [:span {:aria-hidden "true"}] (map segment) (get frames id))]
      [:button.replay {:type "button"
                       :aria-label "Replay message"
                       :on {:click [[:replay/requested {:id id}]]}}
       "↻"]]]))

(defn- transcript [state frames]
  (let [messages (sort-by (juxt :sent-at :id) (vals (:messages state)))]
    (if (empty? messages)
      [:main [:p.empty "No messages yet — say the first word."]]
      [:main
       (into [:ol.transcript]
             (map #(message state (:performing state) frames %))
             messages)])))

(defn- presence-line [state awaiting?]
  (let [agent-name (some (fn [[member-id p]]
                           (let [m (get-in state [:members member-id])]
                             (when (and (= :thinking (:state p)) (= :agent (:kind m)))
                               (:display-name m))))
                         (:presence state))]
    [:p.presence {:role "status" :aria-live "polite"}
     (if (and awaiting? agent-name) (str agent-name " is thinking…") "")]))

(defn- composer [{:keys [composer]}]
  [:form.composer {:on {:submit [[:composer/sent]]}}
   [:label {:for "composer-text"} "Message"]
   [:textarea {:id "composer-text"
               :rows 3
               :value (:text composer)
               :on {:input   [[:composer/edited]]
                    :keydown [[:composer/key {:key nil :shift? false}]]}}]
   [:button {:type "submit" :disabled (str/blank? (:text composer))} "Send"]])

(defn- preferences [{:keys [prefs]}]
  [:fieldset.prefs
   [:legend "Preferences"]
   [:label
    [:input {:type "checkbox"
             :checked (boolean (:reduced-motion? prefs))
             :on {:change [[:prefs/changed]]}}]
    " Reduced motion"]
   [:label
    [:input {:type "checkbox" :checked (boolean (:sound? prefs))}]
    " Sound (soon)"]])

(defn page
  "The whole conversation for state."
  [state]
  (let [frames (core/frames state)]
    [:div.page
     (header state)
     (transcript state frames)
     (presence-line state (core/awaiting-agent? state))
     (composer state)
     (preferences state)]))
