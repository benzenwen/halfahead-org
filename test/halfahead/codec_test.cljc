(ns halfahead.codec-test
  (:require [clojure.test :refer [deftest is testing]]
            [halfahead.codec :as codec]))

(def t0 #inst "2026-10-21T08:00:00Z")

(def human {:id "ben" :kind :human :display-name "Ben" :joined-at t0})
(def agent {:id "ben-agent" :kind :agent :display-name "Sidekick"
            :owner-id "ben" :joined-at t0})
(def conversation {:id "c1" :title "Plans" :member-ids ["ben" "ben-agent"]
                   :created-by "ben" :created-at t0})
(def message {:id "m1" :conversation-id "c1" :author-id "ben" :text "Hello there world"
              :delivery :headline :emphasis [[0 5] [6 11]] :sent-at t0
              :in-reply-to "m0"})
(def presence {:member-id "ben-agent" :conversation-id "c1" :state :thinking
               :since t0})

(deftest round-trips
  (doseq [[k x wire-keys]
          [[:ha/member human #{"id" "kind" "displayName" "joinedAt"}]
           [:ha/member agent #{"id" "kind" "displayName" "ownerId" "joinedAt"}]
           [:ha/conversation conversation
            #{"id" "title" "memberIds" "createdBy" "createdAt"}]
           [:ha/message message
            #{"id" "conversationId" "authorId" "text" "delivery" "emphasis"
              "sentAt" "inReplyTo"}]
           [:ha/presence presence #{"memberId" "conversationId" "state" "since"}]]]
    (testing (str k " " (:kind x))
      (let [wire (codec/encode k x)]
        (is (= wire-keys (set (keys wire))))
        (is (= x (codec/decode k wire)))))))

(deftest enums-and-spans-on-the-wire
  (let [wire (codec/encode :ha/message message)]
    (is (= "headline" (get wire "delivery")))
    (is (= [{"from" 0 "to" 5} {"from" 6 "to" 11}] (get wire "emphasis")))
    (is (= [[0 5] [6 11]] (:emphasis (codec/decode :ha/message wire)))))
  (is (= [] (get (codec/encode :ha/message (assoc message :emphasis []))
                 "emphasis")))
  (is (= "agent" (get (codec/encode :ha/member agent) "kind"))))

(defn- thrown [f]
  (try (f) nil (catch #?(:clj Exception :cljs :default) e (ex-data e))))

(deftest encode-is-strict
  (doseq [bad [(assoc message :text "  ")
               (assoc message :mood :happy)
               (assoc message :delivery :fireworks)]]
    (let [d (thrown #(codec/encode :ha/message bad))]
      (is (= :ha/invalid-data (:type d)))
      (is (= :ha/message (:entity d)))
      (is (some? (:errors d))))))

(deftest decode-validates
  (let [wire (assoc (codec/encode :ha/message message) "text" "   ")
        d (thrown #(codec/decode :ha/message wire))]
    (is (= :ha/invalid-data (:type d)))
    (is (some? (:errors d))))
  (testing "malformed spans and enums are invalid data, not raw exceptions"
    (let [wire (codec/encode :ha/message message)]
      (is (= :ha/invalid-data
             (:type (thrown #(codec/decode :ha/message (assoc wire "emphasis" [{"from" 5}]))))))
      (is (= :ha/invalid-data
             (:type (thrown #(codec/decode :ha/message (assoc wire "emphasis" "x"))))))
      (is (= :ha/invalid-data
             (:type (thrown #(codec/decode :ha/presence
                                           (assoc (codec/encode :ha/presence presence)
                                                  "state" "asleep")))))))))

(deftest tolerant-reading
  (let [wire (codec/encode :ha/message message)]
    (is (not (contains? (codec/decode :ha/message (assoc wire "mood" "happy"))
                        :mood)))
    (is (= :plain (:delivery (codec/decode :ha/message
                                           (assoc wire "delivery" "fireworks")))))))

(deftest member-owner-rule
  (let [agent-wire (codec/encode :ha/member agent)
        human-wire (codec/encode :ha/member human)]
    (is (= :ha/invalid-data
           (:type (thrown #(codec/decode :ha/member (dissoc agent-wire "ownerId"))))))
    (is (= :ha/invalid-data
           (:type (thrown #(codec/decode :ha/member (assoc human-wire "ownerId" "x"))))))))
