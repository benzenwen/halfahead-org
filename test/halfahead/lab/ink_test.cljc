(ns halfahead.lab.ink-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [halfahead.lab.ink :as ink]
            [halfahead.schema :as schema]))

(defn- rgb [hex]
  (mapv #(/ (bit-and (bit-shift-right hex %) 0xff) 255.0) [16 8 0]))

(def ^:private inks [:text :signal :gold :cool :muted])

(def ^:private backgrounds
  "lights -> the background the renderer draws behind the letters."
  {0 (rgb 0xf6f1e7) 1 (rgb 0x141210)})

(defn- close? [a b]
  (every? #(< (Math/abs (double %)) 1e-9) (map - a b)))

(defn- distance [a b]
  (Math/sqrt (reduce + (map #(* % %) (map - a b)))))

(deftest the-palette-is-the-schemas
  (is (every? #(schema/valid? :ha/ink %) inks))
  (is (not (schema/valid? :ha/ink :chartreuse)))
  (is (= :lab/unknown-ink
         (try (ink/colour :chartreuse 0)
              (catch #?(:clj clojure.lang.ExceptionInfo :cljs ExceptionInfo) e
                (:type (ex-data e)))))))

(deftest text-and-signal-are-the-pages-own
  (is (close? (rgb 0x1d1a16) (ink/colour :text 0)))
  (is (close? (rgb 0xefe6d6) (ink/colour :text 1)))
  (is (close? (rgb 0xc2401c) (ink/colour :signal 0)))
  (is (close? (rgb 0xff7a55) (ink/colour :signal 1))))

(deftest every-ink-is-readable-on-both-backgrounds
  (doseq [[lights bg] backgrounds
          k inks]
    (testing (str k " at lights " lights)
      (is (>= (ink/contrast (ink/colour k lights) bg) 4.5)))))

(deftest the-inks-are-told-apart-on-both-backgrounds
  (doseq [lights (keys backgrounds)
          [i a] (map-indexed vector inks)
          b (drop (inc i) inks)]
    (testing (str a " and " b " at lights " lights)
      (is (>= (distance (ink/colour a lights) (ink/colour b lights)) 0.2)))))

(deftest contrast-is-wcags
  (is (< (Math/abs (- 21.0 (ink/contrast [0 0 0] [1 1 1]))) 1e-9))
  (is (== 1 (ink/contrast [0.3 0.5 0.7] [0.3 0.5 0.7])))
  (is (== (ink/contrast [0.1 0.2 0.3] [0.9 0.8 0.7])
          (ink/contrast [0.9 0.8 0.7] [0.1 0.2 0.3])))
  ;; #767676 on white, the usual example of just passing AA
  (is (< 4.5 (ink/contrast (rgb 0x767676) [1 1 1]) 4.6)))

(defspec between-the-lights-a-colour-is-between-its-ends 200
  (prop/for-all [k (gen/elements inks)
                 lights (gen/double* {:min 0.0 :max 1.0 :NaN? false :infinite? false})]
    (let [paper (ink/colour k 0), stage (ink/colour k 1)
          c (ink/colour k lights)]
      (and (= 3 (count c))
           (every? true? (map (fn [v p s] (<= (- (min p s) 1e-12) v (+ (max p s) 1e-12)))
                              c paper stage))
           (close? c (map (fn [p s] (+ p (* lights (- s p)))) paper stage))))))
