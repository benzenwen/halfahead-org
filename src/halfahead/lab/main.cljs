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
  with ?stage=name (lab.stage; the grid floor by default). With ?specimen
  it draws lab.specimen's type specimen on every frame instead of the
  draft, and typing does nothing. Ctrl+Enter or the Read back button reads
  the draft back at the viewer's Pace (lab.reading), in the style in force
  as it starts: while it plays the shell hands the reading's cues, the
  head's ink and that style to the frame, whatever Auto would make of the
  rhythm meanwhile, and Escape, an edit or another reading ends it. It
  keeps the draft's dress (lab.dress) for the frame too: decided by the
  style in force when the text last changed or the writer picked a style,
  so it stays while the writer is idle, with when each was first seen,
  and where the mouse is over the stage, which gold glints by. With ?webgl the renderer
  starts on WebGL2 without trying WebGPU, for a browser whose WebGPU
  misbehaves. With ?tour, or from the Tour button, it plays lab.tour's
  script on a timer of its own through these same paths (the textarea's
  input event, the picker, the wand, the read-back, the light), until the
  script ends or the visitor touches a key or the page."
  (:require [clojure.string :as str]
            [halfahead.lab.audio :as audio]
            [halfahead.lab.cues :as cues]
            [halfahead.lab.debug :as debug]
            [halfahead.lab.director :as director]
            [halfahead.lab.draft :as draft]
            [halfahead.lab.dress :as dress]
            [halfahead.lab.ink :as ink]
            [halfahead.lab.instance :as instance]
            [halfahead.lab.moments :as moments]
            [halfahead.lab.pace :as pace]
            [halfahead.lab.pose :as pose]
            [halfahead.lab.reading :as reading]
            [halfahead.lab.render :as render]
            [halfahead.lab.reveal :as reveal]
            [halfahead.lab.score :as score]
            [halfahead.lab.sound :as sound]
            [halfahead.lab.specimen :as specimen]
            [halfahead.lab.stage :as stage]
            [halfahead.lab.style :as style]
            [halfahead.lab.tour :as tour]))

(defn- start-level
  "?reveal=N, 0 to 3: the reveal level the lab starts at; 0 when absent."
  []
  (let [n (js/parseInt (.get (js/URLSearchParams. js/location.search) "reveal") 10)]
    (if (js/isNaN n) 0 (max 0 (min 3 n)))))

(def ^:private specimen?
  "?specimen: the page shows the type specimen, not what is typed."
  (.has (js/URLSearchParams. js/location.search) "specimen"))

(def ^:private specimen-lights
  "The specimen's house lights from ?reveal: paper (0) at level 0 or 1,
  the dark stage (1) at 2 or 3 and when absent."
  (let [n (js/parseInt (.get (js/URLSearchParams. js/location.search) "reveal") 10)]
    (if (< n 2) 0 1)))

(defonce ^:private state
  ;; :sound-log, the last sounds played, for tests and the console;
  ;; :sound-queue, those not yet handed to the speaker;
  ;; :reveal, lab.reveal's opts; :pointer, the last moments of mouse motion;
  ;; :moment-ctx, the last tick's {:draft :frame} that moments are counted
  ;; from; :last-moment, the newest of them, for the overlay;
  ;; :pace, the viewer's words a minute; :reading, the read-back playing, if
  ;; any: {:plan (lab.reading's) :style (the named style it was planned in
  ;; and plays in) :t0 :cues (its placed cues) :voiced-to (the time up to
  ;; which its cues' sounds were played)};
  ;; :dress, what the draft wears: {:point (the style point that decided
  ;; it) :placed :seen (both lab.dress's)}; :light-px, where the
  ;; mouse is in client pixels, nil when it is away or there is none;
  ;; :tour, the tour playing, if any: {:segments (lab.tour's plan) :segment
  ;; (the index of the one playing) :t0 (when its clock began) :done (the
  ;; indexes of its steps that are over) :awaiting-read? (the segment is
  ;; over and the next waits for the read-back) :scene :typing (the :type
  ;; step under way: {:base (the text before it) :typed (keys so far)})
  ;; :lit? (a :light step holds the light) :before (the visitor's {:style
  ;; :pace}) :timer :on-touch}; :caption-timer, the timeout that will take
  ;; the caption away
  (atom {:draft (draft/empty-draft) :last-frame nil :style-choice :auto
         :pace 250 :reading nil
         :dress {:point nil :placed [] :seen {}} :light-px nil
         :tour nil :caption-timer nil
         :sound? false :sound-log [] :sound-queue []
         :reveal {:start-level (start-level) :wands [] :reached nil}
         :pointer []
         :moment-ctx nil :last-moment nil}))

(def ^:private chosen-stage
  "[name stage] from ?stage=name; the grid when absent or unknown."
  (stage/named (.get (js/URLSearchParams. js/location.search) "stage")))

;; Only this viewer's choice of Sound, on this device; never sent anywhere.
(def ^:private sound-key "halfahead.lab.sound")

;; Likewise this viewer's reading pace.
(def ^:private pace-key "halfahead.lab.pace")
(def ^:private slowest-pace 150)
(def ^:private fastest-pace 450)
(def ^:private usual-pace 250)

(def ^:private style-keys
  "Ctrl+1..6, in the picker's order."
  {"1" :auto "2" :plain "3" :elegant "4" :caffeinated "5" :drunken "6" :coarse})

(defn- now-ms [] (js/Math.round (js/performance.now)))

(defn- by-id ^js [id] (js/document.getElementById id))

;; ?debug or ?debug=1: frame timing and recent cues drawn over the stage for
;; screen recordings. Without it no stats or cue log are kept at all.
(def ^:private debug? (.has (js/URLSearchParams. js/location.search) "debug"))

(def ^:private webgl?
  "?webgl: start on WebGL2 and leave WebGPU alone."
  (.has (js/URLSearchParams. js/location.search) "webgl"))

(defn- width-em
  "The layout width that fills about 70% of the viewport at the renderer's
  type size: 18 em, a 36-40 character line, wherever em-px is not floored."
  []
  (let [w js/window.innerWidth]
    (max 4 (/ (* 0.7 w) (render/em-px w)))))

(defn- aspect [] (/ (max 1 js/window.innerWidth) (max 1 js/window.innerHeight)))

(defn- performed-draft
  "The draft as it is performed: with the cues of the reading playing, if
  any, after its own. The state's draft is never changed by a reading."
  [{:keys [draft reading]}]
  (cond-> draft
    reading (update :cues into (:cues reading))))

(defn- style-at
  "The named style in force at t: the one picked, or under :auto the one
  the typing rhythm suggests then."
  [{:keys [draft style-choice]} t]
  (if (= :auto style-choice) (style/infer (:glyphs draft) t) style-choice))

(defn- choice-in-force
  "What the frame performs in: the style of the reading playing, if any,
  so its gestures keep the lengths they were granted; else the picker's
  choice."
  [{:keys [reading style-choice]}]
  (if reading (:style reading) style-choice))

(defn- seen
  "state with each dress's first sighting brought up to t."
  [s t]
  (update s :dress #(assoc % :seen (dress/seen (:seen %) (:placed %) t))))

(defn- redressed
  "state with the draft dressed by the style in force at t. Only an edit
  and the writer's pick of a style come here: between them the dress
  stays as it is, whatever :auto makes of the silence."
  [{:keys [draft] :as s} t]
  (let [{:keys [text glyphs]} draft
        point (style/point (style-at s t))]
    (-> s
        (update :dress assoc
                :point point
                :placed (dress/placed (:dress (score/from-text text point)) glyphs))
        (seen t))))

(defn- frame [r t reduced-motion?]
  (let [{:keys [draft reading light-px] :as s} @state]
    (pose/frame (draft/prune (performed-draft s) t) t
                {:advance #(render/advance r %)
                 :width-em (width-em)
                 :aspect (aspect)
                 :reduced-motion? reduced-motion?
                 :style-choice (choice-in-force s)
                 :reveal-opts (:reveal s)
                 :dress {:placed (get-in s [:dress :placed])
                         :seen (get-in s [:dress :seen])
                         :light (when-let [[x y] light-px] (render/plane-point r x y))}
                 :fills (when reading
                          (reading/fills (:plan reading) (:glyphs draft) (:t0 reading) t))})))

(defn- specimen-opts [r]
  {:advance #(render/advance r %) :width-em (width-em) :aspect (aspect)
   :lights specimen-lights})

(defn- voiced
  "s with the sounds of newly placed entries of a kind logged and queued."
  [s kind entries]
  (let [played (sound/sounds {:sound? (:sound? s)
                              :style (sound/style-in-force (choice-in-force s) (:last-frame s))}
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
  "state with the levels reached by t, never fewer than before. A
  reading's cues are use like any other, so a level they reach is kept
  when the reading ends."
  [s t]
  (assoc-in s [:reveal :reached] (reveal/levels (performed-draft s) t (:reveal s))))

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
  (let [{:keys [stats cue-log direction-log style-choice sound? last-moment reading draft]
         worn :dress}
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
               "\n" (if sound? "sound on" "sound off")
               (when-let [line (debug/dress-text (:placed worn) (:glyphs draft))]
                 (str "\n" line))
               (when reading
                 (str "\n" (debug/reading-text (:reading (:plan reading))
                                               (map :cue (:cues reading)))))))))

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

(defn- specimen-loop!
  "The specimen, drawn afresh every frame so it follows the window's shape;
  no draft, director, sound or moments."
  [r]
  (let [f (specimen/frame (specimen-opts r))]
    (swap! state assoc :last-frame f)
    (show-lights! (:reveal f))
    (render/draw! r f [])
    (when debug? (show-debug! r (now-ms) f))
    (js/requestAnimationFrame #(specimen-loop! r))))

(defn- sample-boxes
  "The specimen's samples, each with the box its glyphs were drawn in, in
  CSS pixels; nil off the specimen page."
  [r]
  (when specimen?
    (let [index (into {} (map-indexed (fn [i p] [(:id p) i])) (:poses (:last-frame @state)))]
      (clj->js
       (for [{:keys [glyph-ids] :as s} (specimen/samples (specimen-opts r))
             :let [[x0 y0 x1 y1] (render/glyph-box r (map index glyph-ids))]]
         (assoc s :box {:x0 x0 :y0 y0 :x1 x1 :y1 y1}))))))

(defn- read-on
  "state with the reading playing, if any, brought up to t: ended once it
  is over or motion is reduced, and the sounds of the cues that began
  since the last tick logged and queued."
  [{:keys [draft reading] :as s} t reduced-motion?]
  (cond
    (nil? reading) s

    (or reduced-motion? (reading/over? (:plan reading) (:glyphs draft) (:t0 reading) t))
    (assoc s :reading nil)

    :else
    (let [begun (for [{:keys [cue at-ms]} (:cues reading)
                      :when (and (< (:voiced-to reading) at-ms) (<= at-ms t))]
                  (assoc cue :at-ms at-ms))]
      (-> s
          (assoc-in [:reading :voiced-to] t)
          (voiced :cue begun)))))

(defn- loop! [r ^js motion-query]
  (let [t (now-ms)
        reduced-motion? (.-matches motion-query)
        _ (swap! state #(-> % (read-on t reduced-motion?) (directed t) (seen t)))
        _ (play-queued!)
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
             ;; what WebGPU has reported since the start, and the vertex
             ;; buffers the glyph pipeline may bind (lab.instance)
             :gpuErrors #(clj->js (render/gpu-errors r))
             :vertexBuffers #(instance/vertex-buffers)
             ;; the read-back playing, times on the lab's clock; nil if none
             :reading #(when-let [{:keys [plan t0]} (:reading @state)]
                         (let [{:keys [duration-ms beat-ms stops entries] :as rd} (:reading plan)]
                           #js {:t0 t0
                                :durationMs duration-ms
                                :beatMs beat-ms
                                :stops (clj->js stops)
                                :entries (clj->js entries)
                                :performing (clj->js (:performing plan))
                                :headAt (fn [at] (pace/head rd (- at t0)))}))
             :bounds #(clj->js sound/bounds)
             :specimen #(sample-boxes r)
             ;; the dress placed on the draft's glyphs, as lab.dress has it
             :dress #(clj->js (get-in @state [:dress :placed]))
             ;; an ink's colour as the 0-255 triple a screenshot shows
             ;; the tour playing, as plain data; nil when none is
             :tour #(when-let [{:keys [scene segment]} (:tour @state)]
                      #js {:scene scene
                           :caption (tour/caption tour/script scene)
                           :segment segment
                           :running true})
             :inkColour #(clj->js (mapv (fn [c] (js/Math.round (* 255 c)))
                                        (ink/colour (keyword %1) %2)))}))

(defn- edit! [text]
  (let [t (now-ms)]
    (swap! state (fn [{old :draft :as s}]
                   ;; draft/edit builds a new draft without :directions
                   (let [new (-> (draft/edit old text t cues/word-cue)
                                 (assoc :directions (:directions old [])))
                         placed (when (or debug? (:sound? s)) (debug/new-cues old new))]
                     ;; a reading is of the text as it was: an edit ends it
                     (cond-> (assoc s :draft new :reading nil)
                       debug? (update :cue-log debug/cue-log placed t)
                       true (voiced :cue placed)
                       true (directed t)
                       true (redressed t)))))
    (play-queued!)))

(defn- read-back!
  "Read the draft back from now, as the style in force now performs it
  (kept for the whole reading) and at the viewer's pace, in place of any
  reading already playing. Nothing with
  reduced motion (the text is already all there), on the specimen, or
  before there is a message to read."
  [^js motion-query]
  (when-not (or specimen? (.-matches motion-query))
    (let [t (now-ms)]
      (swap! state
             (fn [{:keys [draft pace] :as s}]
               (let [named (style-at s t)
                     ;; the tempo lab.pose plays a named style at is its
                     ;; params', not its point's
                     plan (reading/plan (:text draft) (style/point named)
                                        (style/params named) {:wpm pace})]
                 (assoc s :reading (when plan
                                     {:plan plan
                                      :style named
                                      :t0 t
                                      :cues (reading/cues plan (:glyphs draft) t)
                                      :voiced-to (dec t)}))))))))

(defn- stop-reading!
  "End the reading playing, if any: everything is lit at once."
  []
  (swap! state assoc :reading nil))

(defn- remembered-pace
  "The pace this viewer chose here before, kept within the slider's range;
  the usual one when storage is empty, blocked or throws."
  []
  (let [n (try (js/parseInt (.getItem js/localStorage pace-key) 10)
               (catch :default _ js/NaN))]
    (if (js/isNaN n) usual-pace (max slowest-pace (min fastest-pace n)))))

(defn- remember-pace! [wpm]
  (try (.setItem js/localStorage pace-key (str wpm))
       (catch :default _ nil)))

(defn- show-pace! [wpm]
  (set! (.-value (by-id "pace")) (str wpm))
  (set! (.-textContent (by-id "pace-value")) (str wpm)))

(defn- set-pace!
  "Readings from now on are at wpm words a minute, and the slider says so."
  [wpm]
  (swap! state assoc :pace wpm)
  (show-pace! wpm))

(defn- choose-pace!
  "The viewer's own choice of pace: set, and remembered on this device."
  [wpm]
  (let [wpm (max slowest-pace (min fastest-pace wpm))]
    (set-pace! wpm)
    (remember-pace! wpm)))

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
  afresh after one, so one sweep is one wand. Where the mouse is is also
  the light gold glints by; a finger or a pen is no light."
  [^js e]
  (when (= "mouse" (.-pointerType e))
    (swap! state assoc :light-px [(.-clientX e) (.-clientY e)])
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
  (swap! state #(-> % (assoc :style-choice choice) (redressed (now-ms))))
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

(defn- on-key [picker input motion-query ^js e]
  (cond
    ;; left to do whatever else it does: stopping a reading takes nothing
    ;; from the key
    (= "Escape" (.-key e))
    (stop-reading!)

    (and (.-ctrlKey e) (not (.-altKey e)) (not (.-metaKey e)))
    (if-let [choice (style-keys (.-key e))]
      (do (.preventDefault e)
          (choose-style! choice picker input))
      (case (.-key e)
        "." (do (.preventDefault e) (wand!))
        "Enter" (do (.preventDefault e) (read-back! motion-query))
        nil))))

;; --- the tour ----------------------------------------------------------------

(def ^:private tour? (.has (js/URLSearchParams. js/location.search) "tour"))

(def ^:private your-turn "Your turn.")
(def ^:private your-turn-ms 2000)

(def ^:private no-tour
  "The tour is motion. Reduced motion is on, so here is the lab instead.")

(defn- show-caption!
  "The caption bar reads text, for good; with no text it is out of sight."
  [text]
  (js/clearTimeout (:caption-timer @state))
  (set! (.-textContent (by-id "caption")) text)
  (.toggle (.-classList (by-id "tour-bar")) "idle" (= "" text)))

(defn- show-caption-for! [text ms]
  (show-caption! text)
  (swap! state assoc :caption-timer (js/setTimeout #(show-caption! "") ms)))

(defn- enter!
  "The text box holds text, by the input event a key press ends in, so the
  draft, its cues, Auto's rhythm, the dress and the director follow as
  they do for a person."
  [^js input text input-type]
  (set! (.-value input) text)
  (.dispatchEvent input (js/InputEvent. "input" #js {:bubbles true :inputType input-type})))

(defn- phrase-box
  "[x0 y0 x1 y1] in client pixels around the letters of the draft's first
  phrase as they were last drawn; nil while it is not on the page."
  [r phrase]
  (let [{:keys [draft last-frame]} @state]
    (when-let [from (str/index-of (:text draft) phrase)]
      (let [to (+ from (count phrase))
            ids (:ids (reduce (fn [{:keys [at ids]} {:keys [id char]}]
                                (let [end (+ at (count char))]
                                  {:at end :ids (cond-> ids (and (<= from at) (<= end to)) (conj id))}))
                              {:at 0 :ids #{}}
                              (:glyphs draft)))
            indexes (keep-indexed (fn [i p] (when (and (ids (:id p)) (not (:ghost? p))) i))
                                  (:poses last-frame))]
        (when (and (seq indexes) (< (reduce max indexes) (render/drawn r)))
          (render/glyph-box r indexes))))))

(defn- run-step!
  "One step of the tour, through the path the visitor's own hand takes."
  [{:keys [r picker input motion-query]} {:keys [step elapsed-ms over?]}]
  (case (:do step)
    :clear (enter! input "" "deleteContentBackward")
    :type (let [{:keys [base typed]} (or (get-in @state [:tour :typing])
                                         {:base (.-value input) :typed 0})
                due (count (tour/keys-due step elapsed-ms))]
            ;; a late tick still types key by key
            (doseq [n (range (inc typed) (inc due))]
              (enter! input (str base (subs (:text step) 0 n)) "insertText"))
            (swap! state assoc-in [:tour :typing] (when-not over? {:base base :typed due})))
    :style (choose-style! (:style step) picker input)
    :reveal (dotimes [_ 3]
              (when (< (level @state (now-ms)) (:level step)) (wand!)))
    :read-back (do (set-pace! (:wpm step))
                   (read-back! motion-query))
    :light (let [[x0 y0 x1 y1] (phrase-box r (:over step))
                 along (min 1 (/ elapsed-ms (:ms step)))]
             (swap! state (fn [s]
                            (-> s
                                (assoc :light-px (when (and x0 (not over?))
                                                   [(+ x0 (* along (- x1 x0))) (/ (+ y0 y1) 2)]))
                                (assoc-in [:tour :lit?] (not over?))))))
    (:wait :until-read) nil))

(defn- stop-tour!
  "The tour is over, by its script or by the visitor's hand: what is on
  the page stays and is theirs, with the style and pace they had."
  [{:keys [picker input]}]
  (when-let [{:keys [timer on-touch before lit?]} (:tour @state)]
    (js/clearInterval timer)
    (.removeEventListener js/window "keydown" on-touch true)
    (.removeEventListener js/window "pointerdown" on-touch true)
    (swap! state #(cond-> (assoc % :tour nil)
                    lit? (assoc :light-px nil)))
    (choose-style! (:style before) picker input)
    (set-pace! (:pace before))
    (set! (.-hidden (by-id "tour-stop")) true)
    (show-caption-for! your-turn your-turn-ms)))

(defn- tour-tick!
  "Play whatever of the tour is due now. It ends on a tick with nothing
  left to play, so its last scene is on the page for at least one."
  [ctx]
  (when-let [{:keys [segments segment t0 done awaiting-read?]} (:tour @state)]
    (let [last? (= (inc segment) (count segments))
          steps (when-not awaiting-read? (tour/due (segments segment) (- (now-ms) t0) done))]
      (cond
        awaiting-read?
        (when-not (:reading @state)
          (if last?
            (stop-tour! ctx)
            (swap! state update :tour assoc
                   :segment (inc segment) :t0 (now-ms) :done #{} :awaiting-read? false)))

        (and (empty? steps) last? (= (count done) (count (segments segment))))
        (stop-tour! ctx)

        :else
        (doseq [{:keys [index scene step over?] :as entry} steps]
          (when (not= scene (get-in @state [:tour :scene]))
            (swap! state assoc-in [:tour :scene] scene)
            (show-caption! (tour/caption tour/script scene)))
          (run-step! ctx entry)
          (when over?
            (swap! state update :tour
                   #(cond-> (update % :done conj index)
                      (= :until-read (:do step)) (assoc :awaiting-read? true)))))))))

(defn- start-tour!
  "Play the tour from its first scene; nothing on the specimen or while it
  is already playing. With reduced motion there is no tour: the caption
  says so until the visitor's next key or click."
  [{:keys [^js motion-query] :as ctx}]
  (cond
    (or specimen? (:tour @state)) nil

    (.-matches motion-query)
    (do (show-caption! no-tour)
        (doseq [kind ["keydown" "pointerdown"]]
          (.addEventListener js/window kind
                             #(when (= no-tour (.-textContent (by-id "caption"))) (show-caption! ""))
                             #js {:once true :capture true})))

    :else
    (let [{:keys [style-choice pace]} @state
          ;; any key, click or tap is the visitor taking over; the event
          ;; goes on to do whatever it does
          on-touch (fn [_] (stop-tour! ctx))]
      (swap! state assoc :tour {:segments (tour/plan tour/script) :segment 0 :t0 (now-ms)
                                :done #{} :awaiting-read? false :scene nil :typing nil :lit? false
                                :before {:style style-choice :pace pace}
                                :timer (js/setInterval #(tour-tick! ctx) 16)
                                :on-touch on-touch})
      (.addEventListener js/window "keydown" on-touch true)
      (.addEventListener js/window "pointerdown" on-touch true)
      (set! (.-hidden (by-id "tour-stop")) false)
      (tour-tick! ctx))))

(defn- probe-frame
  "The frame the renderer tries itself on before the first real one: one
  letter and the caret, so every pipeline the lab draws with is made
  while the renderer is still watching for failures."
  []
  (pose/frame (draft/edit (draft/empty-draft) "a" 0 cues/word-cue) 0
              {:width-em (width-em) :aspect (aspect)}))

(defn- show-backend!
  "The status line: the backend in use, and, once WebGPU has reported an
  error, the way round it."
  [r]
  (set! (.-textContent (by-id "status"))
        (if (seq (render/gpu-errors r))
          "Rendering with WebGPU, with errors: add ?webgl to the address"
          (str "Rendering with " (render/backend r)))))

(defn init! []
  (let [^js input (by-id "type")
        ^js status (by-id "status")
        ^js picker (by-id "style")
        ^js sound-box (by-id "sound")
        ;; read on every frame, so a change of the setting applies at once
        motion-query (js/window.matchMedia "(prefers-reduced-motion: reduce)")]
    (when debug? (.removeAttribute (by-id "debug") "hidden"))
    (show-lights! {:lights (cond specimen? specimen-lights
                                 (>= (get-in @state [:reveal :start-level]) 2) 1
                                 :else 0)})
    (.addEventListener js/window "pointermove" on-pointer-move)
    ;; the mouse has left the page: the gold goes back to its own glow
    (.addEventListener js/document.documentElement "mouseleave"
                       #(swap! state assoc :light-px nil))
    (.focus input)
    ;; the specimen is not the writer's text: typing changes nothing there
    (when-not specimen? (.addEventListener input "input" on-input))
    (set! (.-value picker) (name (:style-choice @state)))
    (.addEventListener picker "change"
                       #(choose-style! (keyword (.-value picker)) picker input))
    (.addEventListener js/window "keydown" #(on-key picker input motion-query %))
    (.addEventListener (by-id "read") "click"
                       (fn [_] (read-back! motion-query) (.focus input)))
    (swap! state assoc :pace (remembered-pace))
    (show-pace! (:pace @state))
    (.addEventListener (by-id "pace") "input"
                       #(choose-pace! (js/parseInt (.. % -target -value) 10)))
    (swap! state assoc :sound? (remembered-sound?))
    (set! (.-checked sound-box) (:sound? @state))
    (.addEventListener sound-box "change" #(choose-sound! (.-checked sound-box) input))
    (.addEventListener js/window "keydown" on-gesture)
    (.addEventListener js/window "pointerdown" on-gesture)
    (-> (render/start! (by-id "stage") (probe-frame) (second chosen-stage)
                       {:webgl? webgl? :on-gpu-error show-backend!})
        (.then (fn [r]
                 (if specimen?
                   (specimen-loop! r)
                   (do (edit! (.-value input))
                       (loop! r motion-query)))
                 (expose! r)
                 (show-backend! r)
                 (let [ctx {:r r :picker picker :input input :motion-query motion-query}]
                   ;; a click the tour's own listeners never see: they are
                   ;; added after its pointerdown
                   (.addEventListener (by-id "tour") "click" #(start-tour! ctx))
                   ;; for a click with no pointer under it (a screen reader's)
                   (.addEventListener (by-id "tour-stop") "click" #(stop-tour! ctx))
                   (when tour? (start-tour! ctx)))))
        (.catch (fn [e]
                  (set! (.-textContent status)
                        (str "Couldn't start the 3D renderer: " (.-message ^js e)))
                  (js/console.error e))))))
