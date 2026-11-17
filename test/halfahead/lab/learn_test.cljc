(ns halfahead.lab.learn-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [halfahead.lab.learn :as learn]
            [halfahead.schema :as schema]))

(def ^:private axes [:energy :flourish :softness :warmth])

(defn- point [e f s w] {:energy e :flourish f :softness s :warmth w})

(def ^:private centre (point 0.5 0.5 0.5 0.5))

(def gen-unit (gen/double* {:min 0.0 :max 1.0 :NaN? false :infinite? false}))

(def gen-point (gen/fmap #(apply point %) (gen/vector gen-unit 4)))

(deftest start-shape
  (is (= {:point  centre
          :step   (point 0.25 0.25 0.25 0.25)
          :dir    (point 1 1 1 1)
          :missed (point 0 0 0 0)
          :axis   :energy
          :flicks 0}
         (learn/start)))
  (let [p (point 0.1 0.2 0.3 0.4)]
    (is (= (assoc (learn/start) :point p) (learn/start p))))
  (is (schema/valid? :ha/style-point (:point (learn/start)))))

(deftest pair-shape
  (is (= {:axis :energy :a centre :b (point 0.75 0.5 0.5 0.5)}
         (learn/pair (learn/start))))
  (testing "the step is kept inside the unit interval"
    (is (= (point 1.0 0.5 0.5 0.5)
           (:b (learn/pair (learn/start (point 0.9 0.5 0.5 0.5))))))))

(deftest wall-flips-direction
  (testing "at the upper wall the other direction is offered"
    (let [s (learn/start (point 1.0 0.5 0.5 0.5))
          {:keys [a b]} (learn/pair s)]
      (is (= (point 1.0 0.5 0.5 0.5) a))
      (is (= (point 0.75 0.5 0.5 0.5) b))
      (testing "and kept when it is preferred"
        (let [s' (learn/choose s :b)]
          (is (= b (:point s')))
          (is (= -1 (get-in s' [:dir :energy])))
          (is (= 0 (get-in s' [:missed :energy])))))
      (testing "and counted as one rejection when it is not"
        (let [s' (learn/choose s :a)]
          (is (= a (:point s')))
          (is (= 1 (get-in s' [:dir :energy])))
          (is (= 1 (get-in s' [:missed :energy])))))))
  (testing "at the lower wall"
    (let [s (assoc-in (learn/start (point 0.0 0.5 0.5 0.5)) [:dir :energy] -1)
          s' (learn/choose s :b)]
      (is (= (point 0.25 0.5 0.5 0.5) (:b (learn/pair s))))
      (is (= 1 (get-in s' [:dir :energy])))
      (is (= -1 (get-in (learn/choose s :a) [:dir :energy]))))))

(deftest scripted-sequence
  (testing "moves, flips, a halving, the round robin and the count"
    (is (= {:point  (point 0.75 0.625 1.0 0.25)
            :step   (point 0.125 0.125 0.25 0.25)
            :dir    (point 1 1 1 -1)
            :missed (point 0 0 0 0)
            :axis   :softness
            :flicks 10}
           (reduce learn/choose (learn/start)
                   [:b :a :b :a      ; energy up; flourish and warmth refused once
                    :a :a :b :b      ; energy refused at 1.0; flourish halves; warmth down
                    :a :b]))))       ; energy refused both ways: halves; flourish up
  (testing "one rejection flips the direction and keeps the step"
    (let [s (learn/choose (learn/start) :a)]
      (is (= centre (:point s)))
      (is (= -1 (get-in s [:dir :energy])))
      (is (= 1 (get-in s [:missed :energy])))
      (is (= 0.25 (get-in s [:step :energy])))
      (is (= :flourish (:axis s)))))
  (testing "the step never goes below 1/64"
    (let [s (reduce learn/choose (learn/start) (repeat 48 :a))]
      (is (= centre (:point s)))
      (is (= (point 0.015625 0.015625 0.015625 0.015625) (:step s)))
      (is (= 48 (:flicks s))))))

(deftest unknown-choice-throws
  (is (= :ha.learn/unknown-choice
         (try (learn/choose (learn/start) :c)
              (catch #?(:clj Exception :cljs :default) e (:type (ex-data e)))))))

(defspec point-stays-valid 200
  (prop/for-all [p gen-point
                 choices (gen/vector (gen/elements [:a :b]) 0 200)]
    (every? true?
            (map-indexed
             (fn [i s]
               (let [{:keys [a b]} (learn/pair s)]
                 (and (schema/valid? :ha/style-point (:point s))
                      (every? #(<= 0.015625 % 0.25) (vals (:step s)))
                      (every? #{1 -1} (vals (:dir s)))
                      (every? #{0 1} (vals (:missed s)))
                      (= (set axes) (set (keys (:step s))))
                      (= i (:flicks s))
                      (= (nth axes (mod i 4)) (:axis s))
                      (= a (:point s))
                      (not= a b)
                      (schema/valid? :ha/style-point b))))
             (reductions learn/choose (learn/start p) choices)))))

(defspec noiseless-distance-never-increases 200
  (prop/for-all [hidden gen-point
                 seed gen/large-integer]
    (let [ds (learn/simulate {:hidden hidden :noise 0.0 :flicks 32 :seed seed})]
      (and (= 33 (count ds))
           (= (first ds) (learn/distance centre hidden))
           (apply >= ds)))))

(deftest distance-is-root-mean-square
  (is (= 0.0 (learn/distance centre centre)))
  (is (= 0.5 (learn/distance centre (point 1.0 1.0 1.0 1.0))))
  (is (= 0.25 (learn/distance centre (point 1.0 0.5 0.5 0.5)))))

(deftest simulate-deterministic
  ;; with no noise the seed has nothing to decide, so these use noise
  (let [run #(learn/simulate {:hidden (point 0.9 0.1 0.3 0.7) :noise 0.25
                              :flicks 32 :seed %})]
    (is (= (run 1) (run 1)))
    (is (not= (run 1) (run 2)))
    (is (= 33 (count (run 1))))))

(deftest experiment-deterministic
  (let [run #(learn/experiment {:readers 50 :flicks 8 :noise 0.1 :seed %})
        {:keys [median p80] :as one} (run 1)]
    (is (= one (run 1)))
    (is (not= one (run 2)))
    (is (= 9 (count median) (count p80)))
    (is (every? true? (map <= median p80)))))

(deftest experiment-is-the-same-on-both-hosts
  ;; pinned from the Babashka run; node must agree
  (let [{:keys [median p80]} (learn/experiment {:readers 50 :flicks 8 :noise 0.1 :seed 1})]
    (is (= 0.14682829896025104 (nth median 8)))
    (is (= 0.23241451279618666 (nth p80 8)))))

(deftest measured-facts
  ;; H5 in docs/product/lab-notebook.md. Hypotheses about the learner as
  ;; specified, asserted loosely; not requirements to tune it toward.
  (let [median #(:median (learn/experiment {:readers 300 :flicks 24 :noise % :seed 1}))]
    (testing "with no noise, two dozen flicks land close"
      (is (< (nth (median 0.0) 24) 0.1)))
    (testing "with one flick in ten mistaken, the learner still gains"
      (let [m (median 0.1)]
        (is (< (nth m 24) (nth m 0)))))))
