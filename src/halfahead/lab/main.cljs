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
  script ends or the visitor touches a key or the page; a draft the
  visitor had when it began is kept aside and put back when it ends. It
  also wires the board (lab.board): a toolbar of pads, hidden until asked
  for, and an XY pad whose point is the writer's own style, Mine. The
  shell turns pointer and key events into lab.board's hold events and adds
  what a completed press gives to the draft; it decides nothing itself."
  (:require [clojure.string :as str]
            [halfahead.lab.audio :as audio]
            [halfahead.lab.board :as board]
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
            [halfahead.lab.tour :as tour]
            [halfahead.schema :as schema]))

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
  ;; any: {:plan (lab.reading's) :style (the named style, or under Mine the
  ;; point, it was planned in and plays in) :t0 :cues (its placed cues)
  ;; :voiced-to (the time up to
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
  ;; :pace}) :kept (the visitor's text when it began, if any) :timer
  ;; :on-touch}; :caption-timer, the timeout that will take
  ;; the caption away;
  ;; :style-choice, the picker's: a :ha/style-choice, or :mine for the
  ;; board's point; :board, {:visible? :point (the XY pad's :ha/style-point)
  ;; :holds (lab.board/hold's: the pads held down)}
  (atom {:draft (draft/empty-draft) :last-frame nil :style-choice :auto
         :board {:visible? false :point (style/point :plain) :holds nil}
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

;; Likewise whether this viewer has the board out, and their own style
;; point on its XY pad.
(def ^:private board-key "halfahead.lab.board")
(def ^:private point-key "halfahead.lab.board.point")

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

(defn- own-choice
  "The picker's choice as lab.pose takes it: Mine is the board's point."
  [{:keys [style-choice board]}]
  (if (= :mine style-choice) (:point board) style-choice))

(defn- point-at
  "The :ha/style-point in force at t: the board's under Mine, else where
  the named style in force sits."
  [{:keys [style-choice board] :as s} t]
  (if (= :mine style-choice) (:point board) (style/point (style-at s t))))

(defn- choice-in-force
  "What the frame performs in: the style of the reading playing, if any,
  so its gestures keep the lengths they were granted; else the picker's
  choice, or under Mine the board's point."
  [{:keys [reading] :as s}]
  (if reading (:style reading) (own-choice s)))

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
        point (point-at s t)]
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
  (let [choice (choice-in-force s)
        played (sound/sounds {:sound? (:sound? s)
                              ;; sound has no voice for a point yet: Mine sounds plain
                              :style (if (map? choice)
                                       :plain
                                       (sound/style-in-force choice (:last-frame s)))}
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
         worn :dress :as s}
        (swap! state #(-> %
                          (update :stats debug/record t)
                          (update :cue-log debug/cue-log [] t)
                          (update :direction-log debug/cue-log [] t)))]
    (set! (.-textContent (by-id "debug"))
          (str (debug/overlay-text {:summary (debug/summary stats) :t-ms t
                                    :backend (render/backend r) :glyphs (render/drawn r)
                                    :style (:style f)
                                    :style-choice (if (map? (choice-in-force s))
                                                    (choice-in-force s)
                                                    style-choice)
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

(defn- text-box
  "The box in CSS pixels around the live letters as last drawn, as #js
  {:x0 :y0 :x1 :y1}; nil when there are none."
  [r]
  (let [indexes (keep-indexed (fn [i p] (when-not (:ghost? p) i))
                              (:poses (:last-frame @state)))]
    (when (and (seq indexes) (< (reduce max indexes) (render/drawn r)))
      (let [[x0 y0 x1 y1] (render/glyph-box r indexes)]
        #js {:x0 x0 :y0 y0 :x1 x1 :y1 y1}))))

(defn- expose! [r]
  (set! (.-__lab js/window)
        #js {:backend (render/backend r)
             ;; the board as plain data: whether it is out, the XY pad's
             ;; point, and the span a pad would act on now (nil for none)
             :board #(let [{:keys [board draft]} @state
                           ^js input (by-id "type")]
                       (clj->js {:visible (:visible? board)
                                 :point (:point board)
                                 :target (board/target (:text draft)
                                                       {:from (.-selectionStart input)
                                                        :to (.-selectionEnd input)})}))
             :textBox #(text-box r)
             :liveText #(apply str (map :char (remove :ghost? (:poses (:last-frame @state)))))
             :lastFrame #(clj->js (:last-frame @state))
             :directions #(clj->js (:directions (:draft @state)))
             :cues #(clj->js (:cues (:draft @state)))
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
               (let [mine? (= :mine (:style-choice s))
                     in-force (if mine? (get-in s [:board :point]) (style-at s t))
                     ;; the tempo lab.pose plays a named style at is its
                     ;; params', not its point's; a point has only its own
                     plan (reading/plan (:text draft) (point-at s t)
                                        (if mine?
                                          (style/from-point in-force)
                                          (style/params in-force))
                                        {:wpm pace})]
                 (assoc s :reading (when plan
                                     {:plan plan
                                      :style in-force
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

(defn- wanded
  "state after the writer's wand at t: one level more, from then."
  [s t]
  (cond-> s
    (< (level s t) 3) (-> (update-in [:reveal :wands] conj t)
                          (directed t))))

(defn- wand!
  "The writer's wand: one level more, from now."
  []
  (let [t (now-ms)]
    (swap! state wanded t)
    (play-queued!)))

(defn- raised
  "state with the reveal level at least n at t, by as many wands as that
  takes, so the level is kept like any the wand reached."
  [s t n]
  (if (< (level s t) n)
    (recur (wanded s t) t n)
    s))

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

;; --- the board ----------------------------------------------------------------

(defn- remembered-board?
  "Whether this viewer had the board out here before; hidden when storage
  is empty, blocked or throws."
  []
  (try (= "shown" (.getItem js/localStorage board-key))
       (catch :default _ false)))

(defn- remembered-point
  "The style point this viewer left the XY pad at, or nil when storage is
  empty, blocked, throws or holds anything that is not a :ha/style-point."
  []
  (try (let [point (-> (.getItem js/localStorage point-key)
                       js/JSON.parse
                       (js->clj :keywordize-keys true))]
         (when (schema/valid? :ha/style-point point) point))
       (catch :default _ nil)))

(defn- remember! [k v]
  (try (.setItem js/localStorage k v)
       (catch :default _ nil)))

(defn- show-board!
  "The board is out or away, and the toggle and the page's layout say so."
  []
  (let [visible? (get-in @state [:board :visible?])]
    (set! (.-hidden (by-id "board")) (not visible?))
    (.setAttribute (by-id "board-toggle") "aria-pressed" (str visible?))
    (set! (.. js/document.documentElement -dataset -board) (if visible? "shown" "hidden"))))

(defn- toggle-board!
  "Show or hide the board, remembered on this device. Whatever was held
  on it is let go."
  []
  (let [visible? (not (get-in @state [:board :visible?]))]
    (swap! state update :board assoc :visible? visible? :holds nil)
    (remember! board-key (if visible? "shown" "hidden"))
    (show-board!)))

(defn- percent [v] (js/Math.round (* 100 v)))

(defn- show-point!
  "The XY pad's dot and its value text follow the board's point."
  []
  (let [[x y] (board/point->xy (get-in @state [:board :point]))
        ^js pad (by-id "xy")
        ^js dot (by-id "xy-dot")]
    (set! (.. dot -style -left) (str (* 100 x) "%"))
    (set! (.. dot -style -bottom) (str (* 100 y) "%"))
    (.setAttribute pad "aria-valuenow" (str (percent x)))
    (.setAttribute pad "aria-valuetext"
                   (str "Energy " (percent x) "%, flourish " (percent y) "%"))))

(defn- place-point!
  "The board's point is point from now on, shown and remembered on this
  device."
  [point]
  (swap! state assoc-in [:board :point] point)
  (remember! point-key (js/JSON.stringify (clj->js point)))
  (show-point!))

(defn- move-point!
  "The writer moved the XY pad to x, y: that point is their style, Mine,
  from now on."
  [x y ^js picker]
  (place-point! (board/xy->point (get-in @state [:board :point]) x y))
  (swap! state #(-> % (assoc :style-choice :mine) (redressed (now-ms))))
  (set! (.-value picker) "mine"))

(defn- press-pad!
  "A completed press of pad, held for held-ms: what lab.board makes of it
  on the board's target, added to the draft as typing adds its own cues
  and directions, and voiced, with the reveal level raised by the wand's
  path to where the press can be seen from the moment it is placed: at
  least 1 for a mark, 3 for BOOM, whose card needs the stage. Nothing
  when there is nothing to mark, or on the specimen."
  [^js input pad held-ms]
  (when-not specimen?
    (let [t (now-ms)
          selection {:from (.-selectionStart input) :to (.-selectionEnd input)}]
      (swap! state
             (fn [s]
               (if-let [span (board/target (get-in s [:draft :text]) selection)]
                 (let [old (:draft s)
                       {:keys [cues directions]} (board/press pad (point-at s t) (:glyphs old)
                                                              span t (board/amount held-ms))
                       new (-> old
                               (update :cues into cues)
                               (update :directions (fnil into []) directions))
                       log? (or debug? (:sound? s))
                       new-cues (when log? (debug/new-cues old new))
                       new-shots (when log? (debug/new-directions old new))]
                   ;; The press is in the draft before the level is looked
                   ;; at: a cue is use, and may reach level 1 itself at t.
                   ;; A wand as well would then raise it to 2.
                   (cond-> (-> (assoc s :draft new)
                               (revealed t)
                               (raised t (if (= :boom pad) 3 1)))
                     debug? (update :cue-log debug/cue-log new-cues t)
                     debug? (update :direction-log debug/cue-log new-shots t)
                     true (voiced :cue new-cues)
                     true (voiced :shot new-shots)
                     true (directed t)))
                 s)))
      (play-queued!))))

(defn- hold!
  "Hand one of lab.board's hold events on, and play the press it
  completes, if any."
  [input event]
  (let [[old _] (swap-vals! state update-in [:board :holds] #(:holds (board/hold % event)))]
    (when-let [{:keys [pad ms]} (:play (board/hold (get-in old [:board :holds]) event))]
      (press-pad! input pad ms))))

(defn- press-key? [^js e]
  (and (contains? #{"Enter" " "} (.-key e))
       (not (or (.-ctrlKey e) (.-altKey e) (.-metaKey e)))))

(defn- make-pad!
  "A pad's button, wired: a press plays when it is released on the pad,
  by pointer or by Enter or Space, and never if it is abandoned. A hold
  is timed by the events' own timestamps: when the finger moved, not when
  a busy frame let the handler run."
  [^js input {:keys [id label key]}]
  (let [^js el (js/document.createElement "button")
        ^js hint (js/document.createElement "kbd")]
    (set! (.-type el) "button")
    (set! (.-className el) "pad")
    (.setAttribute el "data-pad" (name id))
    (.setAttribute el "aria-keyshortcuts" (str "Alt+" key))
    (set! (.-title el) (str "Alt+" key))
    (.append el label)
    (set! (.-textContent hint) key)
    (.append el hint)
    ;; a pointer on a pad never takes the focus from the text box
    (.addEventListener el "mousedown" #(.preventDefault %))
    (.addEventListener el "pointerdown"
                       (fn [^js e]
                         (when (zero? (.-button e))
                           ;; a finger captures its target: let go, so sliding
                           ;; off the pad is seen as leaving it
                           (when (.hasPointerCapture el (.-pointerId e))
                             (.releasePointerCapture el (.-pointerId e)))
                           (hold! input [:pointer-down id (.-timeStamp e)]))))
    (.addEventListener el "pointerup" #(hold! input [:pointer-up id (.-timeStamp %)]))
    (.addEventListener el "pointerleave" #(hold! input [:pointer-gone]))
    (.addEventListener el "pointercancel" #(hold! input [:pointer-gone]))
    ;; Enter and Space are held like a pointer: down here, played on release,
    ;; and the browser's own click of them is not a second press
    (.addEventListener el "keydown"
                       (fn [^js e]
                         (when (press-key? e)
                           (.preventDefault e)
                           (hold! input [:key-down id (.-timeStamp e)]))))
    (.addEventListener el "keyup"
                       (fn [^js e]
                         (when (press-key? e)
                           (.preventDefault e)
                           (hold! input [:key-up id (.-timeStamp e)]))))
    (.addEventListener el "blur" #(hold! input [:key-gone id]))
    ;; a click with no pointer under it (a screen reader's) is a tap
    (.addEventListener el "click"
                       (fn [^js e]
                         (when (zero? (.-detail e))
                           (hold! input [:click id (.-timeStamp e)]))))
    el))

(def ^:private pad-codes
  "KeyboardEvent.code -> pad: Alt+1 to Alt+7, wherever the layout puts the
  digits' characters."
  (into {} (map (fn [{:keys [id key]}] [(str "Digit" key) id])) board/pads))

(defn- on-pad-key-down
  "Alt+1 to Alt+7 put a pad down from anywhere, the text box included,
  which keeps its focus and gains no character."
  [input ^js e]
  (when-let [pad (and (.-altKey e) (not (.-ctrlKey e)) (not (.-metaKey e))
                      (get-in @state [:board :visible?])
                      (pad-codes (.-code e)))]
    (.preventDefault e)
    (hold! input [:key-down pad (.-timeStamp e)])))

(defn- on-pad-key-up
  "A digit released plays its pad only if Alt+digit put it down: a plain
  1 is just a 1."
  [input ^js e]
  (when-let [pad (pad-codes (.-code e))]
    (hold! input [:key-up pad (.-timeStamp e)])))

(def ^:private xy-step 0.05)

(defn- stepped
  "v moved by n steps of the XY pad, without the float's dust."
  [v n]
  (/ (js/Math.round (* 100 (+ v (* n xy-step)))) 100))

(defn- wire-xy!
  "The XY pad: a click or a drag puts the dot under the pointer, the arrow
  keys move it a step when it has the focus."
  [^js picker]
  (let [^js pad (by-id "xy")
        to-pointer (fn [^js e]
                     (let [box (.getBoundingClientRect pad)]
                       (move-point! (/ (- (.-clientX e) (.-left box)) (max 1 (.-width box)))
                                    (- 1 (/ (- (.-clientY e) (.-top box)) (max 1 (.-height box))))
                                    picker)))]
    (.addEventListener pad "mousedown" #(.preventDefault %))
    (.addEventListener pad "pointerdown"
                       (fn [^js e]
                         (when (zero? (.-button e))
                           (.setPointerCapture pad (.-pointerId e))
                           (to-pointer e))))
    (.addEventListener pad "pointermove"
                       (fn [^js e]
                         (when (.hasPointerCapture pad (.-pointerId e))
                           (to-pointer e))))
    (.addEventListener pad "keydown"
                       (fn [^js e]
                         (when-let [[dx dy] ({"ArrowRight" [1 0] "ArrowLeft" [-1 0]
                                              "ArrowUp" [0 1] "ArrowDown" [0 -1]} (.-key e))]
                           (.preventDefault e)
                           (let [[x y] (board/point->xy (get-in @state [:board :point]))]
                             (move-point! (stepped x dx) (stepped y dy) picker)))))))

(defn- wire-board!
  "Make the pads, wire them, the XY pad and the toggle, and bring back
  what this viewer left: whether the board was out, and their point."
  [^js input ^js picker]
  (let [^js el (by-id "board")
        ^js xy (by-id "xy")]
    (doseq [pad board/pads]
      (.insertBefore el (make-pad! input pad) xy))
    (wire-xy! picker)
    (.addEventListener (by-id "board-toggle") "click"
                       (fn [_] (toggle-board!) (.focus input)))
    (.addEventListener js/window "keydown" #(on-pad-key-down input %))
    (.addEventListener js/window "keyup" #(on-pad-key-up input %))
    ;; a press is abandoned when the pointer comes up anywhere else (the
    ;; pad's own pointerup has run by now) or the window loses the focus
    (.addEventListener js/window "pointerup" #(hold! input [:pointer-gone]))
    (.addEventListener js/window "blur" #(hold! input [:blur]))
    (swap! state update :board assoc
           :visible? (remembered-board?)
           :point (or (remembered-point) (get-in @state [:board :point])))
    (show-board!)
    (show-point!)))

(defn- pick-style!
  "The visitor's own pick of a style, from the picker or Ctrl+1..6: as
  choose-style!, and a named style moves the XY pad's dot to where that
  style sits, so the pad starts from it."
  [choice picker input]
  (when (some #{choice} style/styles)
    (place-point! (style/point choice)))
  (choose-style! choice picker input))

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
          (pick-style! choice picker input))
      (case (.-key e)
        ("b" "B") (do (.preventDefault e) (toggle-board!))
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
  the page stays and is theirs, with the style and pace they had. If they
  had a draft when it began, the page is theirs as it was: that text goes
  back into the box, by the path typing takes, with the caret at its end."
  [{:keys [picker ^js input]}]
  (when-let [{:keys [timer on-touch before lit? kept]} (:tour @state)]
    (js/clearInterval timer)
    (.removeEventListener js/window "keydown" on-touch true)
    (.removeEventListener js/window "pointerdown" on-touch true)
    (swap! state #(cond-> (assoc % :tour nil)
                    lit? (assoc :light-px nil)))
    (choose-style! (:style before) picker input)
    (set-pace! (:pace before))
    (when kept
      (enter! input kept "insertText")
      (.setSelectionRange input (count kept) (count kept)))
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
  says so until the visitor's next key or click. Words already in the text
  box are the visitor's draft: they are kept aside and the tour begins on
  an empty box (stop-tour! puts them back)."
  [{:keys [^js motion-query ^js input] :as ctx}]
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
          kept (not-empty (.-value input))
          ;; any key, click or tap is the visitor taking over; the event
          ;; goes on to do whatever it does
          on-touch (fn [_] (stop-tour! ctx))]
      (when kept (enter! input "" "deleteContentBackward"))
      (swap! state assoc :tour {:segments (tour/plan tour/script) :segment 0 :t0 (now-ms)
                                :done #{} :awaiting-read? false :scene nil :typing nil :lit? false
                                :before {:style style-choice :pace pace}
                                :kept kept
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
                       #(pick-style! (keyword (.-value picker)) picker input))
    (when-not specimen? (wire-board! input picker))
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
