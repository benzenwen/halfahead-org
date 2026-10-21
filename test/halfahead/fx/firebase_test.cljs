(ns halfahead.fx.firebase-test
  (:require ["firebase/firestore" :refer [Timestamp]]
            [cljs.test :refer [deftest is testing]]
            [halfahead.codec :as codec]
            [halfahead.fx.firebase :refer [data->wire]]))

(def d (js/Date. 2026 0 15 12 30 0))
(def ts (.fromDate Timestamp d))

(defn- same-date? [x]
  (and (instance? js/Date x) (= (.getTime d) (.getTime x))))

(deftest timestamps-converted-at-every-depth
  (let [wire (data->wire #js {:at ts
                              :nested #js {:inner ts}
                              :arr #js [ts #js {:deep ts}]})]
    (is (same-date? (get wire "at")))
    (is (same-date? (get-in wire ["nested" "inner"])))
    (is (same-date? (get-in wire ["arr" 0])))
    (is (same-date? (get-in wire ["arr" 1 "deep"])))))

(deftest conversation-shaped-data-decodes
  (let [data #js {:title "Plans" :memberIds #js ["ben" "ben-agent"]
                  :createdBy "ben" :createdAt ts}
        conversation (codec/decode :ha/conversation (assoc (data->wire data) "id" "c1"))]
    (is (= "c1" (:id conversation)))
    (is (= (.getTime d) (.getTime (:created-at conversation))))))

(deftest non-timestamp-values-pass-through
  (testing "scalars, nested maps and arrays are unchanged"
    (is (= {"s" "x" "n" 1.5 "t" true "f" false "nil" nil
            "m" {"a" {"b" "c"}}
            "arr" [1 "two" nil false]}
           (data->wire #js {:s "x" :n 1.5 :t true :f false :nil nil
                            :m #js {:a #js {:b "c"}}
                            :arr #js [1 "two" nil false]})))))
