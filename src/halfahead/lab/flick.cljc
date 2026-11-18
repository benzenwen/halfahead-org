(ns halfahead.lab.flick
  "One exam of the style learner (docs/product/score.md, 'How people and
  agents drive it'), as data: the current line read back twice, as A (the
  style now) and B (one step along one axis), then the choice between
  them. A pair that would look the same is never offered, because the H5
  simulation (docs/product/lab-notebook.md) says flicks only teach when
  the two are easy to tell apart. Nothing here starts an exam: it is
  asked for. Pure, the same on both hosts."
  (:refer-clojure :exclude [distinct?])
  (:require [halfahead.lab.learn :as learn]
            [halfahead.lab.reading :as reading]
            [halfahead.lab.style :as style]))

;; How far apart two motions must be before a person can be asked which
;; they prefer (H5: a choice between look-alikes is noise, and noise is
;; what slows the learner). Tempo and amplitude multiply, so they are
;; compared as a share of the larger; overshoot is compared as it is.
(def ^:private told-apart-share 0.1)
(def ^:private told-apart-overshoot 0.2)

(def ^:private settle-ms
  "Held after each reading, so its last gesture is seen at rest."
  300)

(def ^:private gap-ms 400)

(def ^:private axes-tried
  "One turn of the learner's round of axes."
  4)

(defn- performance
  "[[span effect intensity] ...]: what the plan performs and where. By
  span, not by index: each plan keeps only the marks its own style
  performs, so one mark can sit at a different index in each."
  [{:keys [score performing]}]
  (mapv (fn [{:keys [mark effect intensity]}]
          [(:span ((:marks score) mark)) effect intensity])
        performing))

(defn- share-apart? [a b]
  (>= (abs (- a b)) (* told-apart-share (max a b))))

(defn- motion-apart? [p q]
  (or (share-apart? (:tempo p) (:tempo q))
      (share-apart? (:amplitude p) (:amplitude q))
      (>= (abs (- (:overshoot p) (:overshoot q))) told-apart-overshoot)))

(defn distinct?
  "Whether two reading plans (lab.reading/plan) would look or sound
  different: the marks performing or their gestures differ, or the dress
  differs, or the motion parameters differ by at least 10% (of the
  larger) in tempo or amplitude, or 0.2 in overshoot."
  [plan-a plan-b]
  (or (not= (performance plan-a) (performance plan-b))
      (not= (:dress (:score plan-a)) (:dress (:score plan-b)))
      (motion-apart? (:params plan-a) (:params plan-b))))

(defn- plan-at
  "The reading plan of text in the style at point. A learned point has no
  named style, so it is played with the parameters of the point itself."
  [text point pace]
  (reading/plan text point (style/from-point point) pace))

(defn offer
  "{:state s :axis k :a plan :b plan :point-a p :point-b p}: the exam to
  give for text read at pace, or nil when the text is not a message or no
  axis gives a pair that can be told apart. s is learn-state moved on,
  with no flick counted, past each axis whose pair would look the same;
  a and b are the reading plans of (learn/pair s)'s two points."
  [learn-state text pace]
  (when-let [plan-a (plan-at text (:point learn-state) pace)]
    (some (fn [s]
            (let [{:keys [axis a b]} (learn/pair s)
                  plan-b (plan-at text b pace)]
              (when (distinct? plan-a plan-b)
                {:state s :axis axis :a plan-a :b plan-b :point-a a :point-b b})))
          (take axes-tried (iterate learn/skip learn-state)))))

(defn phases
  "The exam's timeline, in ms from its start: [{:phase :a :at 0 :ms da}
  {:phase :gap :at .. :ms 400} {:phase :b :at .. :ms db} {:phase :choose
  :at ..}]. da and db are each reading's length (stops and cues) plus
  300 ms. :choose has no length: it waits for the writer."
  [{:keys [a b]}]
  (let [length #(+ (:duration-ms (:reading %)) settle-ms)
        da (length a)
        db (length b)]
    [{:phase :a :at 0 :ms da}
     {:phase :gap :at da :ms gap-ms}
     {:phase :b :at (+ da gap-ms) :ms db}
     {:phase :choose :at (+ da gap-ms db)}]))

(defn phase-at
  "The phase (:a :gap :b :choose) in force at t ms from the exam's start;
  the first before it."
  [phases t]
  (:phase (or (last (take-while #(<= (:at %) t) phases))
              (first phases))))

(defn decide
  "The learn state after the writer chooses :a or :b of the offer."
  [offer choice]
  (learn/choose (:state offer) choice))
