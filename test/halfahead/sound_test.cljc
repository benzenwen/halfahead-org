(ns halfahead.sound-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [halfahead.sound :as sound]))

(defn- span [{:keys [notes]}]
  (apply max (map #(+ (:at-ms %) (:ms %)) notes)))

(defn- valid-cue? [{:keys [notes] :as cue}]
  (and (seq notes)
       (every? (fn [{:keys [hz ms at-ms gain]}]
                 (and (pos? hz) (pos? ms) (>= at-ms 0) (< 0 gain 0.151)))
               notes)
       (<= (span cue) 600)))

(defn- cue-of [delivery] (sound/cue {:delivery delivery}))

(deftest plain-has-no-cue
  (is (nil? (cue-of :plain)))
  (is (nil? (cue-of :fireworks))))

(deftest every-other-delivery-has-a-valid-cue
  (doseq [d [:headline :typed :aside :credits]]
    (is (valid-cue? (cue-of d)) (str d))))

(deftest figures
  (let [hz #(mapv :hz (:notes (cue-of %)))]
    (is (= 2 (count (hz :headline))))
    (is (apply < (hz :headline)))
    (is (= 3 (count (hz :credits))))
    (is (apply > (hz :credits)))
    (is (< (first (hz :aside)) (first (hz :typed))))))

(defspec cue-bounds-over-messages 100
  (prop/for-all [d (gen/elements [:plain :typed :headline :aside :credits :nope nil])
                 text gen/string]
    (let [c (sound/cue {:text text :delivery d})]
      (if (#{:headline :typed :aside :credits} d)
        (valid-cue? c)
        (nil? c)))))
