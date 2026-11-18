(ns halfahead.lab.flick-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [halfahead.lab.flick :as flick]
            [halfahead.lab.learn :as learn]
            [halfahead.lab.reading :as reading]
            [halfahead.lab.style :as style]))

;; --- helpers ----------------------------------------------------------------

(def ^:private axes [:energy :flourish :softness :warmth])

(defn- point [e f s w] {:energy e :flourish f :softness s :warmth w})

(def ^:private centre (point 0.5 0.5 0.5 0.5))

(def ^:private pace {:wpm 300})

;; no tells: no mark, so no gesture and no dress at any point
(def ^:private bare-line "hello there")

;; a turn, a doubt and a landing, and no dress at any point
(def ^:private marked-line "Fine. But maybe not.")

(def ^:private plainly (style/params :plain))

(defn- plan
  "The marked line's plan at p, played plainly whatever p is, so that two
  of them differ only in what the test changes."
  [p]
  (reading/plan marked-line p plainly pace))

(defn- with-params [plan k v] (assoc-in plan [:params k] v))

(defn- axes-apart [p q] (filterv #(not= (p %) (q %)) axes))

(defn- contiguous-from-0? [phases]
  (and (= [:a :gap :b :choose] (mapv :phase phases))
       (zero? (:at (first phases)))
       (every? (fn [[{:keys [at ms]} then]] (= (+ at ms) (:at then)))
               (partition 2 1 phases))))

;; --- distinct? ---------------------------------------------------------------

(deftest distinct-in-each-way
  (let [here (plan centre)]
    (testing "the marks performing differ"
      (let [spare (plan (assoc centre :flourish 0.0))]
        (is (= 3 (count (:performing here))))
        (is (empty? (:performing spare)))
        (is (= (:dress (:score here)) (:dress (:score spare))))
        (is (flick/distinct? here spare))
        (is (flick/distinct? spare here))))
    (testing "the same marks perform with another gesture"
      (let [lively (plan (assoc centre :energy 0.75))]
        (is (= (mapv :mark (:performing here)) (mapv :mark (:performing lively))))
        (is (not= (mapv :effect (:performing here)) (mapv :effect (:performing lively))))
        (is (flick/distinct? here lively))))
    (testing "the dress differs"
      (is (flick/distinct? here (assoc-in here [:score :dress] [{:span [0 4] :as :strong}]))))
    (testing "the tempo differs by a tenth"
      (is (flick/distinct? here (with-params here :tempo 0.8)))
      (is (flick/distinct? (with-params here :tempo 0.8) here)))
    (testing "the amplitude differs by a tenth"
      (is (flick/distinct? here (with-params here :amplitude 1.25))))
    (testing "the overshoot differs by 0.2"
      (is (flick/distinct? here (with-params here :overshoot 0.5)))
      (is (flick/distinct? here (with-params here :overshoot 1.25))))))

(deftest not-distinct
  (let [here (plan centre)]
    (is (not (flick/distinct? here here)))
    (testing "motion closer than can be told apart"
      (is (not (flick/distinct? here (with-params here :tempo 0.95))))
      (is (not (flick/distinct? here (with-params here :amplitude 1.05))))
      (is (not (flick/distinct? here (with-params here :overshoot 1.125))))
      (is (not (flick/distinct? here (-> here
                                         (with-params :tempo 1.05)
                                         (with-params :amplitude 0.95)
                                         (with-params :overshoot 0.875))))))
    (testing "a style that differs only where this line shows nothing"
      (is (not (flick/distinct? here (plan (assoc centre :warmth 1.0))))))))

;; --- offer -------------------------------------------------------------------

(deftest offer-shape
  (let [s (learn/start)
        {:keys [a b]} (learn/pair s)
        o (flick/offer s bare-line pace)]
    (is (= #{:state :axis :a :b :point-a :point-b} (set (keys o))))
    (is (= s (:state o)))
    (is (= :energy (:axis o)))
    (is (= a (:point-a o)))
    (is (= b (:point-b o)))
    (testing "each plan is the reading the lab plays a learned point with"
      (is (= (reading/plan bare-line a (style/from-point a) pace) (:a o)))
      (is (= (reading/plan bare-line b (style/from-point b) pace) (:b o))))
    (is (flick/distinct? (:a o) (:b o)))))

(deftest offer-skips-a-silent-axis
  (let [s (learn/skip (learn/start))
        {:keys [axis a b]} (learn/pair s)
        plan-of #(reading/plan bare-line % (style/from-point %) pace)
        o (flick/offer s bare-line pace)]
    (testing "flourish shows nothing on a line with no marks"
      (is (= :flourish axis))
      (is (not (flick/distinct? (plan-of a) (plan-of b)))))
    (is (= :softness (:axis o)))
    (is (= (learn/skip s) (:state o)))
    (is (= 0 (:flicks (:state o))))
    (is (= (:point s) (:point-a o)))
    (is (= [:softness] (axes-apart (:point-a o) (:point-b o))))
    (testing "from warmth, which a bare line does not show either, it comes round to energy"
      (let [o (flick/offer (assoc (learn/start) :axis :warmth) bare-line pace)]
        (is (= :energy (:axis o)))
        (is (= (learn/start) (:state o)))))))

(deftest offer-nil
  (testing "text that is not a message"
    (is (nil? (flick/offer (learn/start) "" pace)))
    (is (nil? (flick/offer (learn/start) nil pace))))
  (testing "no axis gives a pair that can be told apart"
    (let [small (assoc (learn/start) :step (zipmap axes (repeat 0.015625)))]
      (is (nil? (flick/offer small bare-line pace)))
      (is (some? (flick/offer (assoc-in small [:step :softness] 0.25) bare-line pace))))))

;; --- phases, phase-at --------------------------------------------------------

(deftest phases-timeline
  (testing "a bare line: two words at 300 wpm are 400 ms"
    (is (= [{:phase :a :at 0 :ms 700}
            {:phase :gap :at 700 :ms 400}
            {:phase :b :at 1100 :ms 700}
            {:phase :choose :at 1800}]
           (flick/phases (flick/offer (learn/start) bare-line pace)))))
  (testing "each reading is given its own length, cues and all, and 300 ms"
    (let [o (flick/offer (learn/start) marked-line pace)
          da (+ 300 (:duration-ms (:reading (:a o))))
          db (+ 300 (:duration-ms (:reading (:b o))))
          ps (flick/phases o)]
      (is (= 2220 da) "the doubt's lean ends 1920 ms in, after the head has crossed")
      (is (not= da db) "B is livelier, so its gestures are shorter")
      (is (= [{:phase :a :at 0 :ms da}
              {:phase :gap :at da :ms 400}
              {:phase :b :at (+ da 400) :ms db}
              {:phase :choose :at (+ da 400 db)}]
             ps))
      (is (contiguous-from-0? ps)))))

(deftest phase-at-each
  (let [ps (flick/phases (flick/offer (learn/start) bare-line pace))]
    (is (= [:a :a :a :gap :gap :b :b :choose :choose]
           (mapv #(flick/phase-at ps %) [-50 0 699 700 1099 1100 1799 1800 1000000])))))

;; --- decide ------------------------------------------------------------------

(deftest decide-is-choose
  (let [o (flick/offer (learn/skip (learn/start)) bare-line pace)]
    (is (= (learn/choose (:state o) :a) (flick/decide o :a)))
    (is (= (learn/choose (:state o) :b) (flick/decide o :b)))
    (is (= (:point-a o) (:point (flick/decide o :a))))
    (is (= (:point-b o) (:point (flick/decide o :b))))
    (testing "only the flick shown is counted, not the axis skipped"
      (is (= 1 (:flicks (flick/decide o :b))))
      (is (= :warmth (:axis (flick/decide o :b)))))
    (is (= :ha.learn/unknown-choice
           (try (flick/decide o :c)
                (catch #?(:clj Exception :cljs :default) e (:type (ex-data e))))))))

;; --- property ----------------------------------------------------------------

(def ^:private gen-unit (gen/double* {:min 0.0 :max 1.0 :NaN? false :infinite? false}))

(def ^:private gen-point (gen/fmap #(apply point %) (gen/vector gen-unit 4)))

(def ^:private gen-learn-state
  (gen/let [p gen-point
            steps (gen/vector (gen/elements [:a :b :skip]) 0 40)]
    (reduce (fn [s step] (if (= :skip step) (learn/skip s) (learn/choose s step)))
            (learn/start p)
            steps)))

(def ^:private gen-text
  (gen/one-of
   [gen/string
    ;; words and punctuation, so that marks and dress turn up
    (gen/fmap #(str/join " " %)
              (gen/vector (gen/elements ["I" "loooove" "this" "(honestly)." "But" "maybe"
                                         "not." "Ship" "it!" "NEVER" "so..." "really?"
                                         "*now*" "fine," "hello" "there"])
                          0 16))]))

(defspec offer-is-nil-or-distinct 100
  (prop/for-all [text gen-text
                 s gen-learn-state
                 wpm (gen/choose 100 800)]
    (let [{:keys [state axis a b point-a point-b] :as o} (flick/offer s text {:wpm wpm})]
      (or (nil? o)
          (and (flick/distinct? a b)
               (= [axis] (axes-apart point-a point-b))
               (= axis (:axis state))
               (= (:point s) (:point state) point-a)
               (= (dissoc s :axis) (dissoc state :axis))
               (contiguous-from-0? (flick/phases o)))))))
