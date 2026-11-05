(ns halfahead.lab.stage.light-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [halfahead.lab.stage.light :as light]))

(defn- close? [a b] (< (Math/abs (- a b)) 1e-12))

(deftest flash-follows-its-curve
  (testing "at at-ms it is the intensity"
    (is (== 1.0 (light/flash 1000 1.0 1000)))
    (is (== 0.6 (light/flash 1000 0.6 1000))))
  (testing "half way through, a quarter of it"
    (is (close? 0.25 (light/flash 1000 1.0 1450)))
    (is (close? 0.15 (light/flash 1000 0.6 1450))))
  (testing "0 at and after 900 ms, before at-ms, and with no at-ms"
    (is (== 0 (light/flash 1000 1.0 1900)))
    (is (== 0 (light/flash 1000 1.0 3000)))
    (is (== 0 (light/flash 1000 1.0 999)))
    (is (== 0 (light/flash nil 1.0 1000)))))

(defspec flash-stays-between-0-and-its-intensity 200
  (prop/for-all [at-ms (gen/choose 0 100000)
                 offset (gen/double* {:min -2000 :max 3000 :NaN? false :infinite? false})
                 intensity (gen/double* {:min 0 :max 1 :NaN? false :infinite? false})]
    (let [b (light/flash at-ms intensity (+ at-ms offset))]
      (<= 0 b intensity))))

(deftest pulse-is-one-arch
  (is (== 0 (light/pulse 1000 1000)))
  (is (close? 0 (light/pulse 1000 1700)))
  (is (close? 1 (light/pulse 1000 1350)))
  (is (== 0 (light/pulse 1000 999)))
  (is (== 0 (light/pulse 1000 1701)))
  (is (== 0 (light/pulse nil 1000))))
