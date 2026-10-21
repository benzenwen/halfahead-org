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
                     :send-fn (fn [_] {:status 200 :body (json/generate-string [{"document" d}])})})]
    (is (= [msg] (store/messages! (cfg doc) "c1")))
    (is (= :ha/invalid-data (:type (ex-data (try (store/messages! (cfg bad) "c1")
                                                 (catch Exception e e))))))))

(deftest messages-query-request-test
  (let [r (store/messages-query-request {:project-id "demo-x"} "c1")
        q (get (json/parse-string (:body r) true) :structuredQuery)]
    (is (= :post (:method r)))
    (is (re-find #"/projects/demo-x/databases/\(default\)/documents/conversations/c1:runQuery$"
                 (:url r)))
    (is (= [{:collectionId "messages"}] (:from q)))
    (is (= [{:field {:fieldPath "sentAt"} :direction "DESCENDING"}] (:orderBy q)))
    (is (= 200 (:limit q)))))

(deftest messages-newest-first-become-oldest-first-test
  (let [doc  (fn [id ms]
               (assoc (rest/->fields (codec/encode :ha/message (assoc msg :id id :sent-at (Date. ms))))
                      "name" (str "projects/p/databases/(default)/documents/conversations/c1/messages/" id)))
        sent (atom nil)
        cfg  {:project-id "demo-x"
              :send-fn (fn [req]
                         (reset! sent req)
                         {:status 200
                          :body (json/generate-string
                                 [{"document" (doc "m3" 3000)}
                                  {"document" (doc "m2" 2000)}
                                  {"document" (doc "m1" 1000)}
                                  {"readTime" "2026-01-01T00:00:00Z"}])})}
        out  (store/messages! cfg "c1")]
    (is (= ["m1" "m2" "m3"] (mapv :id out)))
    (is (= [(Date. 1000) (Date. 2000) (Date. 3000)] (mapv :sent-at out)))
    (is (= (store/messages-query-request cfg "c1") @sent))))
