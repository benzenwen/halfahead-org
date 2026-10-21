(ns halfahead.app.views
  "The conversation as hiccup. Pure: state in, data out. Motion appears only
  as each segment's --p (its frame progress); the stylesheet decides what
  progress looks like. Events are data without times or ids; the shell adds
  those before handling."
  (:require [clojure.string :as str]
            [halfahead.app.core :as core]))

(defn- display-name [state member-id]
  (or (get-in state [:members member-id :display-name]) member-id))

;; The separator is its own span so the .owner text stays exactly
;; "agent of <name>" while the author line reads "Scout · agent of Ben".
(defn- owner-line [state {:keys [kind owner-id]}]
  (when (= :agent kind)
    (list [:span.sep " · "]
          [:span.owner "agent of " (display-name state owner-id)])))

(defn- presence-state [state member-id]
  (get-in state [:presence member-id :state] :idle))

;; Shape is the state: a diamond at rest, a stellated star while thinking.
;; Colour comes from the stylesheet via currentColor; the presence line
;; carries the accessible signal.
(defn- glyph [state member-id]
  (let [current (presence-state state member-id)]
    [:svg.glyph {:viewBox "0 0 24 24"
                 :aria-hidden "true"
                 :focusable "false"
                 :data-state (name current)}
     (if (= :thinking current)
       [:g {:fill "currentColor"}
        [:path {:d "M12 1 L14.5 9.5 L23 12 L14.5 14.5 L12 23 L9.5 14.5 L1 12 L9.5 9.5 Z"}]
        [:path {:d "M12 6 L18 12 L12 18 L6 12 Z" :fill "none" :stroke "currentColor"
                :stroke-width "1.5"}]]
       [:path {:d "M12 3 L21 12 L12 21 L3 12 Z" :fill "currentColor"}])]))

(defn- member-chip [state {:keys [id kind display-name] :as member}]
  [:li.chip {:key id :class [(name kind)]}
   (when (= :agent kind) (glyph state id))
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
     [:article
      [:p.author
       (when (= :agent (:kind author)) (glyph state author-id))
       [:span.name (display-name state author-id)]
       (when author (owner-line state author))]
      [:p.body
       [:span.sr-only text]
       (into [:span {:aria-hidden "true"}] (map segment) (get frames id))]
      [:button.replay {:type "button"
                       :aria-label "Replay message"
                       :on {:click [[:replay/requested {:id id}]]}}
       "↻"]
      (when (contains? performing id)
        [:button.skip {:type "button"
                       :on {:click [[:message/skipped {:id id}]]}}
         "Skip"])]]))

(defn- transcript [state frames]
  (let [messages (sort-by (juxt :sent-at :id) (vals (:messages state)))]
    (if (empty? messages)
      [:main [:p.empty "No messages yet — say the first word."]]
      [:main
       (into [:ol.transcript]
             (map #(message state (:performing state) frames %))
             messages)])))

(defn- presence-line [state awaiting?]
  (let [[agent-id agent-name]
        (some (fn [[member-id p]]
                (let [m (get-in state [:members member-id])]
                  (when (and (= :thinking (:state p)) (= :agent (:kind m)))
                    [member-id (:display-name m)])))
              (:presence state))]
    ;; The region stays mounted for aria-live; the glyph appears only beside
    ;; the text it illustrates.
    (if (and awaiting? agent-name)
      [:p.presence {:role "status" :aria-live "polite"}
       (glyph state agent-id)
       (str agent-name " is thinking…")]
      [:p.presence {:role "status" :aria-live "polite"} ""])))

;; Same guidance as the agent's instructions (halfahead.worker.core), trimmed
;; for a person; that namespace is .clj so it cannot be required here.
(def ^:private delivery-options
  [[:plain    "the default; most lines."]
   [:typed    "when the pause itself means something."]
   [:headline "one striking point."]
   [:aside    "a quiet remark or caveat."]
   [:credits  "thanks, attributions, or a short list."]])

(defn- delivery-picker [current]
  (into [:div.delivery {:role "radiogroup" :aria-label "Delivery"}]
        (for [[d hint] delivery-options]
          [:label {:key (name d) :title hint}
           [:input {:type "radio"
                    :name "delivery"
                    :value (name d)
                    :checked (= d current)
                    :on {:change [[:composer/delivery-chosen {:delivery d}]]}}]
           [:span (name d)]])))

(defn- composer [{:keys [composer]}]
  [:form.composer {:on {:submit [[:composer/sent]]}}
   [:label {:for "composer-text"} "Message"]
   [:textarea {:id "composer-text"
               :rows 3
               :value (:text composer)
               :on {:input   [[:composer/edited]]
                    :keydown [[:composer/key {:key nil :shift? false}]]}}]
   [:div.send-row
    (delivery-picker (get composer :delivery :plain))
    [:button {:type "submit" :disabled (str/blank? (:text composer))} "Send"]]])

(defn- preferences [{:keys [prefs]}]
  (let [reduced? (boolean (:reduced-motion? prefs))
        sound? (boolean (:sound? prefs))]
    [:fieldset.prefs
     [:legend "Preferences"]
     [:label
      [:input {:type "checkbox"
               :checked reduced?
               :on {:change [[:prefs/changed {:reduced-motion? (not reduced?)}]]}}]
      " Reduced motion"]
     [:label
      [:input {:type "checkbox"
               :checked sound?
               :on {:change [[:prefs/changed {:sound? (not sound?)}]]}}]
      " Sound (soon)"]]))

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
