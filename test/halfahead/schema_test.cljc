(ns halfahead.schema-test
  (:require [clojure.test :refer [deftest is testing]]
            [halfahead.schema :as schema]))

(def t0 #inst "2026-10-21T08:00:00Z")

(deftest members
  (testing "a human has no owner; an agent must have one"
    (is (schema/valid? :ha/member {:id "ben" :kind :human :display-name "Ben"
                                   :joined-at t0}))
    (is (schema/valid? :ha/member {:id "ben-agent" :kind :agent
                                   :display-name "Sidekick" :owner-id "ben"
                                   :joined-at t0}))
    (is (not (schema/valid? :ha/member {:id "stray" :kind :agent
                                        :display-name "Nobody's"
                                        :joined-at t0})))
    (is (not (schema/valid? :ha/member {:id "ben" :kind :human
                                        :display-name "Ben" :owner-id "x"
                                        :joined-at t0})))))

(deftest messages
  (let [m {:id "m1" :conversation-id "c1" :author-id "ben" :text "Hello"
           :delivery :plain :emphasis [] :sent-at t0}]
    (is (schema/valid? :ha/message m))
    (testing "deliveries are a closed vocabulary"
      (is (schema/valid? :ha/message (assoc m :delivery :headline)))
      (is (not (schema/valid? :ha/message (assoc m :delivery :fireworks)))))
    (testing "blank text and unknown keys are invalid"
      (is (not (schema/valid? :ha/message (assoc m :text "   "))))
      (is (not (schema/valid? :ha/message (assoc m :mood :happy)))))
    (testing "spans are half-open and non-empty"
      (is (schema/valid? :ha/message (assoc m :emphasis [[0 5]])))
      (is (not (schema/valid? :ha/message (assoc m :emphasis [[3 3]])))))))

(deftest timelines
  (let [tl {:duration-ms 400
            :beats [{:span [0 5] :effect :appear :at-ms 0 :ms 400}]}]
    (is (schema/valid? :ha/timeline tl))
    (is (not (schema/valid? :ha/timeline
                            (assoc tl :duration-ms (inc schema/max-performance-ms)))))
    (is (not (schema/valid? :ha/timeline (assoc tl :beats []))))))

(deftest engine-contract
  (is (schema/valid? :ha/engine-request
                     {:persona "You are a sidekick."
                      :history [{:role :human :text "Hi"}]}))
  (is (schema/valid? :ha/engine-reply
                     {:text "Hi back." :delivery :aside :emphasis ["back"]}))
  (is (some? (schema/explain :ha/engine-reply {:text "" :delivery :plain
                                               :emphasis []}))))
