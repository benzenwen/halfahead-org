(ns halfahead.lab.debug-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [halfahead.lab.debug :as debug]
            [halfahead.lab.draft :as draft]))

;; --- helpers ----------------------------------------------------------------

(defn- round [x] (Math/round (double x)))

(defn- record-all [stats times]
  (reduce debug/record stats times))

(defn- steady
  "n frame times every period ms from start, rounded as the lab's clock is."
  [start period n]
  (map #(+ start (round (* % period))) (range n)))

(defn- near? [want got tolerance]
  (<= (Math/abs (- (double want) (double got))) tolerance))

(defn- cue-fn [word _following]
  {:word word :effect :launch :intensity 0.9 :source :glossary})

(defn- type-out
  "[draft new-cues-per-edit] after typing text one unit at a time, the k-th
  at ms k."
  [d text]
  (reduce (fn [[d found] k]
            (let [d' (draft/edit d (subs text 0 k) k cue-fn)]
              [d' (into found (debug/new-cues d d'))]))
          [d []]
          (range 1 (inc (count text)))))

;; --- timing -----------------------------------------------------------------

(deftest steady-60-hz
  (let [s (debug/summary (record-all nil (steady 0 (/ 1000.0 60) 120)))]
    (is (near? 60 (:fps s) 1))
    (is (near? 16.7 (:mean-ms s) 0.2))
    (is (= 120 (:frame s)))))

(defspec steady-period-gives-its-rate 100
  (prop/for-all [period (gen/choose 8 50)
                 n (gen/choose 3 200)]
    (let [s (debug/summary (record-all nil (steady 1000 period n)))]
      (and (near? (/ 1000.0 period) (:fps s) 0.01)
           (near? period (:mean-ms s) 0.01)
           (>= (:worst-ms s) (:mean-ms s))))))

(deftest one-long-gap-is-the-worst
  (let [before (steady 0 (/ 1000.0 60) 31)          ; ends at 500
        after (steady 550 (/ 1000.0 60) 20)
        s (debug/summary (record-all nil (concat before after)))]
    (is (== 50 (:worst-ms s)))
    (is (> (:mean-ms s) 16.7))))

(deftest fewer-than-two-frames-gives-zeros
  (doseq [stats [nil (debug/record nil 1234)]]
    (let [s (debug/summary stats)]
      (is (== 0 (:fps s) (:mean-ms s) (:worst-ms s))))
    (is (= 0 (:frame (debug/summary nil))))
    (is (= 1 (:frame (debug/summary (debug/record nil 1234)))))))

(deftest the-window-is-the-last-second
  (let [stats (record-all nil (steady 0 (/ 1000.0 60) 181))
        times (:times stats)
        now (peek times)]
    (is (= 181 (:frames stats)))
    (is (every? #(<= (- now 1000) % now) times))
    (is (<= 60 (count times) 61))))

;; --- new cues ---------------------------------------------------------------

(deftest typing-finishes-one-cue-per-word
  (let [[_ found] (type-out (draft/empty-draft) "we shipped it ")]
    (is (= ["we" "shipped" "it"] (mapv :word found)))
    (is (every? #(= #{:word :effect :intensity :source :at-ms} (set (keys %))) found))
    (is (= {:word "shipped" :effect :launch :intensity 0.9 :source :glossary :at-ms 11}
           (second found)))))

(deftest edits-that-place-no-cue
  (let [[d _] (type-out (draft/empty-draft) "we shipped it ")]
    (testing "an edit that finishes no word"
      (is (= [] (debug/new-cues d (draft/edit d "we shipped it n" 100 cue-fn)))))
    (testing "deleting a letter of a cued word shrinks its cue; nothing new"
      (let [d' (draft/edit d "we shiped it " 100 cue-fn)]
        (is (< (count (:glyph-ids (second (:cues d'))))
               (count (:glyph-ids (second (:cues d))))))
        (is (= [] (debug/new-cues d d')))))
    (testing "the same draft"
      (is (= [] (debug/new-cues d d))))))

(deftest a-paste-gives-every-word-in-order
  (let [d (draft/empty-draft)]
    (is (= ["ship" "it"]
           (mapv :word (debug/new-cues d (draft/edit d "ship it " 5 cue-fn)))))))

;; --- cue log ----------------------------------------------------------------

(defn- entry [word at-ms]
  {:word word :effect :pop :intensity 0.5 :source :default :at-ms at-ms})

(deftest the-log-keeps-six-newest-first
  (let [log (reduce (fn [log k] (debug/cue-log log [(entry (str "w" k) k)] k))
                    []
                    (range 10))]
    (is (= ["w9" "w8" "w7" "w6" "w5" "w4"] (mapv :word log))))
  (testing "several cues from one edit: the last in text order is newest"
    (is (= ["b" "a"] (mapv :word (debug/cue-log [] [(entry "a" 0) (entry "b" 0)] 0))))))

(deftest the-log-drops-entries-over-eight-seconds-old
  (let [log [(entry "kept" 2000) (entry "dropped" 1999)]]
    (is (= ["kept"] (mapv :word (debug/cue-log log [] 10000)))))
  (is (= [] (debug/cue-log [(entry "old" 0)] [] 9000))))

(defspec the-log-is-short-and-newest-first 100
  (prop/for-all [batches (gen/vector (gen/vector (gen/return nil) 0 4) 0 30)
                 step (gen/choose 0 3000)]
    (let [log (reduce (fn [log [k batch]]
                        (let [t (* k step)]
                          (debug/cue-log log (mapv (fn [_] (entry "w" t)) batch) t)))
                      []
                      (map-indexed vector batches))]
      (and (<= (count log) 6)
           (or (empty? log) (apply >= (map :at-ms log)))))))

;; --- overlay ----------------------------------------------------------------

(deftest the-overlay-reads-like-the-sketch
  (let [lines (str/split-lines
               (debug/overlay-text
                {:summary {:frame 1234 :fps 59.8 :mean-ms 16.62 :worst-ms 18}
                 :t-ms 12345 :backend "WebGPU" :glyphs 36
                 :style :caffeinated :style-choice :auto
                 :log [(entry "shipped" 12000)
                       {:word "shipped" :effect :launch :intensity 1.0
                        :source :glossary :at-ms 12000}
                       {:word "deploy" :effect :launch :intensity 0.9
                        :source :glossary :at-ms 11000}]}))]
    (is (= "frame 1234 · t 12.345 s · 60 fps · mean 16.6 ms · worst 18.0 ms" (first lines)))
    (is (= "WebGPU · 36 glyphs · style caffeinated (auto)" (second lines)))
    (is (= ["shipped -> pop 0.5 (default)"
            "shipped -> launch 1.0 (glossary)"
            "deploy -> launch 0.9 (glossary)"]
           (drop 2 lines)))))

(deftest the-overlay-renders-before-any-timing
  (is (= "frame 0 · t 0.005 s · 0 fps · mean 0.0 ms · worst 0.0 ms\nWebGL2 · 0 glyphs · style plain"
         (debug/overlay-text {:summary (debug/summary nil) :t-ms 5
                              :backend "WebGL2" :glyphs 0
                              :style :plain :style-choice :plain :log []}))))

(deftest overlay-names-the-style
  (let [backend-line #(second (str/split-lines
                               (debug/overlay-text {:summary (debug/summary nil) :t-ms 0
                                                    :backend "WebGL2" :glyphs 3
                                                    :style %1 :style-choice %2 :log []})))]
    (testing "a style the writer picked"
      (is (= "WebGL2 · 3 glyphs · style elegant" (backend-line :elegant :elegant))))
    (testing "a style inferred from the rhythm says so"
      (is (= "WebGL2 · 3 glyphs · style drunken (auto)" (backend-line :drunken :auto))))))
