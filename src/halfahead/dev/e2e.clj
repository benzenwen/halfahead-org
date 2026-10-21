(ns halfahead.dev.e2e
  "The end-to-end smoke: emulators up and seeded, the worker answering with
  the :mock engine, the app served locally, and a headless Chromium driving
  it (e2e/smoke.mjs).

    bb e2e"
  (:require [babashka.fs :as fs]
            [babashka.process :as p :refer [shell]]
            [halfahead.dev.serve :as serve]))

(def ^:private out-dir "out/e2e")
(def ^:private screenshots ["conversation.png" "reduced-motion.png"])

(defn- log-file [name] (fs/file out-dir name))

(defn- once
  "A thunk that runs f the first time and never again."
  [f]
  (let [ran? (atom false)]
    #(when (compare-and-set! ran? false true) (f))))

(defn- run-logged!
  "Runs a command to completion with its output in out/e2e/<log>; -> exit code."
  [log & args]
  (:exit (apply shell {:out (log-file log) :err :out :continue true} args)))

(defn- start-worker! []
  (p/process {:out (log-file "worker.log") :err :out}
             "bb" "worker" "--agent" "scout" "--engine" "mock"))

(defn- smoke-exit
  "The smoke test's exit code, or 1 when it passed but left a screenshot out."
  [url]
  (let [{:keys [exit]} (shell {:continue true} "node" "e2e/smoke.mjs" url)]
    (if (and (zero? exit) (not-every? #(fs/exists? (fs/path out-dir %)) screenshots))
      (do (println "e2e: a screenshot is missing from" out-dir) 1)
      exit)))

(defn inner!
  "Runs inside the emulators. Exits with the smoke test's exit code."
  []
  (fs/create-dirs out-dir)
  (let [worker    (atom nil)
        stop      (atom nil)
        stop-srv! (once #(when-let [f @stop] (f) (println "static server stopped")))
        stop-wkr! (once #(when-let [w @worker]
                           (p/destroy-tree w)
                           @w
                           (println "worker pid" (.pid (:proc w)) "stopped")))
        exit      (try
                    (let [compiled (run-logged! "compile.log" "npx" "shadow-cljs" "compile" "app")
                          seeded   (when (zero? compiled)
                                     (run-logged! "seed.log" "bb" "-m" "halfahead.dev.seed"))]
                      (if-not (and (zero? compiled) (zero? seeded))
                        (do (println "e2e: compile or seed failed; see" out-dir) 1)
                        (do (reset! worker (start-worker!))
                            (reset! stop (serve/start! {:root "public"}))
                            (smoke-exit (str "http://127.0.0.1:" (:port (meta @stop)) "/")))))
                    (finally
                      (stop-srv!)
                      (stop-wkr!)))]
    (when (some-> @worker :proc .isAlive)
      (println "e2e: worker still alive after cleanup")
      (System/exit 1))
    (System/exit exit)))

(defn -main [& _]
  (fs/create-dirs out-dir)
  (let [{:keys [exit]} (shell {:continue true}
                              "npx" "firebase" "emulators:exec"
                              "--only" "firestore,auth"
                              "--project" "demo-halfahead"
                              "bb e2e:inner")]
    (System/exit exit)))
