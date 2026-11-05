(ns halfahead.lab.director-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [halfahead.lab.cues :as cues]
            [halfahead.lab.director :as director]
            [halfahead.lab.draft :as draft]
            [halfahead.schema :as schema]))

;; --- helpers ----------------------------------------------------------------

(defn- edit
  "What the lab's shell does on an edit: the draft's edit, its directions
  carried over, then the director at the same time."
  [d text t]
  (-> (draft/edit d text t cues/word-cue)
      (assoc :directions (:directions d []))
      (director/direct t)))

(defn- type-at
  "d after typing text one unit at a time from t0, step ms apart; the
  director runs on every edit."
  [d text t0 step]
  (reduce (fn [d k] (edit d (str (:text d) (subs text (dec k) k)) (+ t0 (* step (dec k)))))
          d
          (range 1 (inc (count text)))))

(defn- typed [text t0] (type-at (draft/empty-draft) text t0 10))

(defn- phrase
  "The chars of direction d's glyphs in draft."
  [d direction]
  (let [chars (into {} (map (juxt :id :char)) (:glyphs d))]
    (apply str (map chars (:glyph-ids direction)))))

(defn- shots [d] (mapv (juxt #(phrase d %) :shot) (:directions d)))

;; --- phrases ----------------------------------------------------------------

(deftest punctuation-ends-phrases
  (let [d (typed "a. b! c? d" 0)]
    (is (= ["a." "b!" "c?"] (mapv first (shots d))))
    (testing "the unfinished phrase waits"
      (is (= 3 (count (:directions d)))))))

(deftest a-newline-ends-a-phrase-and-is-not-in-it
  (let [d (typed "dear Jon\nhi" 0)]
    (is (= [["dearJon" :line]] (shots d)))
    (is (not-any? #{"\n"} (map #(get (into {} (map (juxt :id :char)) (:glyphs d)) %)
                               (:glyph-ids (first (:directions d))))))))

(deftest ids-are-the-non-space-glyphs
  (let [d (typed "  so  it goes. " 0)
        spaces (into #{} (comp (filter #(str/blank? (:char %))) (map :id)) (:glyphs d))]
    (is (= ["soitgoes."] (mapv first (shots d))))
    (is (not-any? spaces (:glyph-ids (first (:directions d)))))))

(deftest a-pause-ends-a-phrase
  (let [d (typed "hello there" 0)                     ; last glyph born at 100
        direct #(director/direct d %)]
    (testing "1199 ms after the last glyph it is still being written"
      (is (= [] (:directions (direct 1299)))))
    (testing "1200 ms after it is finished"
      (is (= [["hellothere" :line]] (shots (direct 1300)))))
    (testing "and stays finished when typing resumes"
      (let [d (type-at (direct 1300) " again." 2000 10)]
        (is (= [["hellothere" :line] ["again." :line]] (shots d)))))))

(deftest a-phrase-without-a-word-is-not-directed
  (is (= [["what?" :push]] (shots (typed "what?! ?. " 0))))
  (is (= [] (:directions (director/direct (typed "... " 0) 5000)))))

(deftest a-directed-phrase-is-directed-once
  (let [d (typed "so it goes. " 0)]
    (is (= (:directions d) (:directions (director/direct d 50000))))
    (testing "inserting into it does not stage it again"
      (let [d' (edit d "so it really goes. " 1000)]
        (is (= 1 (count (:directions d'))))))))

;; --- rules ------------------------------------------------------------------

(defn- only
  "The first direction of text typed from 0, 10 ms a key, then left alone
  until 5000 ms."
  [text]
  (let [[d] (:directions (director/direct (typed text 0) 5000))]
    (select-keys d [:shot :energy :source :at-ms])))

(deftest the-rules
  (testing "! is a card"
    (is (= {:shot :card :energy 0.9 :source :rules :at-ms 100} (only "what a day! "))))
  (testing "most words in capitals is a card"
    (is (= :card (:shot (only "WHAT A day. "))))
    (is (= :line (:shot (only "WHAT a day. ")))))
  (testing "? is a push"
    (is (= {:shot :push :energy 0.6 :source :rules :at-ms 70} (only "why not? "))))
  (testing "12 or more words ending in . is a pull"
    (let [twelve "one two three four five six seven eight nine ten eleven twelve."]
      (is (= {:shot :pull :energy 0.5 :source :rules :at-ms (* 10 (dec (count twelve)))}
             (only twelve)))
      (is (= :line (:shot (only (str/replace twelve "twelve" "")))))))
  (testing "anything else stays in the line"
    (is (= {:shot :line :energy 0.2 :source :rules :at-ms 5000} (only "so it is "))))
  (testing "the direction is placed at now"
    (is (= 7777 (:at-ms (first (:directions (director/direct (typed "ok" 0) 7777))))))))

;; --- ids --------------------------------------------------------------------

(deftest directions-keep-only-present-glyphs
  (let [d (typed "so it goes. then " 0)]
    (testing "deleting part of a phrase shrinks its direction"
      (let [d' (edit d "so goes. then " 1000)]
        (is (= [["sogoes." :line]] (shots d')))))
    (testing "deleting all of a :line phrase drops its direction"
      (is (= [] (:directions (edit d "then " 1000)))))))

(deftest a-deleted-spectacle-remembers-the-budget
  (let [d (typed "wow! " 0)                 ; the card is at 30
        gone (edit d "" 1000)]
    (testing "until 10 s after it, it stays as a placeholder"
      (is (= [:card] (mapv :shot (:directions gone))))
      (is (= [:line] (mapv :shot (drop 1 (:directions (edit gone "wow!" 9000)))))))
    (testing "then it is dropped"
      (is (= [] (:directions (director/direct gone 10030)))))))

;; --- budget -----------------------------------------------------------------

(deftest the-spectacle-budget
  (let [d (typed "wow! " 0)]                ; the card is at 30
    (testing "a ? 9999 ms after a card stays in the line"
      (is (= :line (:shot (peek (:directions (edit d "wow! why?" 10029))))))
      (is (= 0.2 (:energy (peek (:directions (edit d "wow! why?" 10029)))))))
    (testing "10000 ms after it is a push"
      (is (= :push (:shot (peek (:directions (edit d "wow! why?" 10030))))))))
  (testing "a paste of two ! sentences gives one card"
    (is (= [:card :line] (mapv :shot (:directions (edit (draft/empty-draft) "yes! yes! " 0))))))
  (testing ":line costs nothing"
    (is (= [:line :card] (mapv :shot (:directions (typed "so. wow! " 0)))))))

;; --- properties -------------------------------------------------------------

(def ^:private gen-step
  "One edit: insert a chunk at a place, or delete a span; and a gap in ms."
  (gen/tuple (gen/one-of [(gen/tuple (gen/return :insert)
                                     (gen/fmap str/join
                                               (gen/vector (gen/elements ["a" "B" "C" " " " " "!" "?" "." "\n"
                                                                          "WOW" "why" "x."])
                                                           1 6)))
                          (gen/tuple (gen/return :delete) (gen/choose 1 6))])
             gen/nat
             (gen/elements [0 10 90 400 1300 3000 9999 10000])))

(defn- apply-step [{:keys [text] :as d} t [[kind arg] at _]]
  (let [n (count text)
        at (mod at (inc n))]
    (edit d (case kind
              :insert (str (subs text 0 at) arg (subs text at))
              :delete (str (subs text 0 at) (subs text (min n (+ at arg)))))
          t)))

(defn- drafts-along
  "Every draft along steps, each followed by a frame-time direct (as the
  shell does between edits)."
  [steps]
  (:out (reduce (fn [{:keys [d t out]} [_ _ gap :as step]]
                  (let [t (+ t gap)
                        d (apply-step d t step)
                        later (director/direct d (+ t (quot gap 2)))]
                    {:d later :t (+ t (quot gap 2)) :out (conj out d later)}))
                {:d (draft/empty-draft) :t 0 :out []}
                steps)))

(defn- spectacle-times [d]
  (map :at-ms (remove #(= :line (:shot %)) (:directions d))))

(defn- apart? [times]
  (every? (fn [[a b]] (>= (- b a) 10000)) (partition 2 1 (sort times))))

(defspec spectacles-are-at-least-ten-seconds-apart 200
  (prop/for-all [steps (gen/vector gen-step 1 40)]
    (let [ds (drafts-along steps)]
      (and (every? (comp apart? spectacle-times) ds)
           (apart? (distinct (mapcat spectacle-times ds)))))))

(defspec directed-drafts-are-valid 200
  (prop/for-all [steps (gen/vector gen-step 1 30)]
    (every? #(schema/valid? :ha/draft %) (drafts-along steps))))

(defspec directions-are-on-present-glyphs-or-spent-budget 200
  (prop/for-all [steps (gen/vector gen-step 1 30)]
    (let [[_ t] (reduce (fn [[_ t] [_ _ gap]] [nil (+ t gap (quot gap 2))]) [nil 0] steps)
          d (peek (drafts-along steps))
          live (into #{} (map :id) (:glyphs d))]
      (every? (fn [{:keys [glyph-ids shot at-ms]}]
                (if (every? live glyph-ids)
                  true
                  (and (not-any? live glyph-ids) (not= :line shot) (< (- t at-ms) 10000))))
              (:directions d)))))
