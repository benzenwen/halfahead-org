(ns halfahead.dev.play
  "Try Half Ahead locally with one command: emulators up and seeded, the
  worker answering as Scout, the app compiled and served.

    bb play [--engine mock|claude] [--port n]"
  (:require [babashka.fs :as fs]
            [babashka.process :as p :refer [shell]]
            [clojure.string :as str]
            [halfahead.dev.serve :as serve])
  (:import (java.net ServerSocket)))

(def ^:private defaults {:engine :claude :port 8000})
(def ^:private engines #{:mock :claude})

(defn- bad-args [message]
  (ex-info message {:type :ha/bad-args}))

(defn parse-args
  "Options from command-line args. Unknown flags, engines and ports throw
  ex-info {:type :ha/bad-args}."
  [args]
  (loop [[k v & more :as args] args, opts defaults]
    (if (empty? args)
      opts
      (case k
        "--engine" (let [engine (some-> v keyword)]
                     (when-not (engines engine)
                       (throw (bad-args (str "--engine takes mock or claude, not " (pr-str v)))))
                     (recur more (assoc opts :engine engine)))
        "--port"   (let [port (some->> v (re-matches #"\d{1,5}") parse-long)]
                     (when-not (and port (<= 1 port 65535))
                       (throw (bad-args (str "--port takes a port number, not " (pr-str v)))))
                     (recur more (assoc opts :port port)))
        (throw (bad-args (str "unknown argument " k)))))))

(defn- opts->args [{:keys [engine port]}]
  ["--engine" (name engine) "--port" (str port)])

(defn first-free-port
  "The first port at or after start for which free? holds."
  [start free?]
  (first (filter free? (range start 65536))))

(defn- port-free? [port]
  (try (with-open [_ (ServerSocket. port 1 (java.net.InetAddress/getByName "127.0.0.1"))] true)
       (catch java.io.IOException _ false)))

(defn- once
  "A thunk that runs f the first time and never again."
  [f]
  (let [ran? (atom false)]
    #(when (compare-and-set! ran? false true) (f))))

(defn- run-step! [what & args]
  (let [{:keys [exit]} (apply shell {:continue true} args)]
    (when-not (zero? exit)
      (throw (ex-info (str what " failed") {:type :ha/play-step-failed :exit exit})))))

(defn- on-path? [exe]
  (boolean (fs/which exe)))

(defn inner!
  "Runs inside the emulators: compile, seed, start the worker and the
  static server, then wait until stopped."
  [args]
  (let [{:keys [engine port]} (parse-args args)]
    (when (and (= engine :claude) (not (on-path? "claude")))
      (throw (ex-info "the claude CLI is not on PATH; install it, or try bb play --engine mock"
                      {:type :ha/play-step-failed})))
    (run-step! "compile" "npx" "shadow-cljs" "compile" "app")
    (run-step! "seed" "bb" "-m" "halfahead.dev.seed")
    (let [worker (p/process {:inherit true}
                            "bb" "worker" "--agent" "scout" "--engine" (name engine))
          stop   (serve/start! {:root "public" :port (first-free-port port port-free?)})
          cleanup! (once (fn []
                           (stop)
                           (p/destroy-tree worker)))
          done   (promise)]
      (.addShutdownHook (Runtime/getRuntime) (Thread. ^Runnable cleanup!))
      (future (let [{:keys [exit]} @worker]
                (println "Half Ahead: the worker stopped (exit" exit "); stopping")
                (deliver done exit)))
      (println (str "Half Ahead is at http://127.0.0.1:" (:port (meta stop)) "/ — Ctrl-C to stop"))
      (flush)
      (let [exit @done]
        (cleanup!)
        (System/exit (if (zero? exit) 1 exit))))))

(defn -main [& args]
  (let [opts   (parse-args args)
        emu    (p/process {:inherit true}
                          "npx" "firebase" "emulators:exec"
                          "--only" "firestore,auth"
                          "--project" "demo-halfahead"
                          (str "bb play:inner " (str/join " " (opts->args opts))))
        cleanup! (once #(p/destroy-tree emu))]
    ;; SIGTERM to this bb does not reach the emulator JVMs on its own.
    (.addShutdownHook (Runtime/getRuntime) (Thread. ^Runnable cleanup!))
    (System/exit (:exit @emu))))
