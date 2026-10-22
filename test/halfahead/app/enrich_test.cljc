(ns halfahead.app.enrich-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [halfahead.app.enrich :as enrich]))

(def ctx {:now-ms 1234 :now #inst "2026-01-01T00:00:00Z" :new-id "m1"
          :value "hello" :key nil :shift? false})

(def sent [[:composer/sent {:id "m1" :now #inst "2026-01-01T00:00:00Z"}]])

(deftest edited-carries-the-input-value
  (is (= [[:composer/edited {:text "hello"}]]
         (enrich/enrich [:composer/edited] ctx))))

(deftest unshifted-enter-sends-once
  (is (= sent (enrich/enrich [:composer/key {:key nil :shift? false}]
                             (assoc ctx :key "Enter")))))

(deftest shift-enter-sends-nothing
  (is (= [] (enrich/enrich [:composer/key {}] (assoc ctx :key "Enter" :shift? true)))))

(deftest other-keys-send-nothing
  (doseq [k ["a" "Escape" "Tab"]]
    (is (= [] (enrich/enrich [:composer/key {}] (assoc ctx :key k))))))

(deftest submit-sends-with-id-and-time
  (is (= sent (enrich/enrich [:composer/sent] ctx))))

(deftest replay-gains-the-time
  (is (= [[:replay/requested {:id "m9" :now-ms 1234}]]
         (enrich/enrich [:replay/requested {:id "m9"}] ctx))))

(deftest unknown-actions-pass-through
  (is (= [[:message/skipped {:id "m1"}]]
         (enrich/enrich [:message/skipped {:id "m1"}] ctx))))

(defspec unknown-actions-always-pass-through 50
  (prop/for-all [k (gen/elements [:message/skipped :prefs/changed :clock/ticked :x/y])
                 n gen/small-integer]
    (let [a [k {:n n}]]
      (= [a] (enrich/enrich a ctx)))))

(deftest prevent-default
  (testing "unshifted Enter and submit"
    (is (enrich/prevent-default? [:composer/key {}] (assoc ctx :key "Enter")))
    (is (enrich/prevent-default? [:composer/sent] ctx)))
  (testing "everything else leaves the browser alone"
    (is (not (enrich/prevent-default? [:composer/key {}] (assoc ctx :key "Enter" :shift? true))))
    (is (not (enrich/prevent-default? [:composer/key {}] (assoc ctx :key "a"))))
    (is (not (enrich/prevent-default? [:composer/edited] ctx)))))

(deftest scene-ended-carries-time-and-date
  (is (= [[:scene/ended {:now-ms 1234 :date "21 October 2026"}]]
         (enrich/enrich [:scene/ended] (assoc ctx :date "21 October 2026")))))

(deftest only-escape-dismisses-the-scene
  (is (= [[:scene/dismissed]] (enrich/enrich [:scene/key] (assoc ctx :key "Escape"))))
  (is (= [] (enrich/enrich [:scene/key] (assoc ctx :key "a"))))
  (is (false? (enrich/prevent-default? [:scene/key] (assoc ctx :key "Escape"))))
  (is (false? (enrich/prevent-default? [:scene/key] (assoc ctx :key "a")))))

(deftest scene-clock-actions-carry-the-time
  (doseq [k [:scene/replay :scene/paused :scene/resumed]]
    (is (= [[k {:now-ms 1234}]] (enrich/enrich [k] ctx))))
  (is (= [[:scene/stopped]] (enrich/enrich [:scene/stopped] ctx))))
