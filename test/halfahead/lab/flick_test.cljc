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

(deftest distinct-at-the-edges
  (let [here (plan centre)
        both-ways (fn [k v]
                    (let [there (with-params here k v)]
                      [(flick/distinct? here there) (flick/distinct? there here)]))]
    (is (= {:tempo 1.0 :amplitude 1.0 :overshoot 1.0}
           (select-keys (:params here) [:tempo :amplitude :overshoot])))
    (testing "the thresholds are inclusive, whatever the rounding"
      (is (= [true true] (both-ways :tempo 0.9)))
      (is (= [true true] (both-ways :amplitude 1.1)))
      (is (= [true true] (both-ways :overshoot 0.8)))
      (is (= [true true] (both-ways :overshoot 1.2))))
    (testing "and just inside them is not distinct"
      (is (= [false false] (both-ways :tempo 0.91)))
      (is (= [false false] (both-ways :amplitude 1.09)))
      (is (= [false false] (both-ways :overshoot 0.81)))
      (is (= [false false] (both-ways :overshoot 1.19))))))

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

(defn- plan-of [text p] (reading/plan text p (style/from-point p) pace))

(deftest offer-asks-the-other-way
  (let [first-offer (flick/offer (learn/start) marked-line pace)
        s (flick/decide first-offer :a)
        o (flick/offer s marked-line pace)]
    (is (= :energy (:axis first-offer)))
    (is (= :flourish (:axis s)))
    (testing "upward, flourish shows nothing on this line from the centre"
      (let [{:keys [a b]} (learn/pair s)]
        (is (= 0.75 (:flourish b)))
        (is (not (flick/distinct? (plan-of marked-line a) (plan-of marked-line b))))))
    (testing "so it is offered downward"
      (is (= :flourish (:axis o)))
      (is (< (:flourish (:point-b o)) (:flourish (:point-a o))))
      (is (= 0.25 (:flourish (:point-b o))))
      (is (= (learn/turn s) (:state o)))
      (is (= 1 (:flicks (:state o))))
      (is (= (:b (learn/pair (:state o))) (:point-b o)))
      (is (= (plan-of marked-line (:point-b o)) (:b o))))
    (testing "straight from the start, too"
      (let [s (learn/skip (learn/start))
            o (flick/offer s marked-line pace)]
        (is (= :flourish (:axis o)))
        (is (< (:flourish (:point-b o)) (:flourish (:point-a o))))
        (is (= (learn/turn s) (:state o)))))))

(deftest a-finished-axis-is-not-offered
  ;; from energy 0.59375 the marked line changes gesture a thirty-second
  ;; up, so only the size of the step keeps energy from being offered
  (let [near (learn/start (assoc centre :energy 0.59375))
        finished (assoc-in near [:step :energy] 0.03125)]
    (testing "the pair itself could be told apart"
      (let [{:keys [a b]} (learn/pair finished)]
        (is (= 0.625 (:energy b)))
        (is (flick/distinct? (plan-of marked-line a) (plan-of marked-line b)))))
    (testing "but a step below 1/16 is passed over"
      (let [o (flick/offer finished marked-line pace)]
        (is (= :flourish (:axis o)))
        (is (= 0 (:flicks (:state o))))))
    (testing "and never comes back"
      (let [axes-offered (->> (iterate (fn [s]
                                         (when-let [o (and s (flick/offer s marked-line pace))]
                                           (flick/decide o :a)))
                                       finished)
                              (take 12)
                              (take-while some?)
                              (keep #(:axis (flick/offer % marked-line pace))))]
        (is (seq axes-offered))
        (is (not-any? #{:energy} axes-offered))))
    (testing "a step of 1/16 is not yet finished"
      (let [o (flick/offer (assoc-in near [:step :energy] 0.0625) marked-line pace)]
        (is (= :energy (:axis o)))
        (is (= 0.65625 (:energy (:point-b o))))))
    (testing "on a bare line too, which shows energy at the first step"
      (is (= :energy (:axis (flick/offer (learn/start) bare-line pace))))
      (is (= :softness
             (:axis (flick/offer (assoc-in (learn/start) [:step :energy] 0.03125)
                                 bare-line pace)))))))

;; --- the exam ends -----------------------------------------------------------

(def ^:private exam-lines
  ["ok" "Merged." "Sounds good." "Thanks!" "We shipped it."
   "It works on my machine (of course it does)."
   "That is *the one thing* that matters (trust me)."
   "sooo tired" "Nooo, not the database again!"
   "WHAT A DAY!" "DO NOT DEPLOY ON FRIDAY."
   "Fine. But maybe not."])

(defn- flicks-until-nil
  "How many exams a writer choosing by pick sits on text before none is
  offered; cap + 1 if there is still one after cap."
  [text pick cap]
  (loop [s (learn/start), n 0]
    (let [o (flick/offer s text pace)]
      (if (or (nil? o) (> n cap))
        n
        (recur (flick/decide o (pick o)) (inc n))))))

(defn- lower-energy [{:keys [point-a point-b]}]
  (if (< (:energy point-b) (:energy point-a)) :b :a))

(deftest the-exam-ends
  (doseq [line exam-lines]
    (testing line
      (is (some? (flick/offer (learn/start) line pace)) "there is an exam to end")
      (testing "for a writer who always chooses :a"
        (is (<= 1 (flicks-until-nil line (constantly :a) 24) 24)))
      (testing "for a writer who always picks the lower energy"
        (is (<= 1 (flicks-until-nil line lower-energy 30) 30))))))

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
            steps (gen/vector (gen/elements [:a :b :skip :turn]) 0 40)]
    (reduce (fn [s step]
              (case step
                :skip (learn/skip s)
                :turn (learn/turn s)
                (learn/choose s step)))
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
               (= (dissoc s :axis :dir) (dissoc state :axis :dir))
               (= (dissoc (:dir s) axis) (dissoc (:dir state) axis))
               (>= (learn/step state) 0.0625)
               (contiguous-from-0? (flick/phases o)))))))
