(ns halfahead.dev.emulator
  "Runs the Firestore rules tests under the emulator on ports chosen per run,
  so parallel factory worktrees never collide."
  (:require [babashka.fs :as fs]
            [babashka.process :refer [shell]]
            [cheshire.core :as json])
  (:import (java.net ServerSocket)))

(defn free-ports
  "n distinct free TCP ports. Every socket stays open until all are chosen, so
  one call never repeats a port."
  [n]
  (let [sockets (vec (repeatedly n #(ServerSocket. 0)))]
    (try
      (mapv #(.getLocalPort ^ServerSocket %) sockets)
      (finally (run! #(.close ^ServerSocket %) sockets)))))

(defn free-port [] (first (free-ports 1)))

(defn emulator-config
  "firebase.json with every listener on one of `ports` (firestore, its
  websocket, hub, logging), the UI off, and an absolute rules path. Hub, logging and the websocket default to
  fixed ports (4400, 4500, 9150) that would collide across parallel runs."
  [base-config rules-path [firestore-port hub-port logging-port websocket-port]]
  (-> base-config
      (assoc-in [:firestore :rules] (str rules-path))
      (assoc-in [:emulators :firestore :port] firestore-port)
      (assoc-in [:emulators :firestore :websocketPort] websocket-port)
      (assoc-in [:emulators :hub] {:host "127.0.0.1" :port hub-port})
      (assoc-in [:emulators :logging] {:host "127.0.0.1" :port logging-port})
      (assoc-in [:emulators :ui] {:enabled false})))

(defn run-rules-tests!
  "Runs `bb test:rules` inside the emulator; exits non-zero if it fails."
  []
  (let [config (emulator-config (json/parse-string (slurp "firebase.json") true)
                                (fs/absolutize "firestore.rules")
                                (free-ports 4))
        tmp    (fs/create-temp-file {:prefix "firebase-" :suffix ".json"})]
    (try
      (spit (fs/file tmp) (json/generate-string config))
      (let [{:keys [exit]} (shell {:continue true}
                                  "npx" "firebase" "emulators:exec"
                                  "--only" "firestore"
                                  "--project" "demo-halfahead"
                                  "--config" (str tmp)
                                  "bb test:rules")]
        (when-not (zero? exit)
          (System/exit exit)))
      (finally (fs/delete-if-exists tmp)))))
