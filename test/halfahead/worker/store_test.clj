(ns halfahead.worker.store-test
  (:require [cheshire.core :as json]
            [clojure.test :refer [deftest is testing]]
            [halfahead.codec :as codec]
            [halfahead.firestore-rest :as rest]
            [halfahead.worker.store :as store])
  (:import (java.util Date)))

(def msg {:id "m1" :conversation-id "c1" :author-id "bot" :text "hi" :delivery :plain
          :emphasis [] :sent-at (Date. 0)})
(def pres {:member-id "bot" :conversation-id "c1" :state :thinking :since (Date. 0)})

(defn- never-sent [_] (throw (AssertionError. "HTTP reached")))

(deftest refuses-non-emulator-before-http-test
  (doseq [project ["prod" "halfahead" "my-demo-x"]
          :let [cfg {:project-id project :send-fn never-sent}]
          f [#(store/conversations-of! cfg "bot") #(store/members! cfg ["bot"])
             #(store/messages! cfg "c1") #(store/write-message! cfg msg)
             #(store/write-presence! cfg pres)]]
    (let [e (try (f) nil (catch Throwable e e))]
      (is (= :ha/not-emulator (:type (ex-data e)))))))

(deftest paths-test
  (is (= "members/a" (store/document-path :member "a")))
  (is (= "conversations/c" (store/document-path :conversation "c")))
  (is (= "conversations/c/messages/m" (store/document-path :message "c" "m")))
  (is (= "conversations/c/presence/a" (store/document-path :presence "c" "a"))))

(deftest request-building-test
  (let [cfg {:project-id "demo-x"}
        r   (store/write-request cfg "conversations/c1/messages/m1" :ha/message msg)]
    (is (= :patch (:method r)))
    (is (= "Bearer owner" (get-in r [:headers "Authorization"])))
    (is (re-find #"/projects/demo-x/databases/\(default\)/documents/conversations/c1/messages/m1$"
                 (:url r)))
    (is (= msg (codec/decode :ha/message (rest/<-fields (json/parse-string (:body r)))))))
  (let [q (json/parse-string (:body (store/conversations-query-request {} "bot")) true)]
    (is (= {:field {:fieldPath "memberIds"} :op "ARRAY_CONTAINS" :value {:stringValue "bot"}}
           (get-in q [:structuredQuery :where :fieldFilter])))))

(deftest decode-at-boundary-test
  (let [doc (assoc (rest/->fields (codec/encode :ha/message msg))
                   "name" "projects/p/databases/(default)/documents/conversations/c1/messages/m1")
        bad (assoc-in doc ["fields" "text"] {"stringValue" ""})
        cfg (fn [d] {:project-id "demo-x"
                     :send-fn (fn [_] {:status 200 :body (json/generate-string {"documents" [d]})})})]
    (is (= [msg] (store/messages! (cfg doc) "c1")))
    (is (= :ha/invalid-data (:type (ex-data (try (store/messages! (cfg bad) "c1")
                                                 (catch Exception e e))))))))
