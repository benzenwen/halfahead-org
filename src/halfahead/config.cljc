(ns halfahead.config
  "The emulator configuration as data. Nothing here is secret: the project
  is a demo- project and the dev password only ever works against the Auth
  emulator.")

(def config
  {:firebase        {:apiKey     "demo-key"
                     :projectId  "demo-halfahead"
                     :authDomain "demo-halfahead.firebaseapp.com"}
   :emulators       {:firestore {:host "127.0.0.1" :port 8080}
                     :auth      "http://127.0.0.1:9099"}
   :conversation-id "first"
   :dev-sign-in     {:email    "ben@halfahead.test"
                     :password "halfahead-dev"}})
