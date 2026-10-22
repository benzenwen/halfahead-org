(ns halfahead.app.scene-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [halfahead.app.core :as core]
            [halfahead.app.scene :as scene]
            [halfahead.perform :as perform]))

(def opts {:seed-fn core/seed :reduced-motion? false})

(defn m [id text delivery sent-ms]
  {:id id :text text :delivery delivery :emphasis []
   :sent-at (#?(:clj java.util.Date. :cljs js/Date.) sent-ms)})

(defn duration-of [{:keys [id text delivery]}]
  (:duration-ms (perform/perform {:text text :delivery delivery :seed (core/seed id)})))

(deftest first-at-zero-and-ordered-by-sent-at
  (let [late  (m "a" "late" :plain 9000)
        early (m "b" "early" :plain 1000)
        tie1  (m "c" "tie" :plain 5000)
        tie2  (m "d" "tie" :plain 5000)
        s     (scene/schedule [late tie2 early tie1] opts)]
    (is (= ["b" "c" "d" "a"] (map :id s)))
    (is (= 0 (:at-ms (first s))))
    (is (= [0] (distinct (map :at-ms (scene/schedule [early] opts)))))))

(deftest pauses-are-clamped
  (let [a (m "a" "one" :plain 0)
        b (m "b" "two" :plain 50)
        c (m "c" "three" :plain 10000000)
        [sa sb sc] (scene/schedule [a b c] opts)
        pause #(- (:at-ms %2) (:at-ms %1) (duration-of %3))]
    (is (= 300 (pause sa sb a)))
    (is (= 2500 (pause sb sc b)))))

(deftest long-conversations-fit-the-budget
  (let [ms (mapv #(m (str "m" %) (apply str (repeat 4000 "word ")) :typed (* % 1000)) (range 200))
        {:keys [end-ms schedule]} (scene/plan ms opts)]
    (is (= 200 (count schedule)))
    (is (<= end-ms 30000))
    (is (= (sort (map :at-ms schedule)) (map :at-ms schedule)))))

(deftest reduced-motion-is-instant
  (let [ms [(m "a" "x" :typed 0) (m "b" "y" :plain 5000)]]
    (is (= [0 0] (map :at-ms (scene/schedule ms (assoc opts :reduced-motion? true)))))))

(deftest scene-clock-stops-while-paused
  (let [s {:started-at 100 :paused-at nil :end-ms 1000}]
    (is (= 50 (scene/scene-ms s 150)))
    (is (= 20 (scene/scene-ms (assoc s :paused-at 120) 900)))
    (is (= 5 (scene/progress (assoc s :paused-at 150) 900)))))

(def gen-message
  (gen/let [text (gen/such-that seq gen/string-alphanumeric 50)
            delivery (gen/elements [:plain :typed :headline :aside :credits])
            sent (gen/choose 0 100000)
            n gen/nat]
    (m (str "id" n "-" sent) text delivery sent)))

(def gen-conversation
  (gen/fmap #(vals (into {} (map (juxt :id identity)) %)) (gen/vector gen-message 0 30)))

(defspec schedule-invariants 100
  (prop/for-all [ms gen-conversation
                 reduced? gen/boolean]
    (let [o  (assoc opts :reduced-motion? reduced?)
          s  (scene/schedule ms o)
          ats (map :at-ms s)]
      (and (= s (scene/schedule (reverse ms) o))
           (or (empty? s) (zero? (first ats)))
           (= ats (sort ats))
           (<= (:end-ms (scene/plan ms o)) 30000)
           (or (not reduced?) (every? zero? ats))))))
