(ns halfahead.firestore-rest-test
  (:require [clojure.test :refer [deftest is testing]]
            [halfahead.firestore-rest :as rest]))

(def wire {"id" "m1"
           "text" "Hello"
           "count" 5
           "ratio" 0.5
           "flag" true
           "nothing" nil
           "sentAt" #inst "2026-10-21T08:00:00.000Z"
           "emphasis" [{"from" 0 "to" 5} {"from" 6 "to" 11}]
           "memberIds" []
           "nested" {"a" {"b" "c"}}})

(defn- thrown-type [f]
  (try (f) nil (catch #?(:clj Exception :cljs :default) e (:type (ex-data e)))))

(deftest round-trip
  (is (= wire (rest/<-fields (rest/->fields wire)))))

(deftest typed-values
  (let [f #(get-in (rest/->fields {"v" %}) ["fields" "v"])]
    (is (= {"integerValue" "5"} (f 5)))
    (is (= {"doubleValue" 0.5} (f 0.5)))
    (is (= {"booleanValue" false} (f false)))
    (is (= {"nullValue" nil} (f nil)))
    (is (= {"stringValue" "x"} (f "x")))
    (is (= {"mapValue" {"fields" {"a" {"integerValue" "1"}}}} (f {"a" 1})))
    (is (= {"arrayValue" {"values" [{"integerValue" "1"}]}} (f [1])))))

(deftest empty-array-has-no-values-key
  (is (= {"fields" {"v" {"arrayValue" {}}}} (rest/->fields {"v" []})))
  (is (= {"v" []} (rest/<-fields (rest/->fields {"v" []}))))
  (is (= {"v" []} (rest/<-fields {"fields" {"v" {"arrayValue" {}}}}))))

#?(:clj
   (deftest timestamp-format
     (is (= {"timestampValue" "2026-10-21T08:00:00Z"}
            (get-in (rest/->fields {"t" #inst "2026-10-21T08:00:00.000Z"})
                    ["fields" "t"])))
     (testing "fractional seconds survive a round trip"
       (let [t #inst "2026-10-21T08:00:00.123Z"]
         (is (= {"t" t} (rest/<-fields (rest/->fields {"t" t}))))))))

(deftest unsupported-values
  (is (= :ha/unsupported-value (thrown-type #(rest/->fields {"k" :kw}))))
  (is (= :ha/unsupported-value (thrown-type #(rest/->fields {"k" #{1}}))))
  (is (= :ha/unsupported-value
         (thrown-type #(rest/<-fields {"fields" {"k" {"geoPointValue" {}}}})))))
