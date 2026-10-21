(ns halfahead.dev.seed
  "Fills the running emulators with what a developer needs to try the app:
  a sign-in-able Auth user, the matching human, his agent, and a first
  conversation. Safe to run twice. Goes through the store, so it refuses
  any project that is not a demo- project.

    bb -m halfahead.dev.seed"
  (:require [babashka.http-client :as http]
            [cheshire.core :as json]
            [clojure.string :as str]
            [halfahead.worker.store :as store]))

(def ^:private default-email "ben@halfahead.test")
(def ^:private default-password "halfahead-dev")
(def ^:private default-auth-host "127.0.0.1:9099")

(defn plan
  "Pure description of everything the seed creates."
  [{:keys [now email password]}]
  {:auth-user     {:local-id "ben" :email email :password password :display-name "Ben"}
   :members       [{:id "ben" :kind :human :display-name "Ben" :joined-at now}
                   {:id "scout" :kind :agent :display-name "Scout" :owner-id "ben"
                    :joined-at now}]
   :conversations [{:id "first" :title "First light" :member-ids ["ben" "scout"]
                    :created-by "ben" :created-at now}]})

(defn auth-user-request
  "Create-account request for the Auth emulator. env is a map of
  environment variables, passed in so this stays pure."
  ([auth-user] (auth-user-request auth-user {}))
  ([{:keys [local-id email password display-name]} env]
   {:method  :post
    :url     (str "http://" (get env "FIREBASE_AUTH_EMULATOR_HOST" default-auth-host)
                  "/identitytoolkit.googleapis.com/v1/projects/" store/default-project
                  "/accounts")
    :headers {"Authorization" "Bearer owner" "Content-Type" "application/json"}
    :body    (json/generate-string {"localId" local-id "email" email
                                    "password" password "displayName" display-name})}))

(def ^:private already-there #{"EMAIL_EXISTS" "DUPLICATE_LOCAL_ID"})

(defn- error-message [body]
  (try (get-in (json/parse-string body) ["error" "message"])
       (catch Exception _ nil)))

(defn- create-auth-user!
  "True when created, false when it was already there."
  [send-fn request]
  (let [{:keys [status body]} (send-fn request)]
    (cond
      (<= 200 status 299) true
      (already-there (some-> (error-message body) (str/split #" ") first)) false
      :else (throw (ex-info "auth emulator refused the seed user"
                            {:type :ha/seed-auth-failed :status status
                             :message (error-message body)})))))

(defn- run-seed!
  [{:keys [env now send-fn]}]
  (let [{:keys [auth-user members conversations]}
        (plan {:now now
               :email default-email
               :password (get env "HALFAHEAD_DEV_PASSWORD" default-password)})
        cfg {:send-fn send-fn}]
    (println (if (create-auth-user! send-fn (auth-user-request auth-user env))
               (str "auth user " (:local-id auth-user) " created (" (:email auth-user) ")")
               (str "auth user " (:local-id auth-user) " already existed")))
    (doseq [m members]
      (store/write-member! cfg m)
      (println "member" (:id m)))
    (doseq [c conversations]
      (store/write-conversation! cfg c)
      (println "conversation" (:id c)))))

(defn -main [& _]
  (run-seed! {:env      (into {} (System/getenv))
         :now      (java.util.Date.)
         :send-fn  #(http/request (assoc % :throw false))}))
