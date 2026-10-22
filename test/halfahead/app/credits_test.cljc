(ns halfahead.app.credits-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [halfahead.app.credits :as credits]
            [halfahead.schema :as schema]))

(def ben {:id "ben" :kind :human :display-name "Ben"})
(def scout {:id "scout" :kind :agent :display-name "Scout" :owner-id "ben"})

(defn state-of [member-ids n]
  {:conversation {:title "First Light" :member-ids member-ids}
   :members {"ben" ben "scout" scout}
   :messages (into {} (map (fn [i] [(str "m" i) {:id (str "m" i)}])) (range n))})

(deftest credits-read-in-order
  (let [text (credits/credits-text (state-of ["ben" "scout"] 4) "21 October 2026")
        at   (fn [s] (str/index-of text s))
        idx  (map at ["FIRST LIGHT" "Ben" "Scout · agent of Ben" "4 lines exchanged"
                      "21 October 2026" "Half Ahead"])]
    (is (every? some? idx))
    (is (apply < idx))
    (is (schema/valid? :ha/message-text text))))

(deftest humans-come-before-agents
  (let [text (credits/credits-text (state-of ["scout" "ben"] 2) "d")]
    (is (< (str/index-of text "Ben") (str/index-of text "Scout")))))

(deftest edge-cases-stay-valid
  (is (str/includes? (credits/credits-text (state-of ["ben"] 1) "d") "1 line exchanged"))
  (let [text (credits/credits-text {:members {"ben" ben}} "d")]
    (is (str/includes? text "0 lines exchanged"))
    (is (schema/valid? :ha/message-text text))))
