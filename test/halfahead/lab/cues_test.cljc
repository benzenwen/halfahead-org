(ns halfahead.lab.cues-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [halfahead.lab.cues :as cues]
            [halfahead.lab.glossary :as glossary]
            [halfahead.schema :as schema]))

(defn- cue [word following]
  (select-keys (cues/word-cue word following) [:effect :intensity :source]))

(deftest glossary-first
  (is (= {:word "deploy" :effect :launch :intensity 0.9 :source :glossary}
         (cues/word-cue "deploy" " ")))
  (testing "lower-cased, one trailing s removed, word kept as typed"
    (is (= {:word "Deploys" :effect :launch :intensity 0.9 :source :glossary}
           (cues/word-cue "Deploys" "."))))
  (testing "a following ! adds 0.2, capped at 1"
    (is (== 1.0 (:intensity (cues/word-cue "ship" "!"))))
    (is (== 1.0 (:intensity (cues/word-cue "crash" "!"))))
    (is (= :glossary (:source (cues/word-cue "ship" "!")))))
  (testing "the glossary beats punctuation"
    (is (= {:effect :launch :intensity 0.9 :source :glossary}
           (cue "deploy" "?")))))

(deftest punctuation-second
  (is (= {:effect :quake :intensity 0.7 :source :punctuation} (cue "really" "!")))
  (is (= {:effect :wobble :intensity 0.5 :source :punctuation} (cue "why" "?")))
  (testing "punctuation beats shape"
    (is (= :punctuation (:source (cues/word-cue "NOPE" "!"))))))

(deftest shape-third
  (is (= {:effect :launch :intensity 0.8 :source :shape} (cue "NOPE" " ")))
  (is (= {:effect :shimmer :intensity 0.5 :source :shape}
         (cue "internationalisation" " ")))
  (is (= {:effect :pop :intensity 0.4 :source :shape} (cue "2026" " ")))
  (testing "one capital letter is not shouting"
    (is (= :default (:source (cues/word-cue "A" " "))))
    (is (= :default (:source (cues/word-cue "A1" " "))))))

(deftest quiet-default
  (is (= {:word "table" :effect :pop :intensity 0.3 :source :default}
         (cues/word-cue "table" " "))))

;; --- property ---------------------------------------------------------------

(def ^:private gen-piece
  (gen/one-of [(gen/fmap str gen/char)
               (gen/fmap str gen/char-alphanumeric)
               (gen/elements ["😀" "日本語" " " "s" "S" "!" "DEPLOY" "ship"])]))

(def ^:private gen-word
  (gen/such-that (complement str/blank?)
                 (gen/fmap #(let [w (str/join %)] (subs w 0 (min 200 (count w))))
                           (gen/vector gen-piece 1 120))
                 100))

(def ^:private gen-following
  (gen/one-of [(gen/fmap str gen/char) (gen/elements [" " "!" "?" "." ","])]))

(defspec every-word-gives-a-valid-cue 200
  (prop/for-all [w gen-word f gen-following]
    (schema/valid? :ha/word-cue (cues/word-cue w f))))

(deftest awkward-words-stay-valid
  (doseq [w [(str (apply str (repeat 70 " ")) "x")
             (str (apply str (repeat 63 "a")) "😀")
             (apply str (repeat 200 "z"))
             (apply str (repeat 200 "Q"))]]
    (let [c (cues/word-cue w " ")]
      (is (schema/valid? :ha/word-cue c) (pr-str (schema/explain :ha/word-cue c)))
      (is (<= (count (:word c)) 64)))))

;; --- the glossary itself ------------------------------------------------------

(deftest glossary-is-well-formed
  (doseq [[k {:keys [effect intensity]}] glossary/entries]
    (is (schema/valid? :ha/word-effect effect) k)
    (is (schema/valid? :ha/unit intensity) k)
    (is (= k (str/lower-case k)) (str k " can never be looked up"))))
