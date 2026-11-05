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
  "The layout width that fills about 70% of the viewport at the renderer's
  type size: 18 em, a 36-40 character line, wherever em-px is not floored."
  []
  (let [w js/window.innerWidth]
    (max 4 (/ (* 0.7 w) (render/em-px w)))))

(defn- frame [r reduced-motion?]
  (let [t (now-ms)]
    (pose/frame (draft/prune (:draft @state) t) t
                {:advance #(render/advance r %)
                 :width-em (width-em)
                 :reduced-motion? reduced-motion?})))

(defn- loop! [r ^js motion-query]
  (let [still? (.-matches motion-query)
        f (frame r still?)]
    (swap! state assoc :last-frame f)
    (render/draw! r f still?)
    (js/requestAnimationFrame #(loop! r motion-query))))

(defn- expose! [r]
  (set! (.-__lab js/window)
        #js {:backend (render/backend r)
             :liveText #(apply str (map :char (remove :ghost? (:poses (:last-frame @state)))))
             :lastFrame #(clj->js (:last-frame @state))
             :drawn #(render/drawn r)
             :lean #(render/lean r %)
             :blockCentre #(render/block-centre r)}))

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
