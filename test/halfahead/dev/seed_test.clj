(ns halfahead.dev.seed-test
  (:require [cheshire.core :as json]
            [clojure.test :refer [deftest is]]
            [halfahead.dev.seed :as seed]
            [halfahead.schema :as schema]
            [halfahead.worker.store :as store])
  (:import (java.util Date)))

(def now (Date. 0))
(def the-plan (seed/plan {:now now :email "ben@halfahead.test" :password "pw"}))

(deftest plan-test
  (is (every? #(schema/valid? :ha/member %) (:members the-plan)))
  (is (every? #(schema/valid? :ha/conversation %) (:conversations the-plan)))
  (is (= ["ben" "scout"] (mapv :id (:members the-plan))))
  (is (= ["first"] (mapv :id (:conversations the-plan))))
  (is (= "ben" (:owner-id (second (:members the-plan))))))

(deftest auth-user-request-test
  (let [r (seed/auth-user-request (:auth-user the-plan) {})]
    (is (= :post (:method r)))
    (is (= "http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/projects/demo-halfahead/accounts"
           (:url r)))
    (is (= "Bearer owner" (get-in r [:headers "Authorization"])))
    (is (= {"localId" "ben" "email" "ben@halfahead.test" "password" "pw" "displayName" "Ben"}
           (json/parse-string (:body r)))))
  (is (re-find #"^http://localhost:9199/identitytoolkit"
               (:url (seed/auth-user-request (:auth-user the-plan)
                                             {"FIREBASE_AUTH_EMULATOR_HOST" "localhost:9199"})))))

(defn- run-with [auth-response env]
  (let [sent (atom [])
        send-fn (fn [req]
                  (swap! sent conj req)
                  (if (re-find #"identitytoolkit" (:url req)) auth-response {:status 200 :body "{}"}))]
    (with-out-str (#'seed/run-seed! {:env env :now now :send-fn send-fn}))
    @sent))

(def created {:status 200 :body "{}"})
(defn- refused [message] {:status 400 :body (json/generate-string {"error" {"message" message}})})

(deftest run-creates-and-upserts-test
  (let [sent (run-with created {"HALFAHEAD_DEV_PASSWORD" "secret"})
        cfg  {}]
    (is (= :post (:method (first sent))))
    (is (= "secret" (get (json/parse-string (:body (first sent))) "password")))
    (is (= [(store/write-request cfg "members/ben" :ha/member (get-in the-plan [:members 0]))
            (store/write-request cfg "members/scout" :ha/member (get-in the-plan [:members 1]))
            (store/write-request cfg "conversations/first" :ha/conversation
                                 (get-in the-plan [:conversations 0]))]
           (rest sent)))))

(deftest existing-user-counts-as-done-test
  (doseq [message ["EMAIL_EXISTS" "DUPLICATE_LOCAL_ID"]]
    (is (= 4 (count (run-with (refused message) {}))))))

(deftest other-auth-failures-propagate-test
  (doseq [response [(refused "WEAK_PASSWORD : too short") {:status 500 :body "boom"}]]
    (is (= :ha/seed-auth-failed
           (:type (ex-data (try (run-with response {}) (catch Exception e e))))))))

(deftest running-twice-is-safe-test
  (is (= (rest (run-with created {})) (rest (run-with (refused "EMAIL_EXISTS") {})))))
