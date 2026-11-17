(ns halfahead.lab.gestures-test
  (:require [clojure.test :refer [deftest is testing]]
            [halfahead.lab.cues :as cues]
            [halfahead.lab.gestures :as gestures]
            [halfahead.lab.glossary :as glossary]
            [halfahead.lab.synth :as synth]
            [halfahead.schema :as schema]
            [malli.core :as m]))

;; --- the reference -----------------------------------------------------------
;; lab.pose's hand-written effects as they stood before order 0045, moved
;; here unchanged (with the arithmetic they lean on), so hypothesis H1 of
;; docs/product/lab-notebook.md is tested against what the lab really did
;; and not against the synthesizer's own idea of it.

(def ^:private tau (* 2.0 Math/PI))

(defn- sin [x] (Math/sin (double x)))
(defn- cos [x] (Math/cos (double x)))

(defn- smoothstep [p] (* p p (- 3.0 (* 2.0 p))))

(defn- phase
  [id salt]
  (let [h (mod (+ (* (mod id 65521) 7919) (* salt 104729)) 65521)]
    (* tau (/ h 65521.0))))

(def ^:private effect-ms
  "How long each effect moves a glyph; none exceeds 1400 ms."
  {:pop 400 :launch 1400 :shimmer 1400 :skitter 900 :quake 1000 :melt 1400
   :rewind 700 :fuse 900 :jitter 1400 :glow 1000 :wobble 1400})

(defn- effect-delta
  "The delta for one effect at local time u (0 <= u < its duration), at
  intensity k, for the glyph at position pos in the cue."
  [effect u k id pos]
  (let [d (double (effect-ms effect))
        p (/ u d)
        fade (- 1.0 p)]
    (case effect
      :pop {:scale (* 0.35 k (sin (* Math/PI p)))}

      :launch (if (< u 700)
                (let [q (/ u 700.0)
                      arc (* 4.0 q (- 1.0 q))]
                  {:y (* 1.2 k arc) :z (* 1.5 k arc)})
                (let [e (- 1.0 (/ (- u 700.0) 700.0))]
                  {:wobble (* k e e)}))

      :shimmer {:glow (* 0.8 k fade fade (Math/abs (sin (/ (* tau u) 350.0))))
                :y (* 0.05 k fade (sin (/ (* tau u) 700.0)))}

      :skitter {:x (* 0.12 k fade fade (sin (+ (/ (* tau u) 90.0) (phase id 1))))}

      :quake (let [a (+ (/ (* tau u) 70.0) (phase id 2))]
               {:x (* 0.15 k fade (sin a))
                :y (* 0.15 k fade (cos a))
                :glow (* k (max 0.0 (- 1.0 (/ u 600.0))))})

      :melt (let [droop (if (< u 600)
                          (sin (* 0.5 Math/PI (/ u 600.0)))
                          (let [q (/ (- u 600.0) 800.0)]
                            (* (- 1.0 q) (- 1.0 q) (cos (* 3.0 Math/PI q)))))]
              {:y (* -0.4 k droop) :rx (* 0.6 k droop)})

      :rewind {:rz (* (- tau) k (- 1.0 (smoothstep p)))}

      ;; the letters come in from a staggered layer in front of the line,
      ;; alternately above and below it; nothing moves sideways
      :fuse (let [apart (* k (- 1.0 (smoothstep p)))]
              {:z (* 0.6 apart (if (even? pos) 1.0 0.5))
               :y (* 0.2 apart (if (even? pos) 1.0 -1.0))})

      :jitter {:x (* 0.04 k fade (sin (+ (/ (* tau u) 60.0) (phase id 3))))
               :y (* 0.04 k fade (sin (+ (/ (* tau u) 75.0) (phase id 4))))}

      :glow {:glow (* 0.9 k (sin (* Math/PI p)))}

      :wobble {:wobble (* k fade fade)})))

(defn- stagger-step
  "25 ms between neighbours, compressed so the last glyph of any cue starts
  within 100 ms: 100 + the longest effect (1400) is the 1500 ms settle."
  [n]
  (if (> n 5) (/ 100.0 (dec n)) 25.0))

;; --- H1 ----------------------------------------------------------------------

(def ^:private old-effects (keys effect-ms))

(def ^:private new-gestures
  [:swell :bloom :slam :hush :lean :nod :shiver :draw])

(defn- first-mismatch
  "The first point of the grid where the patch and the reference differ by
  1e-9 or more on any channel (a channel one of them leaves out is 0
  there), or nil."
  [effect]
  (let [patch (gestures/patch effect)]
    (first
     (for [u (range 0 (effect-ms effect) 7)
           i (range 6)
           id [0 3 11]
           k [0.3 1]
           :let [played (synth/delta patch u i 6 id k)
                 reference (effect-delta effect u k id i)]
           channel (distinct (concat (keys played) (keys reference)))
           :let [a (get played channel 0.0)
                 b (get reference channel 0.0)]
           :when (not (< (abs (- a b)) 1e-9))]
       {:u u :pos i :id id :k k :channel channel :patch a :reference b}))))

(deftest h1-old-effects-move-as-before
  (is (= 11 (count old-effects)))
  (doseq [effect old-effects]
    (testing (str effect)
      (is (= (effect-ms effect) (:ms (gestures/patch effect))))
      (is (nil? (first-mismatch effect))))))

(deftest h1-old-staggers-as-before
  (doseq [effect old-effects
          n (range 1 41)
          :let [patch (gestures/patch effect)
                step (stagger-step n)]]
    (is (every? (fn [i]
                  (let [rank (if (= :rewind effect) (- n 1 i) i)]
                    (< (abs (- (synth/start-ms patch i n) (* step rank))) 1e-9)))
                (range n))
        (str effect " over " n " letters"))))

;; --- the eight new gestures --------------------------------------------------

(deftest new-gestures-exactly-as-specified
  (is (= {:swell  {:ms 2400 :stagger {:ms-per-glyph 120 :max-ms 900}
                   :mods [{:channel :y :amount 0.25 :curve :rise-sine :from 0 :to 0.375}
                          {:channel :y :amount 0.25 :curve :fade2 :from 0.375 :to 1}
                          {:channel :glow :amount 0.45 :curve :arch}]}
          :bloom  {:ms 1600 :stagger {:ms-per-glyph 40 :max-ms 200}
                   :mods [{:channel :weight :amount 0.6 :curve :arch}
                          {:channel :glow :amount 0.5 :curve :arch}
                          {:channel :scale :amount 0.06 :curve :arch}]}
          :slam   {:ms 900 :stagger {:ms-per-glyph 15 :max-ms 60}
                   :mods [{:channel :scale :amount 1.4 :curve :spring-fall}
                          {:channel :z :amount 2.0 :curve :spring-fall}
                          {:channel :weight :amount 0.8 :curve :fade2}
                          {:channel :glow :amount 0.7 :curve :fade2}]}
          :hush   {:ms 1400 :stagger {:ms-per-glyph 20 :max-ms 100}
                   :mods [{:channel :scale :amount -0.12 :curve :arch}
                          {:channel :weight :amount -0.5 :curve :arch}
                          {:channel :blur :amount 0.35 :curve :arch}
                          {:channel :opacity :amount -0.3 :curve :arch}]}
          :lean   {:ms 1300 :stagger {:ms-per-glyph 30 :max-ms 150}
                   :mods [{:channel :skew :amount 0.28 :curve :rise :from 0 :to 0.3}
                          {:channel :skew :amount 0.28 :curve :fall :from 0.3 :to 1}]}
          :nod    {:ms 700 :stagger {:ms-per-glyph 20 :max-ms 100}
                   :mods [{:channel :y :amount -0.12 :curve :arch}
                          {:channel :rx :amount 0.25 :curve :arch}]}
          :shiver {:ms 1100
                   :mods [{:channel :x :amount 0.035 :curve :fade
                           :osc {:wave :sine :period-ms 55 :phase :id :salt 6}}
                          {:channel :blur :amount 0.25 :curve :fade2}]}
          :draw   {:ms 500
                   :mods [{:channel :fill :amount -1.0 :curve :fall}
                          {:channel :glow :amount 0.3 :curve :arch}]}}
         (select-keys gestures/patches new-gestures))))

;; --- the gate ----------------------------------------------------------------

(def ^:private slowest-tempo 1.5)

(deftest every-effect-has-a-safe-patch-within-the-cap
  (is (= (set (m/children (m/deref-all (schema/schema :ha/word-effect)))) (set (keys gestures/patches)))
      "one patch per :ha/word-effect, and no others")
  (is (= 19 (count gestures/patches)))
  (doseq [[effect patch] gestures/patches]
    (testing (str effect)
      (is (= patch (gestures/patch effect)))
      (is (= [] (synth/problems patch)))
      (is (<= (* slowest-tempo (synth/total-ms patch 40)) 5000)))))

(deftest an-unknown-effect-has-no-patch
  (is (= :ha/unknown-gesture
         (:type (ex-data (try (gestures/patch :sparkle)
                              (catch #?(:clj Exception :cljs :default) e e)))))))

;; --- the glossary ------------------------------------------------------------

(deftest new-gestures-perform-their-names
  (doseq [effect new-gestures
          :let [word (name effect)]]
    (testing word
      (is (= {:effect effect :intensity 0.9} (glossary/entries word)))
      (is (= {:word word :effect effect :intensity 0.9 :source :glossary}
             (cues/word-cue word " "))))))
