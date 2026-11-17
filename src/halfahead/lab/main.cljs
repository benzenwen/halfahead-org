(ns halfahead.lab.main
  "The lab page's shell: the textarea's value goes into the draft, every
  animation frame asks the pure core for a frame and hands it to the
  renderer. All deciding happens in lab.draft, lab.cues, lab.director,
  lab.style and lab.pose, the camera included; the shell only reports the
  viewport's shape and which motion style the writer picked. With Sound on
  it also plays each new cue's and direction's sound from lab.sound. It
  keeps the reveal's inputs (lab.reveal): the start level from ?reveal=N,
  the times of the writer's wands (Ctrl+. or a mouse flick over the
  stage), and the levels reached so far, so they never go down. Once a
  frame it asks lab.moments what happened since the last one and hands
  those moments, with the frame, to the stage behind the letters, chosen
  with ?stage=name (lab.stage; the grid floor by default)."
  (:require [halfahead.lab.audio :as audio]
            [halfahead.lab.cues :as cues]
            [halfahead.lab.debug :as debug]
            [halfahead.lab.director :as director]
            [halfahead.lab.draft :as draft]
            [halfahead.lab.moments :as moments]
            [halfahead.lab.pose :as pose]
            [halfahead.lab.render :as render]
            [halfahead.lab.reveal :as reveal]
            [halfahead.lab.sound :as sound]
            [halfahead.lab.stage :as stage]))

(defn- start-level
  "?reveal=N, 0 to 3: the reveal level the lab starts at; 0 when absent."
  []
  (let [n (js/parseInt (.get (js/URLSearchParams. js/location.search) "reveal") 10)]
    (if (js/isNaN n) 0 (max 0 (min 3 n)))))

(defonce ^:private state
  ;; :sound-log, the last sounds played, for tests and the console;
  ;; :sound-queue, those not yet handed to the speaker;
  ;; :reveal, lab.reveal's opts; :pointer, the last moments of mouse motion;
  ;; :moment-ctx, the last tick's {:draft :frame} that moments are counted
  ;; from; :last-moment, the newest of them, for the overlay
  (atom {:draft (draft/empty-draft) :last-frame nil :style-choice :auto
         :sound? false :sound-log [] :sound-queue []
         :reveal {:start-level (start-level) :wands [] :reached nil}
         :pointer []
         :moment-ctx nil :last-moment nil}))

(def ^:private chosen-stage
  "[name stage] from ?stage=name; the grid when absent or unknown."
  (stage/named (.get (js/URLSearchParams. js/location.search) "stage")))

;; Only this viewer's choice of Sound, on this device; never sent anywhere.
(def ^:private sound-key "halfahead.lab.sound")

(def ^:private style-keys
  "Ctrl+1..6, in the picker's order."
  {"1" :auto "2" :plain "3" :elegant "4" :caffeinated "5" :drunken "6" :coarse})

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
               :reduced-motion? reduced-motion?
               :style-choice (:style-choice @state)
               :reveal-opts (:reveal @state)}))

(defn- voiced
  "s with the sounds of newly placed entries of a kind logged and queued."
  [s kind entries]
  (let [played (sound/sounds {:sound? (:sound? s)
                              :style (sound/style-in-force (:style-choice s) (:last-frame s))}
                             kind entries)]
    (-> s
        (update :sound-log sound/log played)
        (update :sound-queue into played))))

(defn- play-queued!
  "Hand every queued sound to the speaker, outside any swap!."
  []
  (let [[old _] (swap-vals! state assoc :sound-queue [])]
    (run! #(audio/play! (:sound %)) (:sound-queue old))))

(defn- revealed
  "state with the levels reached by t, never fewer than before."
  [s t]
  (assoc-in s [:reveal :reached] (reveal/levels (:draft s) t (:reveal s))))

(defn- level [s t] (reveal/level-at (get-in s [:reveal :reached]) (:reveal s) t))

(defn- camera-line [{:keys [tx ty tz]}]
  (str "camera target x " (.toFixed tx 2) " · y " (.toFixed ty 2) " · z " (.toFixed tz 2)))

(defn- moment-line [{:keys [kind effect intensity shot energy level at-ms] :as m}]
  (str "stage " (name (first chosen-stage)) " · moment "
       (if m
         (str (name kind) " "
              (case kind
                :cue (str (name effect) " " (.toFixed intensity 1))
                :shot (str (name shot) " " (.toFixed energy 1))
                :level level)
              " at " (.toFixed (/ at-ms 1000) 3) " s")
         "none")))

(defn- show-debug! [r t f]
  (let [{:keys [stats cue-log direction-log style-choice sound? last-moment]}
        (swap! state #(-> %
                          (update :stats debug/record t)
                          (update :cue-log debug/cue-log [] t)
                          (update :direction-log debug/cue-log [] t)))]
    (set! (.-textContent (by-id "debug"))
          (str (debug/overlay-text {:summary (debug/summary stats) :t-ms t
                                    :backend (render/backend r) :glyphs (render/drawn r)
                                    :style (:style f) :style-choice style-choice
                                    :log cue-log :directions direction-log})
               "\n" (camera-line (:camera f))
               "\n" (let [{:keys [level lights]} (:reveal f)]
                      (str "reveal level " level " · lights " (.toFixed lights 2)))
               "\n" (moment-line last-moment)
               "\n" (if sound? "sound on" "sound off")))))

(defn- directed
  "state with its levels brought up to t and, from level 2, the director
  run over its draft: on every edit, and on every frame for phrases that a
  pause ended. Its first spectacle is what opens level 3."
  [s t]
  (let [{old :draft :as s} (revealed s t)]
    (if (< (level s t) 2)
      s
      (let [new (director/direct old t)
            placed (when (or debug? (:sound? s)) (debug/new-directions old new))]
        (cond-> (assoc s :draft new)
          debug? (update :direction-log debug/cue-log placed t)
          true (voiced :shot placed)
          true (revealed t))))))

(defn- show-lights!
  "The page's own colours follow the house lights: data-lights on <html>."
  [{:keys [lights]}]
  (let [v (if (>= lights 0.5) "down" "up")
        ^js root js/document.documentElement]
    (when (not= v (.. root -dataset -lights))
      (set! (.. root -dataset -lights) v))))

(defn- loop! [r ^js motion-query]
  (let [t (now-ms)
        _ (swap! state directed t)
        _ (play-queued!)
        reduced-motion? (.-matches motion-query)
        f (frame r t reduced-motion?)
        ;; counted only here, once a tick, so each moment is handed on once
        ctx {:draft (:draft @state) :frame f}
        ms (moments/moments (:moment-ctx @state) ctx)]
    (swap! state #(cond-> (assoc % :last-frame f :moment-ctx ctx)
                    (seq ms) (assoc :last-moment (peek ms))))
    (show-lights! (:reveal f))
    ;; with reduced motion the stage stays at rest: no flashes or pulses
    (render/draw! r f (if reduced-motion? [] ms))
    (when debug? (show-debug! r t f))
    (js/requestAnimationFrame #(loop! r motion-query))))

(defn- expose! [r]
  (set! (.-__lab js/window)
        #js {:backend (render/backend r)
             :liveText #(apply str (map :char (remove :ghost? (:poses (:last-frame @state)))))
             :lastFrame #(clj->js (:last-frame @state))
             :directions #(clj->js (:directions (:draft @state)))
             :drawn #(render/drawn r)
             :lean #(render/lean r %)
             :blockCentre #(render/block-centre r)
             :project #(render/screen-point r %1 %2 %3)
             :advance #(render/advance r %)
             :soundLog #(clj->js (:sound-log @state))
             :stage (name (first chosen-stage))
             :stageState #(clj->js (render/stage-state r))
             :bounds #(clj->js sound/bounds)}))

(defn- edit! [text]
  (let [t (now-ms)]
    (swap! state (fn [{old :draft :as s}]
                   ;; draft/edit builds a new draft without :directions
                   (let [new (-> (draft/edit old text t cues/word-cue)
                                 (assoc :directions (:directions old [])))
                         placed (when (or debug? (:sound? s)) (debug/new-cues old new))]
                     (cond-> (assoc s :draft new)
                       debug? (update :cue-log debug/cue-log placed t)
                       true (voiced :cue placed)
                       true (directed t)))))
    (play-queued!)))

(defn- wand!
  "The writer's wand: one level more, from now."
  []
  (let [t (now-ms)]
    (swap! state (fn [s]
                   (cond-> s
                     (< (level s t) 3) (-> (update-in [:reveal :wands] conj t)
                                           (directed t)))))
    (play-queued!)))

(def ^:private flick-window-ms
  "How much recent mouse motion is kept to look for a flick in."
  300)

(defn- on-pointer-move
  "A quick sweep of the mouse over the stage is a wand; the samples start
  afresh after one, so one sweep is one wand."
  [^js e]
  (when (= "mouse" (.-pointerType e))
    ;; when the pointer moved, not when a busy frame let the handler run
    (let [t (.-timeStamp e)
          samples (into [] (filter #(< (- t (:t %)) flick-window-ms))
                        (conj (:pointer @state) {:x (.-clientX e) :y (.-clientY e) :t t}))]
      (if (reveal/flick? samples)
        (do (swap! state assoc :pointer []) (wand!))
        (swap! state assoc :pointer samples)))))

(defn- on-input [^js e]
  (edit! (.. e -target -value)))

(defn- choose-style!
  "Perform from now on in choice, keep the picker showing it, and give the
  textarea its focus back so typing carries on."
  [choice ^js picker ^js input]
  (swap! state assoc :style-choice choice)
  (set! (.-value picker) (name choice))
  (.focus input))

(defn- remembered-sound?
  "Whether this viewer turned Sound on here before; off when storage is
  empty, blocked or throws."
  []
  (try (= "on" (.getItem js/localStorage sound-key))
       (catch :default _ false)))

(defn- remember-sound! [on?]
  (try (.setItem js/localStorage sound-key (if on? "on" "off"))
       (catch :default _ nil)))

(defn- choose-sound!
  "Sound on or off from now on, remembered on this device. Turning it on
  is a click, a user gesture, so the audio is unlocked here."
  [on? ^js input]
  (swap! state #(cond-> (assoc % :sound? on?)
                  (not on?) (assoc :sound-log [] :sound-queue [])))
  (remember-sound! on?)
  (when on? (audio/unlock!))
  (.focus input))

(defn- on-gesture
  "Sound remembered on from an earlier visit stays silent until the
  viewer's first key or click here, a user gesture that may unlock it."
  [_]
  (when (:sound? @state) (audio/unlock!)))

(defn- on-ctrl-key [picker input ^js e]
  (when (and (.-ctrlKey e) (not (.-altKey e)) (not (.-metaKey e)))
    (if-let [choice (style-keys (.-key e))]
      (do (.preventDefault e)
          (choose-style! choice picker input))
      (when (= "." (.-key e))
        (.preventDefault e)
        (wand!)))))

(defn init! []
  (let [^js input (by-id "type")
        ^js status (by-id "status")
        ^js picker (by-id "style")
        ^js sound-box (by-id "sound")
        ;; read on every frame, so a change of the setting applies at once
        motion-query (js/window.matchMedia "(prefers-reduced-motion: reduce)")]
    (when debug? (.removeAttribute (by-id "debug") "hidden"))
    (show-lights! {:lights (if (>= (get-in @state [:reveal :start-level]) 2) 1 0)})
    (.addEventListener js/window "pointermove" on-pointer-move)
    (.focus input)
    (.addEventListener input "input" on-input)
    (set! (.-value picker) (name (:style-choice @state)))
    (.addEventListener picker "change"
                       #(choose-style! (keyword (.-value picker)) picker input))
    (.addEventListener js/window "keydown" #(on-ctrl-key picker input %))
    (swap! state assoc :sound? (remembered-sound?))
    (set! (.-checked sound-box) (:sound? @state))
    (.addEventListener sound-box "change" #(choose-sound! (.-checked sound-box) input))
    (.addEventListener js/window "keydown" on-gesture)
    (.addEventListener js/window "pointerdown" on-gesture)
    (-> (render/start! (by-id "stage")
                       (pose/frame (draft/empty-draft) 0 {:width-em (width-em) :aspect (aspect)})
                       (second chosen-stage))
        (.then (fn [r]
                 (edit! (.-value input))
                 (loop! r motion-query)
                 (expose! r)
                 (set! (.-textContent status) (str "Rendering with " (render/backend r)))))
        (.catch (fn [e]
                  (set! (.-textContent status)
                        (str "Couldn't start the 3D renderer: " (.-message ^js e)))
                  (js/console.error e))))))
