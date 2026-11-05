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
