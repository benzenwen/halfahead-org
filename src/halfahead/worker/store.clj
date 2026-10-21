(ns halfahead.worker.store
  "Firestore REST against the emulator, with domain maps in and out. The
  only worker namespace that does HTTP. Refuses any project that is not a
  demo- project: no real project is touched until an order says so."
  (:require [babashka.http-client :as http]
            [cheshire.core :as json]
            [clojure.string :as str]
            [halfahead.codec :as codec]
            [halfahead.firestore-rest :as rest]))

(def default-project "demo-halfahead")

(defn emulator-host []
  (or (System/getenv "FIRESTORE_EMULATOR_HOST") "127.0.0.1:8080"))

(defn- project-of [cfg] (or (:project-id cfg) default-project))

(defn assert-emulator! [cfg]
  (let [project (project-of cfg)]
    (when-not (str/starts-with? (str project) "demo-")
      (throw (ex-info "worker only talks to emulator projects (demo-*)"
                      {:type :ha/not-emulator :project-id project})))))

(defn document-path
  "Path below .../documents for entity kind and its ids."
  [kind & ids]
  (case kind
    :member       (str "members/" (first ids))
    :conversation (str "conversations/" (first ids))
    :message      (str "conversations/" (first ids) "/messages/" (second ids))
    :presence     (str "conversations/" (first ids) "/presence/" (second ids))))

(defn- documents-url [cfg]
  (str "http://" (emulator-host) "/v1/projects/" (project-of cfg)
       "/databases/(default)/documents"))

(def ^:private headers
  {"Authorization" "Bearer owner" "Content-Type" "application/json"})

(defn get-request [cfg path]
  {:method :get :url (str (documents-url cfg) "/" path) :headers headers})

(defn list-request [cfg collection-path]
  {:method :get
   :url (str (documents-url cfg) "/" collection-path "?pageSize=300")
   :headers headers})

(defn write-request
  "PATCH of the whole document at path; creates or replaces, so a retry is
  harmless."
  [cfg path kind entity]
  {:method  :patch
   :url     (str (documents-url cfg) "/" path)
   :headers headers
   :body    (json/generate-string (rest/->fields (codec/encode kind entity)))})

(defn conversations-query-request [cfg member-id]
  {:method  :post
   :url     (str (documents-url cfg) ":runQuery")
   :headers headers
   :body    (json/generate-string
             {"structuredQuery"
              {"from"  [{"collectionId" "conversations"}]
               "where" {"fieldFilter" {"field" {"fieldPath" "memberIds"}
                                       "op"    "ARRAY_CONTAINS"
                                       "value" {"stringValue" member-id}}}}})})

(defn- send!
  "The one HTTP call. cfg's :send-fn replaces it in tests."
  [cfg request]
  (assert-emulator! cfg)
  (let [{:keys [status body]} ((or (:send-fn cfg) #(http/request (assoc % :throw false)))
                               request)]
    (cond
      (= 404 status) nil
      (<= 200 status 299) (json/parse-string body)
      :else (throw (ex-info "firestore request failed"
                            {:type :ha/firestore-failed :status status :body body
                             :url (:url request)})))))

(defn- decode-doc [kind doc]
  (codec/decode kind (rest/<-fields doc)))

(defn- id-of [doc]
  (last (str/split (get doc "name") #"/")))

(defn- decode-with-id [kind doc]
  (decode-doc kind (update doc "fields" assoc "id" {"stringValue" (id-of doc)})))

(defn conversations-of! [cfg member-id]
  (assert-emulator! cfg)
  (->> (send! cfg (conversations-query-request cfg member-id))
       (keep #(get % "document"))
       (mapv #(decode-with-id :ha/conversation %))))

(defn members! [cfg ids]
  (assert-emulator! cfg)
  (into [] (keep #(some->> (send! cfg (get-request cfg (document-path :member %)))
                           (decode-with-id :ha/member)))
        ids))

(defn messages! [cfg conversation-id]
  (assert-emulator! cfg)
  (->> (send! cfg (list-request cfg (str "conversations/" conversation-id "/messages")))
       (#(get % "documents"))
       (mapv #(decode-with-id :ha/message %))))

(defn write-message! [cfg msg]
  (assert-emulator! cfg)
  (send! cfg (write-request cfg
                            (document-path :message (:conversation-id msg) (:id msg))
                            :ha/message msg))
  msg)

(defn write-presence! [cfg presence]
  (assert-emulator! cfg)
  (send! cfg (write-request cfg
                            (document-path :presence (:conversation-id presence) (:member-id presence))
                            :ha/presence presence))
  presence)
