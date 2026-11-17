(ns halfahead.lab.gestures
  "The lab's vocabulary of motion, as data: every :ha/word-effect written
  out as a :ha/patch for halfahead.lab.synth to play (see
  docs/product/score.md). Amounts are at intensity 1. A patch belongs here
  only if (synth/problems patch) is empty, which the tests hold every one
  to: it ends at rest and never leaves its channels' bounds.")

(def ^:private tau (* 2.0 Math/PI))

(def ^:private ripple
  "The stagger of the lab's first eleven effects: 25 ms between
  neighbours, the last letter starting within 100 ms."
  {:ms-per-glyph 25 :max-ms 100})

(defn- jittered
  "An oscillator phased by letter identity, so neighbours shake apart."
  [wave period-ms salt]
  {:wave wave :period-ms period-ms :phase :id :salt salt})

(def ^:private droop-until
  "Where :melt stops drooping and starts to swing back: 600 of 1400 ms."
  (/ 3.0 7.0))

(def ^:private melt-swing
  "One and a half swings in the 800 ms that remain."
  {:wave :cosine :period-ms (/ 1600.0 3.0)})

(def patches
  "effect -> :ha/patch, for every :ha/word-effect."
  {;; --- the eleven the lab began with, transcribed from lab.pose's
   ;; hand-written deltas (gestures_test proves they move as they did)
   :pop     {:ms 400 :stagger ripple
             :mods [{:channel :scale :amount 0.35 :curve :arch}]}
   :launch  {:ms 1400 :stagger ripple
             :mods [{:channel :y :amount 1.2 :curve :arc :from 0.0 :to 0.5}
                    {:channel :z :amount 1.5 :curve :arc :from 0.0 :to 0.5}
                    {:channel :wobble :amount 1.0 :curve :fade2 :from 0.5 :to 1.0}]}
   :shimmer {:ms 1400 :stagger ripple
             :mods [{:channel :glow :amount 0.8 :curve :fade2
                     :osc {:wave :sine :period-ms 350 :abs? true}}
                    {:channel :y :amount 0.05 :curve :fade
                     :osc {:wave :sine :period-ms 700}}]}
   :skitter {:ms 900 :stagger ripple
             :mods [{:channel :x :amount 0.12 :curve :fade2 :osc (jittered :sine 90 1)}]}
   :quake   {:ms 1000 :stagger ripple
             :mods [{:channel :x :amount 0.15 :curve :fade :osc (jittered :sine 70 2)}
                    {:channel :y :amount 0.15 :curve :fade :osc (jittered :cosine 70 2)}
                    {:channel :glow :amount 1.0 :curve :fade :from 0.0 :to 0.6}]}
   :melt    {:ms 1400 :stagger ripple
             :mods [{:channel :y :amount -0.4 :curve :rise-sine :from 0.0 :to droop-until}
                    {:channel :rx :amount 0.6 :curve :rise-sine :from 0.0 :to droop-until}
                    {:channel :y :amount -0.4 :curve :fade2 :from droop-until :to 1.0
                     :osc melt-swing}
                    {:channel :rx :amount 0.6 :curve :fade2 :from droop-until :to 1.0
                     :osc melt-swing}]}
   :rewind  {:ms 700 :stagger (assoc ripple :order :reverse)
             :mods [{:channel :rz :amount (- tau) :curve :fall}]}
   ;; the letters come in from a staggered layer in front of the line,
   ;; alternately above and below it; nothing moves sideways
   :fuse    {:ms 900 :stagger ripple
             :mods [{:channel :z :amount 0.6 :curve :fall :each :even}
                    {:channel :z :amount 0.3 :curve :fall :each :odd}
                    {:channel :y :amount 0.2 :curve :fall :alternate? true}]}
   :jitter  {:ms 1400 :stagger ripple
             :mods [{:channel :x :amount 0.04 :curve :fade :osc (jittered :sine 60 3)}
                    {:channel :y :amount 0.04 :curve :fade :osc (jittered :sine 75 4)}]}
   :glow    {:ms 1000 :stagger ripple
             :mods [{:channel :glow :amount 0.9 :curve :arch}]}
   :wobble  {:ms 1400 :stagger ripple
             :mods [{:channel :wobble :amount 1.0 :curve :fade2}]}

   ;; --- the eight that joined with the score, as the foreman specified
   ;; a held syllable, measured from the Music app's lyrics: each letter
   ;; lifts as the highlight reaches it, rises for 0.9 s, settles for longer
   :swell   {:ms 2400 :stagger {:ms-per-glyph 120 :max-ms 900}
             :mods [{:channel :y :amount 0.25 :curve :rise-sine :from 0 :to 0.375}
                    {:channel :y :amount 0.25 :curve :fade2 :from 0.375 :to 1}
                    {:channel :glow :amount 0.45 :curve :arch}]}
   :bloom   {:ms 1600 :stagger {:ms-per-glyph 40 :max-ms 200}
             :mods [{:channel :weight :amount 0.6 :curve :arch}
                    {:channel :glow :amount 0.5 :curve :arch}
                    {:channel :scale :amount 0.06 :curve :arch}]}
   :slam    {:ms 900 :stagger {:ms-per-glyph 15 :max-ms 60}
             :mods [{:channel :scale :amount 1.4 :curve :spring-fall}
                    {:channel :z :amount 2.0 :curve :spring-fall}
                    {:channel :weight :amount 0.8 :curve :fade2}
                    {:channel :glow :amount 0.7 :curve :fade2}]}
   :hush    {:ms 1400 :stagger {:ms-per-glyph 20 :max-ms 100}
             :mods [{:channel :scale :amount -0.12 :curve :arch}
                    {:channel :weight :amount -0.5 :curve :arch}
                    {:channel :blur :amount 0.35 :curve :arch}
                    {:channel :opacity :amount -0.3 :curve :arch}]}
   :lean    {:ms 1300 :stagger {:ms-per-glyph 30 :max-ms 150}
             :mods [{:channel :skew :amount 0.28 :curve :rise :from 0 :to 0.3}
                    {:channel :skew :amount 0.28 :curve :fall :from 0.3 :to 1}]}
   :nod     {:ms 700 :stagger {:ms-per-glyph 20 :max-ms 100}
             :mods [{:channel :y :amount -0.12 :curve :arch}
                    {:channel :rx :amount 0.25 :curve :arch}]}
   :shiver  {:ms 1100
             :mods [{:channel :x :amount 0.035 :curve :fade
                     :osc {:wave :sine :period-ms 55 :phase :id :salt 6}}
                    {:channel :blur :amount 0.25 :curve :fade2}]}
   :draw    {:ms 500
             :mods [{:channel :fill :amount -1.0 :curve :fall}
                    {:channel :glow :amount 0.3 :curve :arch}]}})

(defn patch
  "The :ha/patch that performs a :ha/word-effect."
  [effect]
  (or (patches effect)
      (throw (ex-info (str "No gesture for " (pr-str effect))
                      {:type :ha/unknown-gesture :effect effect}))))
