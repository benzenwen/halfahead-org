(ns halfahead.lab.style-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [halfahead.lab.style :as style]
            [halfahead.schema :as schema]))

(defn- glyphs-born [borns]
  (vec (map-indexed (fn [i b] {:id i :char "a" :born-ms b}) borns)))

(defn- typed
  "Birth times from 0, one interval after another, cycling through gaps
  until there are n glyphs."
  [n gaps]
  (vec (reductions + 0 (take (dec n) (cycle gaps)))))

(defn- infer-at-last [borns]
  (style/infer (glyphs-born borns) (peek borns)))

(deftest params-for-every-style
  (doseq [s style/styles
          :let [{:keys [tempo overshoot amplitude sway steps] :as ps} (style/params s)]]
    (testing (str s)
      (is (schema/valid? :ha/style s))
      (is (= #{:tempo :overshoot :amplitude :sway :steps} (set (keys ps))))
      (is (< 0 tempo 2))
      (is (< (* tempo 1500) 2500) "the longest cue settles within 2500 ms")
      (is (<= 0 overshoot 2))
      (is (< 0 amplitude 2))
      (is (or (nil? sway) (and (< 0 (:amp sway) 0.25) (pos? (:period-ms sway)))))
      (is (or (nil? steps) (pos-int? steps)))))
  (is (= #{:plain :elegant :caffeinated :drunken :coarse} (set style/styles))
      "every style in the schema has params")
  (is (= {:tempo 1.0 :overshoot 1.0 :amplitude 1.0 :sway nil :steps nil}
         (style/params :plain)))
  (is (= 3 (:steps (style/params :coarse))))
  (is (= 0.0 (:overshoot (style/params :elegant)))))

(deftest unknown-style-is-an-error
  (is (= :ha/unknown-style
         (try (style/params :wild) nil
              (catch #?(:clj Exception :cljs :default) e (:type (ex-data e)))))))

(deftest infer-on-synthetic-rhythms
  (testing "fast and steady"
    (is (= :caffeinated (infer-at-last (typed 14 [60])))))
  (testing "fast and erratic: median under 110 ms, widely spread"
    (is (= :coarse (infer-at-last (typed 14 [20 20 200])))))
  (testing "slow and steady"
    (is (= :elegant (infer-at-last (typed 13 [450])))))
  (testing "slow and erratic"
    (is (= :drunken (infer-at-last (typed 10 [400 400 1500])))))
  (testing "in between"
    (is (= :plain (infer-at-last (typed 14 [200])))))
  (testing "too few intervals"
    (is (= :plain (infer-at-last (typed 4 [60]))))
    (is (= :plain (style/infer [] 1000))))
  (testing "a paste is one keystroke, not a burst"
    (is (= :plain (infer-at-last (vec (repeat 20 5000)))))
    (is (= :elegant (infer-at-last (into (typed 8 [450]) (repeat 6 3600))))))
  (testing "only the last 12 glyphs count"
    (is (= :elegant (infer-at-last (into (typed 10 [60]) (map #(+ 600 (* 450 %)) (range 1 13)))))))
  (testing "glyphs more than 6 s old are forgotten"
    (is (= :plain (style/infer (glyphs-born (typed 14 [60])) 7000))))
  (testing "glyphs born after t do not count yet"
    (let [borns (into (typed 4 [450]) (map #(+ 1350 (* 60 %)) (range 1 13)))]
      (is (= :plain (style/infer (glyphs-born borns) 1350)))
      (is (= :caffeinated (style/infer (glyphs-born borns) (peek borns)))))))

(defspec infer-is-a-style 200
  (prop/for-all [borns (gen/vector (gen/choose 0 10000) 0 30)
                 t (gen/choose 0 12000)]
    (schema/valid? :ha/style (style/infer (glyphs-born borns) t))))

(defspec infer-ignores-glyph-order 100
  (prop/for-all [borns (gen/vector (gen/choose 0 3000) 0 20)
                 t (gen/choose 0 4000)]
    (= (style/infer (glyphs-born borns) t)
       (style/infer (glyphs-born (shuffle borns)) t))))

;; --- the style space ---------------------------------------------------------

(defn- close-to? [a b] (< (Math/abs (- a b)) 1e-9))

(defn- params-close?
  "from-point's result against expected tempo, overshoot and amplitude."
  [ps [tempo overshoot amplitude]]
  (and (= #{:tempo :overshoot :amplitude :sway :steps} (set (keys ps)))
       (close-to? tempo (:tempo ps))
       (close-to? overshoot (:overshoot ps))
       (close-to? amplitude (:amplitude ps))
       (nil? (:sway ps))
       (nil? (:steps ps))))

(deftest point-for-every-style
  (is (= {:plain       {:energy 0.5  :flourish 0.35 :softness 0.5 :warmth 0.5}
          :elegant     {:energy 0.2  :flourish 0.45 :softness 0.9 :warmth 0.7}
          :caffeinated {:energy 0.95 :flourish 0.75 :softness 0.2 :warmth 0.5}
          :drunken     {:energy 0.4  :flourish 0.65 :softness 0.8 :warmth 0.6}
          :coarse      {:energy 0.8  :flourish 0.55 :softness 0.0 :warmth 0.15}}
         (into {} (map (juxt identity style/point)) style/styles)))
  (doseq [s style/styles]
    (is (schema/valid? :ha/style-point (style/point s)) (str s)))
  (is (= :ha/unknown-style
         (try (style/point :wild) nil
              (catch #?(:clj Exception :cljs :default) e (:type (ex-data e)))))))

(deftest as-point-test
  (let [p {:energy 0.1 :flourish 0.2 :softness 0.3 :warmth 0.4}]
    (is (= p (style/as-point p)) "a point is itself"))
  (doseq [s style/styles]
    (is (= (style/point s) (style/as-point s)) (str s))))

(deftest from-point-at-named-points
  (is (= {:tempo 1.0 :overshoot 1.0 :amplitude 1.0 :sway nil :steps nil}
         (style/params :plain)
         (style/from-point (style/point :plain)))
      "the middle of the space is the lab's unstyled motion exactly, whatever Plain's flourish")
  (doseq [[s expected] {:plain       [1.0 1.0 1.0]
                        :elegant     [1.36 0.2 0.85]
                        :caffeinated [0.55 1.6 1.225]
                        :drunken     [1.12 0.4 0.95]
                        :coarse      [0.7 2.0 1.15]}]
    (is (params-close? (style/from-point (style/point s)) expected) (str s)))
  (testing "the named styles keep their own params"
    (is (= {:tempo 1.5 :overshoot 0.0 :amplitude 0.8 :sway nil :steps nil}
           (style/params :elegant)))
    (is (= {:tempo 0.6 :overshoot 1.8 :amplitude 1.15
            :sway {:amp 0.03 :period-ms 45} :steps nil}
           (style/params :caffeinated)))
    (is (= {:tempo 1.4 :overshoot 1.6 :amplitude 1.1
            :sway {:amp 0.12 :period-ms 900} :steps nil}
           (style/params :drunken)))
    (is (= {:tempo 0.5 :overshoot 1.0 :amplitude 1.25 :sway nil :steps 3}
           (style/params :coarse)))))

(deftest from-point-at-corners
  (doseq [energy [0.0 1.0] softness [0.0 1.0] flourish [0.0 1.0] warmth [0.0 1.0]]
    (is (= {:tempo (if (zero? energy) 1.5 0.5)
            :overshoot (if (zero? softness) 2.0 0.0)
            :amplitude (if (zero? energy) 0.75 1.25)
            :sway nil
            :steps nil}
           (style/from-point {:energy energy :flourish flourish
                              :softness softness :warmth warmth}))
        (str [energy flourish softness warmth]))))

;; Order 0051: the calmest point is as slow as Elegant and no slower, so
;; every motion still ends within lab.pose's 5000 ms.
(deftest calmest-point-is-as-slow-as-elegant
  (is (= 1.5 (:tempo (style/from-point {:energy 0 :flourish 0 :softness 0.5 :warmth 0.5}))))
  (is (= (:tempo (style/params :elegant))
         (:tempo (style/from-point {:energy 0 :flourish 0 :softness 0.5 :warmth 0.5})))))

(def ^:private gen-unit
  (gen/double* {:min 0.0 :max 1.0 :NaN? false :infinite? false}))

(defspec from-point-shape 200
  (prop/for-all [point (gen/hash-map :energy gen-unit :flourish gen-unit
                                     :softness gen-unit :warmth gen-unit)]
    (let [{:keys [tempo overshoot amplitude sway steps] :as ps} (style/from-point point)]
      (and (= #{:tempo :overshoot :amplitude :sway :steps} (set (keys ps)))
           (<= 0.5 tempo 1.5)
           (<= 0.75 amplitude 1.25)
           (<= 0.0 overshoot 2.0)
           (nil? sway)
           (nil? steps)))))
