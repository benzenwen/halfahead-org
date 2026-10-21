(ns halfahead.app.compose-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [halfahead.app.compose :as compose]))

(deftest marks-one-word
  (is (= {:text "the one habit" :emphasis [[4 7]]}
         (compose/parse-emphasis "the *one* habit"))))

(deftest marks-two-spans
  (is (= {:text "a and b c" :emphasis [[0 1] [6 9]]}
         (compose/parse-emphasis "*a* and *b c*"))))

(deftest literal-asterisks-stay
  (doseq [s ["2 * 3 * 4" "**x**" "* x *" "**" "*" "a * b" "*open"]]
    (is (= {:text s :emphasis []} (compose/parse-emphasis s)) s)))

(deftest caps-at-eight
  (let [s (str/join " " (map #(str "*w" % "*") (range 9)))
        {:keys [text emphasis]} (compose/parse-emphasis s)]
    (is (= 8 (count emphasis)))
    (is (= (mapv #(str "w" %) (range 8)) (mapv (fn [[a b]] (subs text a b)) emphasis)))
    (is (str/ends-with? text " *w8*"))))

(deftest blank-after-strip-is-unchanged
  (is (= {:text "* *" :emphasis []} (compose/parse-emphasis "* *"))))

(defspec spans-lie-inside-sorted-and-bounded 300
  (prop/for-all [s (gen/fmap str/join
                             (gen/vector (gen/frequency [[4 (gen/return "*")]
                                                         [3 (gen/return " ")]
                                                         [4 gen/char-alpha]])
                                         0 40))]
    (let [{:keys [text emphasis]} (compose/parse-emphasis s)
          flat (flatten emphasis)]
      (and (<= (count emphasis) 8)
           (every? (fn [[a b]] (<= 0 a) (< a b) (<= b (count text))) emphasis)
           (= flat (sort flat))
           (every? (fn [[[_ b] [a _]]] (<= b a)) (partition 2 1 emphasis))))))
