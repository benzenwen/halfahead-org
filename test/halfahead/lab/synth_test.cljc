(ns halfahead.lab.synth-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [halfahead.lab.synth :as synth]
            [halfahead.schema :as schema]))

(defn- near?
  ([a b] (near? a b 1e-9))
  ([a b tol] (<= (Math/abs (- a b)) tol)))

(def ^:private curves
  [:one :rise :fall :fade :fade2 :arch :arc :rise-sine :spring :spring-fall])

(defn- patch
  "A patch of one or more modulations."
  [ms & mods]
  {:ms ms :mods (vec mods)})

(defn- play
  "The delta of channel c for position pos and glyph id, at intensity 1."
  ([p c u] (play p c u 0 0))
  ([p c u pos id] (get (synth/delta p u pos 8 id 1) c)))

;; --- curves -----------------------------------------------------------------

(deftest curve-values
  (testing "at 0, 0.5 and 1"
    (doseq [[k [at-0 at-half at-1]]
            {:one [1 1 1]
             :rise [0 0.5 1]
             :fall [1 0.5 0]
             :fade [1 0.5 0]
             :fade2 [1 0.25 0]
             :arch [0 1 0]
             :arc [0 1 0]
             :rise-sine [0 (Math/sqrt 0.5) 1]
             ;; the spring is past its target at half time, on its overshoot
             :spring [0 1.11624 1]
             :spring-fall [1 -0.11624 0]}]
      (is (near? at-0 (synth/curve k 0) 1e-12) k)
      (is (near? at-half (synth/curve k 0.5) 1e-5) k)
      (is (near? at-1 (synth/curve k 1) 1e-12) k)))
  (testing "the springs land exactly"
    (is (== 1 (synth/curve :spring 1)))
    (is (== 0 (synth/curve :spring-fall 1))))
  (testing "p is clamped to [0 1]"
    (doseq [k curves]
      (is (== (synth/curve k 0) (synth/curve k -3)) k)
      (is (== (synth/curve k 1) (synth/curve k 7.5)) k)))
  (testing "range on a grid: within [0 1], but for the spring's overshoot"
    (doseq [k curves
            :let [vs (map #(synth/curve k (/ % 1000.0)) (range 1001))
                  [lo hi] (case k
                            :spring [0 1.2]
                            :spring-fall [-0.2 1]
                            [0 1])]]
      (is (<= (- lo 1e-12) (apply min vs)) k)
      (is (>= (+ hi 1e-12) (apply max vs)) k)))
  (testing "the spring overshoots, by about 15%"
    (is (near? 1.15 (apply max (map #(synth/curve :spring (/ % 1000.0)) (range 1001))) 1e-3)))
  (testing "the springs are continuous up to 1: nothing snaps"
    (doseq [k [:spring :spring-fall]]
      (is (near? (synth/curve k 1) (synth/curve k (- 1 1e-9)) 1e-6) k)
      (is (near? (synth/curve k 0.8) (synth/curve k (+ 0.8 1e-9)) 1e-6) k))))

(deftest phase-pins
  ;; h = ((id mod 65521) * 7919 + salt * 104729) mod 65521, by hand
  (is (== 0 (synth/phase 0 0)))
  (is (near? (* 2 Math/PI (/ 7919 65521.0)) (synth/phase 1 0) 1e-12))
  (is (near? (* 2 Math/PI (/ 39208 65521.0)) (synth/phase 65521 1) 1e-12))
  (is (near? (* 2 Math/PI (/ 42015 65521.0)) (synth/phase 7 3) 1e-12)))

;; --- stagger ----------------------------------------------------------------

(defn- starts [stagger n]
  (mapv #(synth/start-ms {:ms 400 :stagger stagger :mods []} % n) (range n)))

(deftest start-ms-orders
  (testing "forward is the default"
    (is (= [0 20 40 60 80] (starts {:ms-per-glyph 20} 5)))
    (is (= [0 20 40 60 80] (starts {:ms-per-glyph 20 :order :forward} 5))))
  (testing "reverse"
    (is (= [80 60 40 20 0] (starts {:ms-per-glyph 20 :order :reverse} 5))))
  (testing "centre-out: the middle letter or pair starts first"
    (is (= [40 20 0 20 40] (starts {:ms-per-glyph 20 :order :centre-out} 5)))
    (is (= [40 20 0 0 20 40] (starts {:ms-per-glyph 20 :order :centre-out} 6))))
  (testing "compressed when the last letter would start after :max-ms"
    (is (every? true? (map == [0 25 50 75 100]
                           (starts {:ms-per-glyph 120 :max-ms 100} 5))))
    (is (every? true? (map == [100 75 50 25 0]
                           (starts {:ms-per-glyph 120 :max-ms 100 :order :reverse} 5))))
    (testing "and :max-ms defaults to 900"
      (is (== 900 (synth/start-ms {:ms 400 :stagger {:ms-per-glyph 500} :mods []} 10 11)))
      (is (== 450 (synth/start-ms {:ms 400 :stagger {:ms-per-glyph 500} :mods []} 5 11)))))
  (testing "centre-out is compressed by its largest rank, so the last
            letters to start do so at exactly :max-ms"
    (is (every? true? (map == [100 50 0 50 100]
                           (starts {:ms-per-glyph 120 :max-ms 100 :order :centre-out} 5))))
    (is (every? true? (map == [100 50 0 0 50 100]
                           (starts {:ms-per-glyph 120 :max-ms 100 :order :centre-out} 6)))))
  (testing "centre-out is not compressed while its last start is within
            :max-ms, although a forward stagger of the same step would be"
    (is (= [60 30 0 30 60] (starts {:ms-per-glyph 30 :max-ms 100 :order :centre-out} 5)))
    (is (every? true? (map == [0 25 50 75 100]
                           (starts {:ms-per-glyph 30 :max-ms 100} 5)))))
  (testing "n of 0, 1 and 2"
    (doseq [order [:forward :reverse :centre-out]
            :let [p {:ms 400 :stagger {:ms-per-glyph 20 :order order} :mods []}]]
      (is (== 0 (synth/start-ms p 0 0)) order)
      (is (== 0 (synth/start-ms p 0 1)) order)
      (is (== 400 (synth/total-ms p 0)) order)
      (is (== 400 (synth/total-ms p 1)) order))
    (is (= [0 20] (starts {:ms-per-glyph 20} 2)))
    (is (= [20 0] (starts {:ms-per-glyph 20 :order :reverse} 2)))
    (is (= [0 0] (starts {:ms-per-glyph 20 :order :centre-out} 2))))
  (testing "no :stagger: every letter starts at once"
    (is (== 0 (synth/start-ms {:ms 400 :mods []} 3 5)))
    (is (== 400 (synth/total-ms {:ms 400 :mods []} 5))))
  (testing "total-ms is :ms plus the latest start"
    (is (== 480 (synth/total-ms {:ms 400 :stagger {:ms-per-glyph 20} :mods []} 5)))
    (is (== 500 (synth/total-ms {:ms 400 :stagger {:ms-per-glyph 120 :max-ms 100} :mods []} 5)))
    (is (== 440 (synth/total-ms {:ms 400 :stagger {:ms-per-glyph 20 :order :centre-out} :mods []} 5)))
    (is (== 400 (synth/total-ms {:ms 400 :stagger {:ms-per-glyph 20 :order :centre-out} :mods []} 2)))))

;; --- delta ------------------------------------------------------------------

(deftest delta-features
  (testing "nothing before the letter starts or from :ms on"
    (let [p (patch 400 {:channel :y :amount 1 :curve :one})]
      (is (= {} (synth/delta p -1 0 1 0 1)))
      (is (= {} (synth/delta p 400 0 1 0 1)))
      (is (= {} (synth/delta p 9000 0 1 0 1)))
      (is (== 1 (play p :y 0)))
      (is (== 1 (play p :y 399.999)))))
  (testing "a curve runs over the patch, scaled by amount and intensity"
    (let [p (patch 400 {:channel :y :amount 0.5 :curve :fade})]
      (is (near? 0.5 (play p :y 0)))
      (is (near? 0.375 (play p :y 100)))
      (is (near? 0.1875 (:y (synth/delta p 100 0 1 0 0.5))))
      (is (near? 0 (:y (synth/delta p 100 0 1 0 0))))))
  (testing "a window is [from to), and the curve runs over the window"
    (let [p (patch 1000 {:channel :y :amount 2 :curve :fade :from 0.25 :to 0.75})]
      (is (= {} (synth/delta p 249 0 1 0 1)))
      (is (near? 2 (play p :y 250)))
      (is (near? 1 (play p :y 500)))
      (is (near? 0.004 (play p :y 749)))
      (is (= {} (synth/delta p 750 0 1 0 1)))))
  (testing "mods on one channel add; other channels are separate"
    (let [p (patch 400
                   {:channel :y :amount 0.5 :curve :one}
                   {:channel :y :amount 0.25 :curve :one}
                   {:channel :glow :amount 0.1 :curve :one})]
      (is (near? 0.75 (play p :y 10)))
      (is (near? 0.1 (play p :glow 10)))
      (is (= #{:y :glow} (set (keys (synth/delta p 10 0 1 0 1)))))))
  (testing ":each limits a mod to even or odd positions"
    (let [even (patch 400 {:channel :y :amount 1 :curve :one :each :even})
          odd (patch 400 {:channel :y :amount 1 :curve :one :each :odd})
          all (patch 400 {:channel :y :amount 1 :curve :one :each :all})]
      (is (= [1.0 nil 1.0 nil] (mapv #(play even :y 10 % 0) (range 4))))
      (is (= [nil 1.0 nil 1.0] (mapv #(play odd :y 10 % 0) (range 4))))
      (is (= [1.0 1.0 1.0 1.0] (mapv #(play all :y 10 % 0) (range 4))))))
  (testing ":alternate? flips the sign on odd positions"
    (let [p (patch 400 {:channel :y :amount 1 :curve :one :alternate? true})]
      (is (= [1.0 -1.0 1.0 -1.0] (mapv #(play p :y 10 % 0) (range 4))))))
  (testing "each wave, a quarter period apart"
    (let [wave (fn [w u] (play (patch 1000 {:channel :y :amount 1 :curve :one
                                            :osc {:wave w :period-ms 400}})
                               :y u))]
      (is (every? true? (map near? [0 1 0 -1 0] (map #(wave :sine %) [0 100 200 300 400]))))
      (is (every? true? (map near? [1 0 -1 0 1] (map #(wave :cosine %) [0 100 200 300 400]))))
      (testing "the triangle has the cosine's period and phase, in straight lines"
        (is (every? true? (map near? [1 0.5 0 -1 -0.5 0 1]
                               (map #(wave :triangle %) [0 50 100 200 250 300 400])))))))
  (testing "a window's oscillator time starts at the window"
    (let [p (patch 1000 {:channel :y :amount 1 :curve :one :from 0.5
                         :osc {:wave :cosine :period-ms 400}})]
      ;; patch time would give cos(2.5 pi) = 0 at 500 and 1 at 800
      (is (near? 1 (play p :y 500)))
      (is (near? 0 (play p :y 600)))
      (is (near? -1 (play p :y 700)))))
  (testing "the oscillator multiplies the curve"
    (let [p (patch 1000 {:channel :y :amount 2 :curve :fade
                         :osc {:wave :sine :period-ms 400}})]
      (is (near? (* 2 0.9 1) (play p :y 100)))
      (is (near? (* 2 0.7 -1) (play p :y 300)))))
  (testing "phase rules"
    (let [osc (fn [rule & more]
                (patch 1000 {:channel :y :amount 1 :curve :one
                             :osc (merge {:wave :sine :period-ms 400 :phase rule}
                                         (apply hash-map more))}))]
      (testing ":same (the default): every letter alike"
        (is (every? #(near? 1 %) (for [pos (range 5) id [0 7 99]]
                                   (play (osc :same) :y 100 pos id))))
        (is (near? 1 (play (patch 1000 {:channel :y :amount 1 :curve :one
                                        :osc {:wave :sine :period-ms 400}})
                           :y 100 3 99))))
      (testing ":index: a quarter turn per position"
        (is (every? true? (map near? [0 1 0 -1 0 1]
                               (map #(play (osc :index) :y 0 % 0) (range 6)))))
        (is (near? 1 (play (osc :index) :y 0 1 12345))))
      (testing ":id: the glyph's fixed angle, salt 0 by default"
        (is (near? (Math/sin (synth/phase 7 0)) (play (osc :id) :y 0 0 7)))
        (is (near? (Math/sin (synth/phase 7 0)) (play (osc :id) :y 0 4 7)))
        (is (near? (Math/sin (synth/phase 7 3)) (play (osc :id :salt 3) :y 0 0 7)))
        (is (near? (Math/sin (+ (/ Math/PI 2) (synth/phase 9 3)))
                   (play (osc :id :salt 3) :y 100 0 9))))))
  (testing ":abs? rectifies the oscillator"
    (let [p (patch 1000 {:channel :y :amount 1 :curve :one
                         :osc {:wave :sine :period-ms 400 :abs? true}})]
      (is (near? 1 (play p :y 100)))
      (is (near? 1 (play p :y 300)))))
  (testing "noise"
    (let [p (patch 2400 {:channel :y :amount 1 :curve :one
                         :osc {:wave :noise :period-ms 100}})
          salted (assoc-in p [:mods 0 :osc :salt] 3)
          us (range 0 2400 7)]
      (testing "stays within [-1 1] and is not a constant"
        (let [vs (for [u us id [0 1 42 65521 1000003]] (play p :y u 0 id))]
          (is (every? #(<= -1 % 1) vs))
          (is (< (apply min vs) -0.5))
          (is (> (apply max vs) 0.5))))
      (testing "is the same for the same step, id and salt, wherever the letter sits"
        (is (== (play p :y 333 0 42) (play p :y 333 0 42)))
        (is (== (play p :y 333 0 42) (play p :y 333 5 42))))
      (testing "differs between ids and between salts"
        (is (not= (mapv #(play p :y % 0 1) us) (mapv #(play p :y % 0 2) us)))
        (is (not= (mapv #(play p :y % 0 1) us) (mapv #(play salted :y % 0 1) us))))
      (testing "each step is eased into the next: no jump at a step"
        (doseq [u [100 200 700 1500]]
          (is (near? (play p :y u 0 42) (play p :y (- u 1e-6) 0 42) 1e-6) u)))
      (testing "is the same on both hosts (pinned from Babashka)"
        (is (near? -0.2794566544 (play salted :y 50 0 42) 1e-9))
        (is (near? 0.3693364062 (play salted :y 777 0 42) 1e-9))
        (is (near? 0.6049145299 (play p :y 100 0 0) 1e-9))))))

;; --- problems: examples -----------------------------------------------------

(defn- kinds [p] (mapv (juxt :problem :channel) (synth/problems p)))

(deftest problems-examples
  (testing "a patch that is fine"
    (is (= [] (synth/problems (patch 400
                                     {:channel :y :amount 0.3 :curve :arch}
                                     {:channel :glow :amount 0.5 :curve :fade2})))))
  (testing "not a patch: one :invalid with the explanation, nothing else"
    (let [ps (synth/problems {:ms 400 :mods [{:channel :y :amount 1 :curve :wiggle}]})]
      (is (= [:invalid] (mapv :problem ps)))
      (is (some? (:explanation (first ps)))))
    (is (= [:invalid] (mapv :problem (synth/problems nil))))
    (is (= [:invalid] (mapv :problem (synth/problems {:ms 400 :mods []})))))
  (testing "left displaced"
    (is (= [{:problem :not-at-rest :channel :y :value 1.0}]
           (synth/problems (patch 400 {:channel :y :amount 1 :curve :one})))))
  (testing "too far sideways"
    (let [[p :as ps] (synth/problems (patch 400 {:channel :x :amount 0.8 :curve :arch}))]
      (is (= [[:out-of-bounds :x]] (mapv (juxt :problem :channel) ps)))
      (is (near? 0.8 (:value p)))
      (is (= [-0.5 0.5] (:bounds p)))
      (is (<= 199 (:ms p) 200))))
  (testing "a :spring that runs to the end ends on 1"
    (let [[p :as ps] (synth/problems (patch 400 {:channel :y :amount 0.5 :curve :spring}))]
      (is (= [[:not-at-rest :y]] (mapv (juxt :problem :channel) ps)))
      (is (near? 0.5 (:value p)))))
  (testing "a :spring-fall that runs to the end lands"
    (is (= [] (synth/problems (patch 400 {:channel :y :amount 0.5 :curve :spring-fall}))))
    (is (= [] (synth/problems (patch 50 {:channel :y :amount 0.5 :curve :spring-fall})))))
  (testing "a window that closes early leaves nothing behind"
    (is (= [] (synth/problems (patch 400 {:channel :y :amount 1 :curve :one :to 0.5})))))
  (testing "the rest check is a limit, not a value pinned at the end"
    (is (= [[:not-at-rest :y]]
           (kinds (patch 400 {:channel :y :amount 1 :curve :rise-sine}))))
    (is (= [[:not-at-rest :y]]
           (kinds (patch 400 {:channel :y :amount 1 :curve :one :from 0.5
                              :osc {:wave :cosine :period-ms 200}})))))
  (testing "one-sided channels"
    (is (= [[:out-of-bounds :blur]]
           (kinds (patch 400 {:channel :blur :amount -0.2 :curve :arch}))))
    (is (= [[:out-of-bounds :glow]]
           (kinds (patch 400 {:channel :glow :amount 0.5 :curve :arch :alternate? true}))))
    (is (= [] (kinds (patch 400 {:channel :glow :amount 0.5 :curve :arch
                                 :osc {:wave :sine :period-ms 100 :abs? true}})))))
  (testing "mods on one channel are summed"
    (is (= [[:out-of-bounds :x]]
           (kinds (patch 400
                         {:channel :x :amount 0.3 :curve :arch}
                         {:channel :x :amount 0.3 :curve :arc}))))
    (is (= [] (kinds (patch 400
                            {:channel :x :amount 0.3 :curve :arch :each :even}
                            {:channel :x :amount 0.3 :curve :arc :each :odd})))))
  (testing "a crest between two whole ms is seen"
    ;; a 0.9 ms window: the arch peaks at 200.45 ms, and is 0 at ms 200
    (let [[p :as ps] (synth/problems
                      (patch 1000 {:channel :x :amount 0.8 :curve :arch
                                   :from 0.2 :to 0.2009}))]
      (is (= [[:out-of-bounds :x]] (mapv (juxt :problem :channel) ps)))
      (is (near? 0.8 (:value p)))
      (is (= 200 (:ms p)))))
  (testing "a stable order: at rest first, then bounds, in the schema's channel order"
    (is (= [[:not-at-rest :y] [:not-at-rest :glow]
            [:out-of-bounds :x] [:out-of-bounds :z] [:out-of-bounds :glow]]
           (kinds (patch 400
                         {:channel :glow :amount 2 :curve :one}
                         {:channel :z :amount 9 :curve :arch}
                         {:channel :y :amount 1 :curve :rise}
                         {:channel :x :amount -0.8 :curve :arch}))))))

(deftest regression-out-of-bounds
  (testing "a travelling wave takes a one-sided channel below 0 at some position"
    (is (= [[:out-of-bounds :blur]]
           (kinds {:ms 400 :mods [{:channel :blur :amount 0.8 :curve :arch
                                   :osc {:wave :sine :period-ms 1600 :phase :index}}]}))))
  (testing "an :id phase cannot be sampled: it is bounded by its envelope"
    (is (= [[:out-of-bounds :x]]
           (kinds {:ms 50 :mods [{:channel :x :amount 0.6 :curve :arch
                                  :osc {:wave :sine :period-ms 5000 :phase :id}}]}))))
  (testing "nor can noise"
    (is (= [[:out-of-bounds :y]]
           (kinds {:ms 400 :mods [{:channel :y :amount 2.6 :curve :arch
                                   :osc {:wave :noise :period-ms 400}}]})))))

(deftest slam-is-safe
  (is (= [] (synth/problems
             {:ms 900
              :stagger {:ms-per-glyph 15 :max-ms 60}
              :mods [{:channel :scale :amount 1.4 :curve :spring-fall}
                     {:channel :z :amount 2.0 :curve :spring-fall}
                     {:channel :weight :amount 0.8 :curve :fade2}
                     {:channel :glow :amount 0.7 :curve :fade2}]}))))

(def ^:private swell
  "Copied from docs/product/score.md."
  {:ms 2400
   :stagger {:ms-per-glyph 120 :max-ms 900 :order :forward}
   :mods [{:channel :y    :amount 0.2  :curve :rise-sine :from 0.0   :to 0.375}
          {:channel :y    :amount 0.2  :curve :fade2     :from 0.375 :to 1.0}
          {:channel :glow :amount 0.45 :curve :arch}]})

(deftest swell-from-score
  (is (= [] (synth/problems swell)))
  (testing "position 0 at intensity 1 peaks at 0.2 at 900 ms, and only there"
    (let [y (fn [u] (:y (synth/delta swell u 0 7 0 1)))]
      (is (near? 0.2 (y 900) 1e-12))
      (is (every? #(< (y %) (y 900)) (remove #{900} (range 2400))))))
  (testing "each letter lifts 120 ms after its neighbour"
    (is (= [0 120 240 360] (mapv #(synth/start-ms swell % 7) (range 4))))
    (is (== 3120 (synth/total-ms swell 7)))))

;; --- generators -------------------------------------------------------------
;; Honest: nothing here is chosen to make a patch safe. Amounts straddle
;; the bounds in both signs, signed oscillators and :alternate? land on
;; one-sided channels, and any curve may sit in any window, the last
;; included. Only `problems` separates the safe from the unsafe.

(defn- gen-optional
  "A map generator: each key of `optional` is present half the time."
  [required optional]
  (gen/let [base (apply gen/hash-map (mapcat identity required))
            extra (apply gen/hash-map
                         (mapcat (fn [[k g]] [k (gen/one-of [(gen/return ::absent) g])])
                                 optional))]
    (into base (remove #(= ::absent (val %)) extra))))

(def ^:private gen-osc
  (gen-optional
   {:wave (gen/elements [:sine :cosine :triangle :noise])
    :period-ms (gen/one-of [(gen/choose 20 5000)
                            (gen/double* {:min 20 :max 5000 :NaN? false :infinite? false})])}
   {:phase (gen/elements [:same :index :id])
    :salt (gen/choose 0 64)
    :abs? gen/boolean}))

(def ^:private gen-window
  ;; eighths, so no window is thinner than the 1e-9 the rest probe steps back
  (gen/fmap (fn [[a b]] {:from (/ (min a b) 8.0) :to (/ (inc (max a b)) 8.0)})
            (gen/tuple (gen/choose 0 7) (gen/choose 0 7))))

(def ^:private gen-mod
  (gen/let [channel (gen/elements (keys synth/bounds))
            ;; from well inside the channel's bounds to half as far again
            reach (gen/double* {:min 0.3 :max 1.5 :NaN? false :infinite? false})
            negative? gen/boolean
            window (gen/one-of [(gen/return {}) gen-window])
            more (gen-optional
                  {:curve (gen/elements curves)}
                  {:osc gen-osc
                   :each (gen/elements [:all :even :odd])
                   :alternate? gen/boolean})]
    (let [[lo hi] (synth/bounds channel)
          amount (* reach (max (Math/abs lo) (Math/abs hi)))]
      (merge {:channel channel :amount (if negative? (- amount) amount)}
             window
             more))))

(def gen-patch
  (gen-optional
   {:ms (gen/choose 50 2400)
    :mods (gen/frequency [[5 (gen/vector gen-mod 1)]
                          [3 (gen/vector gen-mod 2)]
                          [2 (gen/vector gen-mod 3 12)]])}
   {:stagger (gen-optional
              {:ms-per-glyph (gen/choose 0 500)}
              {:max-ms (gen/choose 0 900)
               :order (gen/elements [:forward :reverse :centre-out])})}))

(def ^:private gen-letter
  "[fraction of :ms, position, glyph id]"
  (gen/tuple (gen/double* {:min 0 :max 1 :NaN? false :infinite? false})
             (gen/choose 0 30)
             (gen/one-of [(gen/choose 0 200) (gen/choose 0 100000000)])))

(defn- finite? [v] (< (- ##Inf) v ##Inf))

(defn- within-bounds? [d tol]
  (every? (fn [[c v]]
            (let [[lo hi] (synth/bounds c)]
              (<= (- lo tol) v (+ hi tol))))
          d))

;; --- properties -------------------------------------------------------------

(defspec generated-patches-are-valid 200
  (prop/for-all [p gen-patch] (schema/valid? :ha/patch p)))

(deftest generator-reaches-safe-and-unsafe-patches
  (let [ps (map #(gen/generate gen-patch 30 %) (range 300))
        {safe true unsafe false} (group-by #(empty? (synth/problems %)) ps)
        found (set (mapcat #(map :problem (synth/problems %)) unsafe))]
    (is (<= 15 (count safe)) "enough safe patches for the safety property to bite")
    (is (<= 15 (count unsafe)))
    (is (= #{:not-at-rest :out-of-bounds} found))
    (testing "safe patches are not only the dull ones"
      (is (some #(some :osc (:mods %)) safe))
      (is (some #(< 1 (count (:mods %))) safe))
      (is (some #(some (fn [m] (#{:spring :spring-fall} (:curve m))) (:mods %)) safe)))))

(defspec delta-finite-and-empty-outside 200
  (prop/for-all [p gen-patch
                 letters (gen/vector gen-letter 1 10)
                 k (gen/double* {:min 0 :max 1 :NaN? false :infinite? false})
                 beyond (gen/double* {:min 0 :max 5000 :NaN? false :infinite? false})]
    (let [ms (:ms p)]
      (and (every? (fn [[f pos id]]
                     (let [u (* f ms)
                           d (synth/delta p u pos 31 id k)]
                       (and (map? d)
                            (every? finite? (vals d))
                            (every? (set (map :channel (:mods p))) (keys d))
                            (or (< u ms) (= {} d))
                            (= {} (synth/delta p (+ ms beyond) pos 31 id k))
                            (= {} (synth/delta p (- -1e-9 beyond) pos 31 id k)))))
                   letters)))))

(defspec delta-deterministic 200
  (prop/for-all [p gen-patch
                 [f pos id] gen-letter
                 k (gen/double* {:min 0 :max 1 :NaN? false :infinite? false})]
    (let [u (* f (:ms p))]
      (and (= (synth/delta p u pos 31 id k) (synth/delta p u pos 31 id k))
           (= (synth/problems p) (synth/problems p))))))

(defspec safe-patches-stay-in-bounds-and-rest 400
  (prop/for-all [p gen-patch
                 letters (gen/vector gen-letter 20 40)
                 k (gen/double* {:min 0 :max 1 :NaN? false :infinite? false})]
    (or (seq (synth/problems p))
        (let [ms (:ms p)]
          (every? (fn [[f pos id]]
                    (let [u (* f ms)]
                      (and
                       ;; a fraction of a ms, a whole ms, and a lower intensity
                       (within-bounds? (synth/delta p u pos 31 id 1) 1e-3)
                       (within-bounds? (synth/delta p (Math/floor u) pos 31 id 1) 1e-3)
                       (within-bounds? (synth/delta p u pos 31 id k) 1e-3)
                       (every? #(near? 0 % 1e-6)
                               (vals (synth/delta p (- ms 1e-9) pos 31 id 1))))))
                  letters)))))

(defspec problems-never-throws 300
  (prop/for-all [x (gen/one-of [gen/any
                                (gen/fmap #(assoc % :ms 400) (gen/map gen/keyword gen/any))
                                (gen/fmap (fn [[p k v]] (assoc p k v))
                                          (gen/tuple gen-patch gen/keyword gen/any))])]
    (let [ps (synth/problems x)]
      (and (vector? ps)
           (every? #(keyword? (:problem %)) ps)))))
