(ns halfahead.fx.firebase
  "The only namespace that calls the Firebase JS SDK. Effects in, SDK calls
  out, results back as actions. Everything read is decoded before it is
  dispatched; failures are logged, never swallowed (core has no error state
  yet, so the console is where they surface)."
  (:require ["firebase/app" :refer [initializeApp]]
            ["firebase/auth" :refer [getAuth connectAuthEmulator signInWithEmailAndPassword]]
            ["firebase/firestore" :refer [getFirestore connectFirestoreEmulator doc getDoc
                                          collection query orderBy limitToLast onSnapshot
                                          setDoc serverTimestamp Timestamp]]
            [clojure.walk :as walk]
            [halfahead.codec :as codec]
            [halfahead.config :refer [config]]))

(defonce ^:private db (atom nil))

(defn- log-error [what e]
  (js/console.error (str "halfahead: " what) e)
  (when-let [data (ex-data e)]
    (js/console.error "halfahead: details" (js/JSON.stringify (clj->js (select-keys data [:type :entity :errors]))))))

(defn data->wire
  "A snapshot's data object as a string-keyed map in which every Firestore
  Timestamp, at any depth, is a js/Date. js->clj leaves class instances
  alone and (object? v) is false for them, so conversion must be explicit."
  [data]
  (walk/postwalk #(if (instance? Timestamp %) (.toDate ^js %) %) (js->clj data)))

(defn- ->wire
  "A snapshot's data with the document id as \"id\"."
  [^js snapshot]
  (assoc (data->wire (.data snapshot #js {:serverTimestamps "estimate"}))
         "id" (.-id snapshot)))

(defn- decode-snapshot [kind snapshot]
  (codec/decode kind (->wire snapshot)))

(defn- conversation-path [] ["conversations" (:conversation-id config)])

(defn- watch-messages! [dispatch!]
  (let [initial? (atom true)
        q (query (apply collection @db (concat (conversation-path) ["messages"]))
                 (orderBy "sentAt")
                 (limitToLast 200))]
    (onSnapshot q
                (fn [snap]
                  (try
                    (dispatch! [:messages/received
                                {:messages (mapv #(decode-snapshot :ha/message %) (.-docs ^js snap))
                                 :initial? @initial?
                                 :now-ms (js/Date.now)}])
                    (reset! initial? false)
                    (catch :default e (log-error "messages snapshot" e))))
                #(log-error "messages listener" %))))

(defn- watch-presence! [dispatch!]
  (onSnapshot (apply collection @db (concat (conversation-path) ["presence"]))
              (fn [snap]
                (try
                  (dispatch! [:presence/received
                              {:presence (mapv #(decode-snapshot :ha/presence %) (.-docs ^js snap))}])
                  (catch :default e (log-error "presence snapshot" e))))
              #(log-error "presence listener" %)))

(defn- read-session!
  "Reads the conversation and its members, starts the session, then the
  live listeners."
  [{:keys [dispatch! reduced-motion?]} uid]
  (-> (getDoc (apply doc @db (conversation-path)))
      (.then
       (fn [conv-snap]
         (let [conversation (decode-snapshot :ha/conversation conv-snap)]
           (-> (js/Promise.all
                (clj->js (mapv #(getDoc (doc @db "members" %)) (:member-ids conversation))))
               (.then
                (fn [member-snaps]
                  (dispatch! [:session/started
                              {:me uid
                               :conversation conversation
                               :members (mapv #(decode-snapshot :ha/member %) member-snaps)
                               :now-ms (js/Date.now)
                               :reduced-motion? (reduced-motion?)}])
                  (watch-messages! dispatch!)
                  (watch-presence! dispatch!)))))))
      (.catch #(log-error "reading the conversation" %))))

(defn init!
  "Connects to the emulators, signs in as the dev user, and starts the
  session. opts: {:dispatch! f :reduced-motion? thunk}."
  [opts]
  (let [{:keys [firebase emulators dev-sign-in]} config
        app  (initializeApp (clj->js firebase))
        auth (getAuth app)]
    (reset! db (getFirestore app))
    (connectAuthEmulator auth (:auth emulators) #js {:disableWarnings true})
    (connectFirestoreEmulator @db (get-in emulators [:firestore :host])
                              (get-in emulators [:firestore :port]))
    (-> (signInWithEmailAndPassword auth (:email dev-sign-in) (:password dev-sign-in))
        (.then #(read-session! opts (.-uid (.-user ^js %))))
        (.catch #(log-error "signing in" %)))))

(defn run-effect!
  "Runs one effect from core."
  [[kind message]]
  (case kind
    :fx/write-message
    (let [wire (-> (codec/encode :ha/message message)
                   (assoc "sentAt" (serverTimestamp)))]
      (-> (setDoc (apply doc @db (concat (conversation-path) ["messages" (:id message)]))
                  (clj->js wire))
          (.catch #(log-error "writing a message" %))))
    (js/console.error "halfahead: unknown effect" (pr-str kind))))
