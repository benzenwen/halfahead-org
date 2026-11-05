(ns halfahead.lab.main
  "The lab page's shell: the textarea's value goes into the draft, every
  animation frame asks the pure core for a frame and hands it to the
  renderer. All deciding happens in lab.draft, lab.cues and lab.pose."
  (:require [halfahead.lab.cues :as cues]
            [halfahead.lab.draft :as draft]
            [halfahead.lab.pose :as pose]
            [halfahead.lab.render :as render]))

(defonce ^:private state (atom {:draft (draft/empty-draft) :last-frame nil}))

(defn- now-ms [] (js/Math.round (js/performance.now)))

(defn- by-id ^js [id] (js/document.getElementById id))

(defn- width-em
  "The layout width that fits the viewport, less a margin of 1 em a side."
  []
  (let [w js/window.innerWidth]
    (max 4 (- (/ w (render/em-px w)) 2))))

(defn- frame [r reduced-motion?]
  (let [t (now-ms)]
    (pose/frame (draft/prune (:draft @state) t) t
                {:advance #(render/advance r %)
                 :width-em (width-em)
                 :reduced-motion? reduced-motion?})))

(defn- loop! [r ^js motion-query]
  (let [f (frame r (.-matches motion-query))]
    (swap! state assoc :last-frame f)
    (render/draw! r f)
    (js/requestAnimationFrame #(loop! r motion-query))))

(defn- expose! [r]
  (set! (.-__lab js/window)
        #js {:backend (render/backend r)
             :liveText #(apply str (map :char (remove :ghost? (:poses (:last-frame @state)))))
             :lastFrame #(clj->js (:last-frame @state))
             :drawn #(render/drawn r)}))

(defn- on-input [^js e]
  (swap! state update :draft draft/edit (.. e -target -value) (now-ms) cues/word-cue))

(defn init! []
  (let [^js input (by-id "type")
        ^js status (by-id "status")
        ;; read on every frame, so a change of the setting applies at once
        motion-query (js/window.matchMedia "(prefers-reduced-motion: reduce)")]
    (.focus input)
    (.addEventListener input "input" on-input)
    (-> (render/start! (by-id "stage"))
        (.then (fn [r]
                 (swap! state update :draft draft/edit (.-value input) (now-ms) cues/word-cue)
                 (loop! r motion-query)
                 (expose! r)
                 (set! (.-textContent status) (str "Rendering with " (render/backend r)))))
        (.catch (fn [e]
                  (set! (.-textContent status)
                        (str "Couldn't start the 3D renderer: " (.-message ^js e)))
                  (js/console.error e))))))
