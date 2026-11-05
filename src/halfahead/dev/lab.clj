(ns halfahead.dev.lab
  "The typing lab, locally: compile the :lab build, serve public/ on a free
  port, and either print the page's URL (bb lab) or drive it in headless
  Chromium (bb lab:e2e, e2e/lab.mjs). No emulators, no network.

    bb lab
    bb lab:e2e"
  (:require [babashka.fs :as fs]
            [babashka.process :refer [shell]]
            [halfahead.dev.serve :as serve]))

(def ^:private out-dir "out/lab")
(def ^:private screenshots ["typing-mid.png" "embers.png" "settled.png"])

(defn- compile! []
  (shell "npx" "shadow-cljs" "compile" "lab"))

(defn- lab-url
  "The lab page under a stop function from serve/start!. The static server
  only maps / to index.html, so the page is named in full."
  [stop]
  (str "http://127.0.0.1:" (:port (meta stop)) "/lab/index.html"))

(defn- e2e-exit
  "The e2e script's exit code, or 1 when it passed but left a screenshot out."
  [url]
  (let [{:keys [exit]} (shell {:continue true} "node" "e2e/lab.mjs" url)]
    (if (and (zero? exit) (not-every? #(fs/exists? (fs/path out-dir %)) screenshots))
      (do (println "lab:e2e: a screenshot is missing from" out-dir) 1)
      exit)))

(defn e2e!
  "Compiles, serves, runs e2e/lab.mjs against the page, stops the server.
  Exits with the script's exit code."
  []
  (compile!)
  (fs/create-dirs out-dir)
  (let [stop (serve/start! {:root "public"})
        exit (try
               (e2e-exit (lab-url stop))
               (finally
                 (stop)
                 (println "static server stopped")))]
    (System/exit exit)))

(defn -main [& _]
  (compile!)
  (let [stop (serve/start! {:root "public"})]
    (.addShutdownHook (Runtime/getRuntime) (Thread. ^Runnable (fn [] (stop))))
    (println "Typing lab:" (lab-url stop))
    (println "Ctrl-C stops the server.")
    @(promise)))
