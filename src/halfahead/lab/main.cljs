(ns halfahead.lab.main
  "The lab page's shell: the textarea's value goes into the draft, every
  animation frame asks the pure core for a frame and hands it to the
  renderer. All deciding happens in lab.draft, lab.cues and lab.pose,
  the camera included; the shell only reports the viewport's shape."
  (:require [halfahead.lab.cues :as cues]
            [halfahead.lab.debug :as debug]
            [halfahead.lab.draft :as draft]
            [halfahead.lab.pose :as pose]
            [halfahead.lab.render :as render]))

(defonce ^:private state (atom {:draft (draft/empty-draft) :last-frame nil}))

(defn- now-ms [] (js/Math.round (js/performance.now)))

(defn- by-id ^js [id] (js/document.getElementById id))

;; ?debug or ?debug=1: frame timing and recent cues drawn over the stage for
;; screen recordings. Without it no stats or cue log are kept at all.
(def ^:private debug? (.has (js/URLSearchParams. js/location.search) "debug"))

(defn- width-em
  "The layout width that fills about 70% of the viewport at the renderer's
  type size: 18 em, a 36-40 character line, wherever em-px is not floored."
  []
  (let [w js/window.innerWidth]
    (max 4 (/ (* 0.7 w) (render/em-px w)))))

(defn- aspect [] (/ (max 1 js/window.innerWidth) (max 1 js/window.innerHeight)))

(defn- frame [r t reduced-motion?]
  (pose/frame (draft/prune (:draft @state) t) t
              {:advance #(render/advance r %)
               :width-em (width-em)
               :aspect (aspect)
               :reduced-motion? reduced-motion?}))

(defn- camera-line [{:keys [tx ty tz]}]
  (str "camera target x " (.toFixed tx 2) " · y " (.toFixed ty 2) " · z " (.toFixed tz 2)))

(defn- show-debug! [r t f]
  (let [{:keys [stats cue-log]} (swap! state #(-> %
                                                  (update :stats debug/record t)
                                                  (update :cue-log debug/cue-log [] t)))]
    (set! (.-textContent (by-id "debug"))
          (str (debug/overlay-text {:summary (debug/summary stats) :t-ms t
                                    :backend (render/backend r) :glyphs (render/drawn r)
                                    :log cue-log})
               "\n" (camera-line (:camera f))))))

(defn- loop! [r ^js motion-query]
  (let [t (now-ms)
        f (frame r t (.-matches motion-query))]
    (swap! state assoc :last-frame f)
    (render/draw! r f)
    (when debug? (show-debug! r t f))
    (js/requestAnimationFrame #(loop! r motion-query))))

(defn- expose! [r]
  (set! (.-__lab js/window)
        #js {:backend (render/backend r)
             :liveText #(apply str (map :char (remove :ghost? (:poses (:last-frame @state)))))
             :lastFrame #(clj->js (:last-frame @state))
             :drawn #(render/drawn r)
             :lean #(render/lean r %)
             :blockCentre #(render/block-centre r)
             :project #(render/screen-point r %1 %2 %3)
             :advance #(render/advance r %)}))

(defn- edit! [text]
  (let [t (now-ms)]
    (swap! state (fn [{old :draft :as s}]
                   (let [new (draft/edit old text t cues/word-cue)]
                     (cond-> (assoc s :draft new)
                       debug? (update :cue-log debug/cue-log (debug/new-cues old new) t)))))))

(defn- on-input [^js e]
  (edit! (.. e -target -value)))

(defn init! []
  (let [^js input (by-id "type")
        ^js status (by-id "status")
        ;; read on every frame, so a change of the setting applies at once
        motion-query (js/window.matchMedia "(prefers-reduced-motion: reduce)")]
    (when debug? (.removeAttribute (by-id "debug") "hidden"))
    (.focus input)
    (.addEventListener input "input" on-input)
    (-> (render/start! (by-id "stage")
                       (pose/frame (draft/empty-draft) 0 {:width-em (width-em) :aspect (aspect)}))
        (.then (fn [r]
                 (edit! (.-value input))
                 (loop! r motion-query)
                 (expose! r)
                 (set! (.-textContent status) (str "Rendering with " (render/backend r)))))
        (.catch (fn [e]
                  (set! (.-textContent status)
                        (str "Couldn't start the 3D renderer: " (.-message ^js e)))
                  (js/console.error e))))))
