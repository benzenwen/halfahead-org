(ns halfahead.app
  "The browser shell: one state atom, rendered by Replicant on every
  change. View actions are completed by halfahead.app.enrich, handled by
  halfahead.app.core, and the effects they return are run by the
  interpreter."
  (:require [halfahead.app.core :as core]
            [halfahead.app.enrich :as enrich]
            [halfahead.app.views :as views]
            [halfahead.fx.firebase :as firebase]
            [replicant.dom :as r]))

(defonce ^:private state (atom core/initial-state))
(defonce ^:private ticking? (atom false))

(defn- reduced-motion-query []
  (js/window.matchMedia "(prefers-reduced-motion: reduce)"))

(declare handle!)

(defn- tick! []
  (if (seq (:performing @state))
    (do (handle! [:clock/ticked {:now-ms (js/Date.now)}])
        (js/requestAnimationFrame tick!))
    (reset! ticking? false)))

(defn- ensure-ticking! []
  (when (and (seq (:performing @state)) (not @ticking?))
    (reset! ticking? true)
    (js/requestAnimationFrame tick!)))

(defn- handle! [action]
  (let [{new-state :state effects :effects} (core/handle @state action)]
    (reset! state new-state)
    (ensure-ticking!)
    (run! firebase/run-effect! effects)))

(defn- enrich-context [event]
  {:now-ms  (js/Date.now)
   :now     (js/Date.)
   :date    (.toLocaleDateString (js/Date.) "en-GB"
                                 #js {:day "numeric" :month "long" :year "numeric"})
   :new-id  (str (js/crypto.randomUUID))
   :value   (some-> event .-target .-value)
   :key     (some-> event .-key)
   :shift?  (boolean (some-> event .-shiftKey))})

(defn- dispatch! [{:replicant/keys [dom-event node]} actions]
  (let [ctx (enrich-context dom-event)]
    (doseq [action actions
            :when (vector? action)]
      (if (= [:dom/focus] action)
        ;; a DOM-only life-cycle action: it never reaches the core
        (some-> node .focus)
        (do
          (when (and dom-event (enrich/prevent-default? action ctx))
            (.preventDefault dom-event))
          (run! handle! (enrich/enrich action ctx)))))))

(defn- render! []
  (r/render (js/document.getElementById "app") (views/page @state)))

(defn ^:export init! []
  (r/set-dispatch! dispatch!)
  (add-watch state ::render (fn [_ _ _ _] (render!)))
  (let [mq (reduced-motion-query)]
    (handle! [:prefs/changed {:reduced-motion? (.-matches mq)}])
    (.addEventListener mq "change"
                       #(handle! [:prefs/changed {:reduced-motion? (.-matches mq)}])))
  (render!)
  (firebase/init! {:dispatch! handle!
                   :reduced-motion? #(.-matches (reduced-motion-query))}))
