(ns halfahead.lab.tour-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [halfahead.lab.tour :as tour]
            [halfahead.schema :as schema]))

(def ^:private small
  [{:caption "One."
    :steps [{:do :clear}
            {:do :type :text "abc" :ms-per-key 100}
            {:do :wait :ms 50}
            {:do :read-back :wpm 200}
            {:do :until-read}
            {:do :wait :ms 20}]}
   {:caption "Two."
    :steps [{:do :style :style :plain}
            {:do :light :over "b" :ms 500}
            {:do :reveal :level 2}]}])

(defn- steps-of [script] (mapcat :steps script))

(deftest plan-times-test
  (let [[a b :as segments] (tour/plan small)]
    (is (= 2 (count segments)))
    (testing "each step starts when the one before it ends"
      (is (= [[0 1 :clear] [0 1 :type] [300 1 :wait] [350 1 :read-back] [350 1 :until-read]]
             (mapv (juxt :at-ms :scene (comp :do :step)) a))))
    (testing "after :until-read the clock starts again, across scenes"
      (is (= [[0 1 :wait] [20 2 :style] [20 2 :light] [520 2 :reveal]]
             (mapv (juxt :at-ms :scene (comp :do :step)) b))))
    (is (= (steps-of small) (map :step (apply concat segments))))))

(deftest plan-of-a-script-ending-in-a-read-back-test
  (is (= [[{:at-ms 0 :scene 1 :step {:do :until-read}}]]
         (tour/plan [{:caption "x" :steps [{:do :until-read}]}]))))

(deftest keys-due-test
  (let [step {:do :type :text "abc" :ms-per-key 100}]
    (is (= "a" (tour/keys-due step 0)))
    (is (= "a" (tour/keys-due step 99)))
    (is (= "ab" (tour/keys-due step 100)))
    (is (= "ab" (tour/keys-due step 150)))
    (is (= "abc" (tour/keys-due step 200)))
    (is (= "abc" (tour/keys-due step 300)))
    (is (= "abc" (tour/keys-due step 100000)))))

(defspec keys-due-is-a-growing-prefix 100
  (prop/for-all [text (gen/not-empty gen/string-alphanumeric)
                 ms-per-key (gen/choose 1 200)
                 times (gen/vector (gen/choose 0 50000) 1 20)]
    (let [step {:do :type :text text :ms-per-key ms-per-key}
          typed (map #(tour/keys-due step %) (sort times))]
      (and (every? #(str/starts-with? text %) typed)
           (every? seq typed)
           (apply <= (map count typed))
           (= text (tour/keys-due step (* ms-per-key (count text))))))))

(deftest due-test
  (let [[a] (tour/plan small)]
    (testing "before anything lasts, the instant steps and the first key"
      (is (= [[0 :clear 0 true] [1 :type 0 false]]
             (mapv (juxt :index (comp :do :step) :elapsed-ms :over?) (tour/due a 0 #{})))))
    (testing "a step that lasts is due until it is over, and not once done"
      (is (= [[1 150 false]] (mapv (juxt :index :elapsed-ms :over?) (tour/due a 150 #{0}))))
      (is (= [[1 300 true] [2 0 false]] (mapv (juxt :index :elapsed-ms :over?) (tour/due a 300 #{0}))))
      (is (= [[2 10 false]] (mapv (juxt :index :elapsed-ms :over?) (tour/due a 310 #{0 1})))))
    (testing "a late tick hands out everything it missed, in order"
      (is (= [0 1 2 3 4] (mapv :index (tour/due a 9000 #{}))))
      (is (every? :over? (tour/due a 9000 #{}))))
    (is (= [] (tour/due a 9000 #{0 1 2 3 4})))))

(defn- played
  "Every (index, over?) handed out when segment is played at times, each
  step marked done once it is over, as the shell does."
  [segment times]
  (:seen (reduce (fn [{:keys [done seen]} t]
                   (let [steps (tour/due segment t done)]
                     {:done (into done (comp (filter :over?) (map :index)) steps)
                      :seen (into seen (map (juxt :index :over?)) steps)}))
                 {:done #{} :seen []}
                 times)))

(defspec due-never-skips-or-repeats-a-step 100
  (prop/for-all [times (gen/vector (gen/choose 0 40000) 0 60)]
    (every? (fn [segment]
              (let [end (reduce max 0 (map :at-ms segment))
                    ;; the last tick is late enough for every step to be over
                    seen (played segment (concat (sort times) [(+ end 100000)]))
                    ended (keep (fn [[i over?]] (when over? i)) seen)
                    firsts (distinct (map first seen))]
                (and (= (range (count segment)) ended)
                     (= (range (count segment)) firsts))))
            (concat (tour/plan small) (tour/plan tour/script)))))

(deftest script-test
  (let [steps (steps-of tour/script)
        kinds (fn [k] (filter #(= k (:do %)) steps))]
    (testing "the eight captions, in order"
      (is (= ["It starts like any chat box."
              "What matters can stay gold."
              "Hold a letter, and it holds the note."
              "Then the lights go down. Some words know what they are."
              "Ctrl+Enter reads it back, at your pace."
              "Same words. Another performer."
              "And when it deserves it: the whole stage."
              "Your turn."]
             (mapv #(tour/caption tour/script %) (range 1 9))))
      (is (every? #(and (not (str/blank? %)) (< (count %) 60)) (map :caption tour/script))))
    (testing "what it types is what a person may send"
      (is (= 6 (count (kinds :type))))
      (is (every? #(schema/valid? :ha/message-text (:text %)) (kinds :type)))
      (is (every? #(pos-int? (:ms-per-key %)) (kinds :type))))
    (testing "its styles are named styles, its levels reveal levels"
      (is (seq (kinds :style)))
      (is (every? #(schema/valid? :ha/style (:style %)) (kinds :style)))
      (is (every? #(<= 0 (:level %) 3) (kinds :reveal))))
    (testing "every step is one the shell knows"
      (is (every? #{:clear :type :wait :style :reveal :read-back :until-read :light} (map :do steps))))
    (testing "a light sweeps a phrase that is on the page"
      (is (seq (kinds :light)))
      (is (:ok? (reduce (fn [{:keys [text ok?]} step]
                          (case (:do step)
                            :clear {:text "" :ok? ok?}
                            :type {:text (str text (:text step)) :ok? ok?}
                            :light {:text text :ok? (and ok? (str/includes? text (:over step)))}
                            {:text text :ok? ok?}))
                        {:text "" :ok? true}
                        steps))))
    (testing "every read-back is waited for"
      (is (= (count (kinds :read-back)) (count (kinds :until-read)) (dec (count (tour/plan tour/script))))))
    (testing "under a minute, read-backs aside"
      (is (<= 25000 (tour/total-ms tour/script) 60000)))
    (testing "the plan holds every step once, in order"
      (is (= steps (map :step (apply concat (tour/plan tour/script)))))
      (is (= (range 1 9) (distinct (map :scene (apply concat (tour/plan tour/script)))))))))

(deftest total-ms-test
  (is (= (+ 300 50 20 500) (tour/total-ms small))))
