(ns halfahead.lab.reveal-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [halfahead.lab.cues :as cues]
            [halfahead.lab.director :as director]
            [halfahead.lab.draft :as draft]
            [halfahead.lab.reveal :as reveal]))

(defn- typed
  "draft after typing s onto d, one character every step ms from t0."
  ([s t0 step] (typed (draft/empty-draft) s t0 step))
  ([d s t0 step]
   (reduce (fn [d [i k]] (draft/edit d (str (:text d) (subs s i (inc i))) (+ t0 (* step k)) cues/word-cue))
           d
           (map vector (range (count s)) (range)))))

(def ^:private flair "one two three deploy crash merge finally ")

(deftest levels-from-usage
  (testing "level 1 when the third word is finished"
    (is (= [0 nil nil nil] (reveal/levels (typed "one two " 0 100) 9000 {})))
    (is (= [0 1300 nil nil] (reveal/levels (typed "one two three " 0 100) 9000 {}))
        "the space after \"three\" is the 14th character, at 1300 ms")
    (is (= [0 nil nil nil] (reveal/levels (typed "one two three " 0 100) 1299 {}))
        "not before it happened"))
  (testing "level 2 at the fourth cue that is not the quiet default"
    (is (= [0 1300 4000 nil] (reveal/levels (typed flair 0 100) 9000 {})))
    (is (= [0 1300 nil nil]
           (reveal/levels (typed "one two three four five six seven eight " 0 100) 9000 {}))
        "default cues never count")
    (is (= [0 1300 nil nil] (reveal/levels (typed "one two three deploy crash merge " 0 100) 9000 {}))
        "three are not enough")))

(defn- card [d at ids]
  (update d :directions (fnil conj []) {:glyph-ids ids :shot :card :energy 0.9 :at-ms at :source :rules}))

(deftest level-3-opens-with-a-new-spectacle
  (let [d (typed flair 0 100)                              ; t2 = 4000, 41 glyphs born by then
        d (typed d "WOW! " 5000 100)                        ; ids 41..45, born 5000..5400
        new-ids [41 42 43 44]]
    (testing "a card placed after level 2 on letters written since"
      (is (= [0 1300 4000 5400] (reveal/levels (card d 5400 new-ids) 9000 {}))))
    (testing "a card on old and new letters counts too"
      (is (= 5400 (peek (reveal/levels (card d 5400 (into [0 1 2] new-ids)) 9000 {})))))
    (testing "but not a :line"
      (is (nil? (peek (reveal/levels (assoc-in (card d 5400 new-ids) [:directions 0 :shot] :line) 9000 {})))))
    (testing "nor a shot placed before level 2"
      (is (nil? (peek (reveal/levels (card d 3900 new-ids) 9000 {})))))
    (testing "nor one on letters all written before level 2 (the director's first pass)"
      (is (nil? (peek (reveal/levels (card d 4000 [0 1 2]) 9000 {})))))
    (testing "the director's own card does it"
      (let [directed (director/direct d 5400)]
        (is (= :card (:shot (peek (:directions directed)))))
        (is (= [0 1300 4000 5400] (reveal/levels directed 9000 {})))))))

(deftest wands
  (let [quiet (draft/empty-draft)]
    (testing "each wand raises the level by one, up to 3"
      (is (= [0 100 nil nil] (reveal/levels quiet 9000 {:wands [100]})))
      (is (= [0 100 200 300] (reveal/levels quiet 9000 {:wands [100 200 300 400 500]}))))
    (testing "wands after t do not count yet"
      (is (= [0 100 nil nil] (reveal/levels quiet 150 {:wands [100 200]}))))
    (testing "a wand and then use do not count twice"
      (let [d (typed "one two three " 0 100)]
        (is (= [0 50 nil nil] (reveal/levels d 9000 {:wands [50]})))
        (is (= [0 1300 2000 nil] (reveal/levels d 9000 {:wands [2000]}))
            "a wand after use raises from the level use reached")
        (is (= [0 1300 1300 nil] (reveal/levels d 9000 {:wands [1300]}))
            "at the same instant, the wand still adds one")))))

(deftest start-level
  (is (= [0 0 0 nil] (reveal/levels (draft/empty-draft) 0 {:start-level 2})))
  (is (= {:level 2 :lights 1.0 :entered-ms 0 :reached [0 0 0 nil]}
         (reveal/state (draft/empty-draft) 0 {:start-level 2}))
      "started at level 2, the lights are down with no ease")
  (is (= 3 (:level (reveal/state (draft/empty-draft) -500 {:start-level 3})))
      "even before time 0")
  (is (= [0 0 0 0] (reveal/levels (draft/empty-draft) 100 {:start-level 7 :wands [50]}))
      "clamped to 3"))

(deftest light-easing
  (let [d (typed flair 0 100)                    ; level 2 at 4000
        lights #(:lights (reveal/state d % {}))]
    (is (= 0.0 (lights 3999)) "lights up at level 1")
    (is (= 0.0 (lights 4000)) "exactly up as level 2 is entered")
    (is (< 0.0 (lights 4300) (lights 4450) (lights 4600) 1.0) "going down")
    (is (= 1.0 (lights 4900)) "down by 900 ms")
    (is (= 1.0 (lights 9000)))
    (testing "level 3 reached inside the ease carries on easing"
      (let [d3 (card (typed d "WOW! " 4100 50) 4300 [41])]
        (is (= 3 (:level (reveal/state d3 4400 {}))))
        (is (= (lights 4400) (:lights (reveal/state d3 4400 {}))))
        (is (= 1.0 (:lights (reveal/state d3 4900 {}))))))))

(deftest a-wand-builds-on-levels-already-reached
  (let [d (typed flair 0 100)
        reached (reveal/levels d 5000 {})
        gone (draft/edit d "" 5000 cues/word-cue)]
    (is (= [0 1300 4000 nil] (reveal/levels gone 5000 {:reached reached}))
        "deleting every word keeps level 2")
    (is (= [0 1300 4000 6000] (reveal/levels gone 7000 {:reached reached :wands [6000]}))
        "and a wand then opens level 3")))

;; --- monotonicity -------------------------------------------------------------

(def ^:private gen-step
  "One thing the writer does: type a word and a space, delete characters,
  or wave a wand; each after some ms."
  (gen/tuple (gen/frequency [[5 (gen/fmap (fn [w] [:type (str w " ")])
                                          (gen/elements ["one" "deploy" "crash" "merge" "finally" "WOW!"
                                                         "why?" "so." "WHAT" "fine" "1234" "a"]))]
                             [2 (gen/fmap (fn [n] [:delete n]) (gen/choose 1 12))]
                             [1 (gen/return [:wand])]])
             (gen/choose 0 1500)))

(defspec levels-never-go-down 150
  (prop/for-all [start (gen/choose 0 3)
                 steps (gen/vector gen-step 1 30)]
    (loop [steps steps, t 0, d (draft/empty-draft), wands [], reached nil, prev-level -1, ok? true]
      (if-let [[[kind arg] dt] (first steps)]
        (let [t (+ t dt)
              [d wands] (case kind
                          :type [(draft/edit d (str (:text d) arg) t cues/word-cue) wands]
                          :delete [(draft/edit d (subs (:text d) 0 (max 0 (- (count (:text d)) arg))) t cues/word-cue)
                                   wands]
                          :wand [d (conj wands t)])
              d (assoc d :directions (:directions d []))
              opts {:start-level start :wands wands :reached reached}
              before (reveal/levels d t opts)
              d (if (>= (reveal/level-at before opts t) 2) (director/direct d t) d)
              opts (assoc opts :reached before)
              after (reveal/levels d t opts)
              {:keys [level lights]} (reveal/state d t (assoc opts :reached after))]
          (recur (rest steps) t d wands after level
                 (and ok?
                      (>= level prev-level)
                      (<= 0.0 lights 1.0)
                      (= level (reveal/level-at after opts t))
                      ;; a time once reached never changes
                      (every? true? (map (fn [a b] (or (nil? a) (= a b))) (or reached [nil nil nil nil]) after))
                      ;; reaching a level reaches every one below it, no later
                      (every? true? (map (fn [a b] (or (nil? b) (and a (<= a b)))) after (rest after))))))
        ok?))))

;; --- the wand by mouse ------------------------------------------------------------

(defn- sweep
  "Samples every 16 ms moving dx px in total over ms."
  [dx ms]
  (mapv (fn [k] {:x (* dx (/ k (quot ms 16))) :y 300 :t (* 16 k)}) (range (inc (quot ms 16)))))

(deftest flicks
  (testing "fast and far is a flick"
    (is (reveal/flick? (sweep 600 200)))
    (is (reveal/flick? (sweep -800 160)) "either way")
    (is (reveal/flick? [{:x 0 :y 0 :t 0} {:x 480 :y 360 :t 120}]) "600 px on the diagonal"))
  (testing "slow or short is not"
    (is (not (reveal/flick? (sweep 600 320))) "600 px in 320 ms")
    (is (not (reveal/flick? (sweep 300 100))) "only 300 px")
    (is (not (reveal/flick? [{:x 0 :y 0 :t 0} {:x 600 :y 0 :t 250}])) "250 ms is not under 250"))
  (testing "too few samples"
    (is (not (reveal/flick? [])))
    (is (not (reveal/flick? [{:x 0 :y 0 :t 0}]))))
  (testing "a fast burst inside slow pointing is one"
    (let [slow (mapv (fn [k] {:x (* 2 k) :y 0 :t (* 16 k)}) (range 30))
          t0 (:t (peek slow)), x0 (:x (peek slow))
          burst (mapv (fn [k] {:x (+ x0 (* 70 k)) :y 0 :t (+ t0 (* 16 k))}) (range 1 11))]
      (is (not (reveal/flick? slow)))
      (is (reveal/flick? (into slow burst))))))
