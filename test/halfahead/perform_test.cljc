(ns halfahead.perform-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [halfahead.perform :as perform]
            [halfahead.schema :as schema]))

;; --- generators -------------------------------------------------------------

(def gen-token
  (gen/elements ["a" "word" "remarkable" "héllo" "😀" "👨‍👩‍👧" "日本語" "\n" " " "  "
                 "\t" ". " "! " "? " ", "]))

(def gen-text
  (gen/such-that seq (gen/fmap str/join (gen/vector gen-token 1 30))))

(def gen-delivery
  (gen/elements [:plain :typed :headline :aside :credits :fireworks nil]))

(def gen-input
  (gen/bind gen-text
            (fn [text]
              (let [n (count text)
                    idx (gen/choose -5 (+ n 5))]
                (gen/hash-map :text (gen/return text)
                              :delivery gen-delivery
                              :emphasis (gen/vector (gen/tuple idx idx) 0 6)
                              :seed gen/large-integer
                              :reduced-motion? gen/boolean)))))

(defn- valid-timeline? [{:keys [text]} tl]
  (let [beats (:beats tl)
        spans (map :span beats)]
    (and (schema/valid? :ha/timeline tl)
         (= 0 (ffirst spans))
         (= (count text) (second (last spans)))
         (every? (fn [[[_ a-to] [b-from _]]] (= a-to b-from)) (partition 2 1 spans))
         (every? #(<= (+ (:at-ms %) (:ms %)) (:duration-ms tl)) beats)
         (= (:duration-ms tl) (reduce max (map #(+ (:at-ms %) (:ms %)) beats))))))

(def gen-input-and-t
  (gen/tuple gen-input (gen/one-of [(gen/choose -100 10000) gen/large-integer])))

;; --- properties -------------------------------------------------------------

(defspec timeline-is-valid-and-partitions-the-text 300
  (prop/for-all [in gen-input]
    (valid-timeline? in (perform/perform in))))

(defspec final-frame-is-the-text 300
  (prop/for-all [in gen-input]
    (let [tl (perform/perform in)
          segs (perform/frame-at tl (:text in) (:duration-ms tl))]
      (and (= (:text in) (apply str (map :text segs)))
           (every? #(= 1.0 (:progress %)) segs)))))

(defspec layout-never-changes 300
  (prop/for-all [[in t] gen-input-and-t]
    (let [segs (perform/frame-at (perform/perform in) (:text in) t)]
      (and (= (:text in) (apply str (map :text segs)))
           (every? #(<= 0.0 (:progress %) 1.0) segs)))))

(defspec perform-is-deterministic 300
  (prop/for-all [in gen-input]
    (= (perform/perform in) (perform/perform (into {} in)))))

(defspec reduced-motion-is-instant 300
  (prop/for-all [in gen-input]
    (let [tl (perform/perform (assoc in :reduced-motion? true))
          segs (perform/frame-at tl (:text in) 0)]
      (and (= 0 (:duration-ms tl))
           (= 1 (count (:beats tl)))
           (= [{:text (:text in) :effect :appear :progress 1.0}] segs)))))

;; --- examples ---------------------------------------------------------------

(defn- input [text delivery & {:as more}]
  (merge {:text text :delivery delivery :emphasis [] :seed 1 :reduced-motion? false}
         more))

(defn- piece [text {[from to] :span}] (subs text from to))

(deftest plain-and-aside
  (is (= {:duration-ms 150
          :beats [{:span [0 5] :effect :appear :at-ms 0 :ms 150}]}
         (perform/perform (input "Hello" :plain))))
  (is (= {:duration-ms 600
          :beats [{:span [0 5] :effect :whisper :at-ms 0 :ms 600}]}
         (perform/perform (input "Hello" :aside)))))

(deftest typed-seeds
  (let [text "the quick brown fox jumps over lazy dogs"
        t1 (perform/perform (input text :typed :seed 1))
        t2 (perform/perform (input text :typed :seed 2))]
    (is (valid-timeline? (input text :typed) t1))
    (is (valid-timeline? (input text :typed) t2))
    (is (not= (map :ms (:beats t1)) (map :ms (:beats t2))))
    (is (every? #(<= (* 35 0.8 (- (second (:span %)) (first (:span %)))) (:ms %)
                     (* 35 1.2 (- (second (:span %)) (first (:span %))) 1.001))
                (:beats t1))
        "jitter stays within 20% of 35 ms per character")))

(deftest typed-long-text-is-capped
  (doseq [unit ["word " "😀 "]]
    (let [text (subs (str/join (repeat 4000 unit)) 0 4000)
          in (input text :typed)
          tl (perform/perform in)]
      (is (<= (:duration-ms tl) schema/max-performance-ms))
      (is (valid-timeline? in tl))
      (is (= (count (re-seq #"\S+" text)) (count (:beats tl)))))))

(deftest headline
  (testing "explicit emphasis slams first"
    (let [text "Hello world, friends"
          beats (:beats (perform/perform (input text :headline :emphasis [[6 11]])))
          slam (first (filter #(= :slam (:effect %)) beats))]
      (is (= "world" (piece text slam)))
      (is (= 0 (:at-ms slam)))
      (is (every? #(< (:at-ms slam) (:at-ms %)) (filter #(= :appear (:effect %)) beats)))
      (is (= ["Hello " "world" ", friends"] (map #(piece text %) beats)))))
  (testing "no emphasis slams the longest word"
    (let [text "a big remarkable day"
          beats (:beats (perform/perform (input text :headline)))]
      (is (= ["remarkable"] (map #(piece text %) (filter #(= :slam (:effect %)) beats))))))
  (testing "ties go to the first word; surrounding whitespace is not slammed"
    (let [text "  one two "
          beats (:beats (perform/perform (input text :headline)))]
      (is (= ["one"] (map #(piece text %) (filter #(= :slam (:effect %)) beats))))))
  (testing "emphasis is staggered and untrusted spans are cleaned"
    (let [text "aa bb cc dd"
          beats (:beats (perform/perform
                         (input text :headline :emphasis [[9 99] [0 2] [1 2] [5 5] [-3 1] [10 11]])))
          slams (filter #(= :slam (:effect %)) beats)]
      (is (= ["aa" "dd"] (map #(piece text %) slams)) "overlaps merge, empties drop")
      (is (= [0 250] (map :at-ms slams)))
      (is (= [550] (distinct (map :at-ms (filter #(= :appear (:effect %)) beats))))))))

(deftest credits
  (let [text "one\ntwo\nthree"
        beats (:beats (perform/perform (input text :credits)))]
    (is (= [:rise :rise :rise] (map :effect beats)))
    (is (= ["one\n" "two\n" "three"] (map #(piece text %) beats)))
    (is (= [0 400 800] (map :at-ms beats)))
    (is (= 1500 (:duration-ms (perform/perform (input text :credits))))))
  (testing "a single line splits at sentence ends"
    (let [text "Hi. Really? Yes! ok"
          beats (:beats (perform/perform (input text :credits)))]
      (is (= ["Hi. " "Really? " "Yes! " "ok"] (map #(piece text %) beats))))))

(deftest cap-keeps-every-beat
  (let [text (str/join "\n" (repeat 40 "line"))
        in (input text :credits)
        tl (perform/perform in)]
    (is (= 40 (count (:beats tl))))
    (is (<= (:duration-ms tl) schema/max-performance-ms))
    (is (valid-timeline? in tl))))

(deftest frame-progress
  (let [tl {:duration-ms 300
            :beats [{:span [0 2] :effect :slam :at-ms 100 :ms 200}
                    {:span [2 4] :effect :appear :at-ms 50 :ms 0}]}]
    (is (= [0.0 0.0] (map :progress (perform/frame-at tl "abcd" 0))))
    (is (= [0.0 1.0] (map :progress (perform/frame-at tl "abcd" 50))))
    (is (= [0.5 1.0] (map :progress (perform/frame-at tl "abcd" 200))))
    (is (= [1.0 1.0] (map :progress (perform/frame-at tl "abcd" 300))))
    (is (= ["ab" "cd"] (map :text (perform/frame-at tl "abcd" -5))))))

(deftest hosts-agree-on-the-prng
  ;; Pinned numbers: a drift in the LCG on either host fails here.
  (is (= [183 205 143 200 132]
         (map :ms (:beats (perform/perform (input "aaaa bbbb cccc dddd eeee" :typed :seed 7)))))
      "5-char words at 35 ms/char with seed 7"))
