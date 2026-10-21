(ns halfahead.rules-test
  "Firestore rules, exercised over the emulator's REST API. The unit tests of
  the free-port and config functions need no emulator."
  (:require [babashka.fs :as fs]
            [babashka.http-client :as http]
            [cheshire.core :as json]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [halfahead.dev.emulator :as emulator]
            [halfahead.firestore-rest :as rest])
  (:import (java.net ServerSocket)
           (java.util Base64)))

(def ^:private project "demo-halfahead")

(defn- host [] (System/getenv "FIRESTORE_EMULATOR_HOST"))

(defn- docs-url [& segments]
  (str "http://" (host) "/v1/projects/" project "/databases/(default)/documents"
       (apply str (map #(str "/" %) segments))))

(defn- b64 [m]
  (.encodeToString (.withoutPadding (Base64/getUrlEncoder))
                   (.getBytes (json/generate-string m) "UTF-8")))

(defn- id-token [uid]
  (let [now (quot (System/currentTimeMillis) 1000)]
    (str (b64 {:alg "none" :typ "JWT"}) "."
         (b64 {:sub uid :user_id uid :aud project
               :iss (str "https://securetoken.google.com/" project)
               :iat now :auth_time now :exp (+ now 3600)
               :firebase {:sign_in_provider "password" :identities {}}})
         ".")))

(defn- auth-header [as]
  (case as
    :admin      {"Authorization" "Bearer owner"}
    :signed-out {}
    {"Authorization" (str "Bearer " (id-token as))}))

(defn- call [method as url body]
  (let [resp (http/request (cond-> {:method  method
                                    :uri     url
                                    :headers (merge {"Content-Type" "application/json"}
                                                    (auth-header as))
                                    :throw   false}
                             body (assoc :body (json/generate-string body))))]
    (:status resp)))

(defn- get-doc [as & segments] (call :get as (apply docs-url segments) nil))

(defn- put-doc!
  "Writes a document with plain wire fields (a PATCH replaces it)."
  [as wire & segments]
  (call :patch as (apply docs-url segments) (rest/->fields wire)))

(defn- delete-doc [as & segments] (call :delete as (apply docs-url segments) nil))

(defn- create-message
  "Commits a new message document; `sent-at` is :request-time (the valid
  case) or a client timestamp string."
  [as cid mid wire sent-at]
  (let [path (str "projects/" project "/databases/(default)/documents/conversations/"
                  cid "/messages/" mid)
        wire (dissoc wire :sentAt)
        write (cond-> {:update (merge {:name path} (rest/->fields (update-keys wire name)))
                       :currentDocument {:exists false}}
                (= sent-at :request-time)
                (assoc :updateTransforms [{:fieldPath "sentAt" :setToServerValue "REQUEST_TIME"}])
                (string? sent-at)
                (update-in [:update :fields] assoc "sentAt" {"timestampValue" sent-at}))]
    (call :post as
          (str "http://" (host) "/v1/projects/" project "/databases/(default)/documents:commit")
          {:writes [write]})))

(defn- message
  "A valid message with `overrides` applied; an override of ::drop removes the key."
  [overrides]
  (->> (merge {"id" "m2" "conversationId" "c1" "authorId" "alice"
               "text" "hello" "delivery" "plain" "emphasis" []}
              overrides)
       (remove (comp #{::drop} val))
       (into {})))

(defn- send!
  "Creates the document c1/messages/<::mid or the message id>; the body's own
  id and conversationId come from `overrides`, so they can disagree with the path."
  [as overrides & [sent-at]]
  (let [m (message (dissoc overrides ::mid))]
    (create-message as "c1" (get overrides ::mid (get m "id"))
                    (update-keys m keyword) (or sent-at :request-time))))

(defn- seed! []
  (http/delete (str "http://" (host) "/emulator/v1/projects/" project
                    "/databases/(default)/documents"))
  (doseq [m ["alice" "bob" "eve"]]
    (put-doc! :admin {"id" m} "members" m))
  (put-doc! :admin {"id" "c1" "memberIds" ["alice" "bob"]} "conversations" "c1")
  (put-doc! :admin {"id" "m1" "conversationId" "c1" "authorId" "bob"
                    "text" "first" "delivery" "plain" "emphasis" []}
            "conversations" "c1" "messages" "m1"))

(defn- with-seed [f]
  (when (host) (seed!))
  (f))

(use-fixtures :each with-seed)

;; --- emulator-backed rules tests ---

(deftest allowed-reads
  (is (= 200 (get-doc "alice" "conversations" "c1")))
  (is (= 200 (get-doc "alice" "conversations" "c1" "messages" "m1")))
  (is (= 200 (get-doc "bob" "members" "alice"))))

(deftest allowed-writes
  (is (= 200 (send! "alice" {})))
  (is (= 200 (send! "alice" {"id" "m3" "inReplyTo" "m1" "delivery" "headline"
                             "emphasis" [{"from" 0 "to" 1}]})))
  (is (= 200 (put-doc! "alice" {"state" "thinking"}
                       "conversations" "c1" "presence" "alice")))
  (is (= 200 (get-doc "bob" "conversations" "c1" "presence" "alice"))))

(deftest non-member-denied
  (is (= 403 (get-doc "eve" "conversations" "c1")))
  (is (= 403 (get-doc "eve" "conversations" "c1" "messages" "m1")))
  (is (= 403 (get-doc "eve" "conversations" "c1" "presence" "alice")))
  (is (= 403 (send! "eve" {"authorId" "eve"}))))

(deftest signed-out-denied
  (doseq [path [["members" "alice"]
                ["conversations" "c1"]
                ["conversations" "c1" "messages" "m1"]
                ["conversations" "c1" "presence" "alice"]]]
    (is (= 403 (apply get-doc :signed-out path)) (str path)))
  (is (= 403 (send! :signed-out {}))))

(deftest message-create-denied
  (testing "positive control: the same shape is allowed"
    (is (= 200 (send! "alice" {"id" "ok"}))))
  (doseq [[label overrides sent-at]
          [["another author"       {"authorId" "bob"} nil]
           ["extra key"            {"extra" "x"} nil]
           ["missing delivery"     {"delivery" ::drop} nil]
           ["empty text"           {"text" ""} nil]
           ["blank text"           {"text" "  \n "} nil]
           ["oversize text"        {"text" (apply str (repeat 4001 "a"))} nil]
           ["non-string text"      {"text" 7} nil]
           ["unknown delivery"     {"delivery" "shout"} nil]
           ["too much emphasis"    {"emphasis" (vec (repeat 9 1))} nil]
           ["mismatched id"        {"id" "m9" ::mid "m2"} nil]
           ["foreign conversation" {"conversationId" "c2"} nil]
           ["client sentAt"        {} "2020-01-01T00:00:00Z"]]]
    (is (= 403 (send! "alice" overrides sent-at))
        label)))

(deftest messages-are-immutable
  (is (= 403 (put-doc! "bob" {"text" "edited"} "conversations" "c1" "messages" "m1")))
  (is (= 403 (delete-doc "bob" "conversations" "c1" "messages" "m1")))
  (is (= 200 (get-doc "bob" "conversations" "c1" "messages" "m1"))))

(deftest presence-is-own-only
  (is (= 403 (put-doc! "alice" {"state" "idle"} "conversations" "c1" "presence" "bob")))
  (is (= 403 (put-doc! "eve" {"state" "idle"} "conversations" "c1" "presence" "eve"))))

(deftest no-client-writes-to-members-or-conversations
  (is (= 403 (put-doc! "alice" {"id" "alice"} "members" "alice")))
  (is (= 403 (put-doc! "eve" {"id" "eve2"} "members" "eve2")))
  (is (= 403 (put-doc! "alice" {"memberIds" ["alice" "eve"]} "conversations" "c1")))
  (is (= 403 (put-doc! "alice" {"memberIds" ["alice"]} "conversations" "c9")))
  (is (= 403 (delete-doc "alice" "conversations" "c1"))))

(deftest unmatched-paths-denied
  (is (= 403 (get-doc "alice" "secrets" "s1")))
  (is (= 403 (put-doc! "alice" {"a" "b"} "secrets" "s1"))))

;; --- unit tests: no emulator ---

(defn- bindable? [port]
  (with-open [s (ServerSocket. port)] (pos? (.getLocalPort s))))

(deftest free-ports-are-distinct-and-available
  (let [ports (emulator/free-ports 3)]
    (is (= 3 (count (set ports))))
    (is (every? pos? ports))
    (is (every? bindable? ports)))
  (is (bindable? (emulator/free-port))))

(deftest emulator-config-isolates-every-listener
  (let [base   {:firestore {:rules "firestore.rules"}
                :emulators {:firestore {:host "127.0.0.1" :port 8080}
                            :auth {:host "127.0.0.1" :port 9099}
                            :ui {:enabled false}
                            :singleProjectMode true}}
        rules  (fs/absolutize "firestore.rules")
        config (emulator/emulator-config base rules [1111 2222 3333 4444])]
    (is (= 1111 (get-in config [:emulators :firestore :port])))
    (is (= "127.0.0.1" (get-in config [:emulators :firestore :host])))
    (is (= 4444 (get-in config [:emulators :firestore :websocketPort])))
    (is (= 2222 (get-in config [:emulators :hub :port])))
    (is (= 3333 (get-in config [:emulators :logging :port])))
    (is (= {:enabled false} (get-in config [:emulators :ui])))
    (is (= (str rules) (get-in config [:firestore :rules])))
    (is (fs/absolute? (get-in config [:firestore :rules])))
    (is (= (get-in base [:emulators :auth]) (get-in config [:emulators :auth])))
    (is (true? (get-in config [:emulators :singleProjectMode])))))

(deftest two-runs-get-different-configs
  (let [base   {:firestore {} :emulators {:firestore {:port 8080}}}
        rules  (fs/absolutize "firestore.rules")
        ;; held open together, as two concurrent runs would be
        a      (emulator/emulator-config base rules (emulator/free-ports 4))
        ports  (conj (mapv #(get-in a [:emulators % :port]) [:firestore :hub :logging])
                     (get-in a [:emulators :firestore :websocketPort]))]
    (is (= 4 (count (set ports))))
    (is (not-any? #{8080 4400 4500 9150} ports))))
