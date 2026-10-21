(ns halfahead.dev.serve
  "A static file server for the e2e smoke test: serves a directory and
  nothing outside it."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [halfahead.dev.emulator :as emulator]
            [org.httpkit.server :as http])
  (:import (java.net URLDecoder)
           (java.nio.charset StandardCharsets)))

(def ^:private content-types
  {"html" "text/html; charset=utf-8"
   "js"   "text/javascript; charset=utf-8"
   "css"  "text/css; charset=utf-8"
   "png"  "image/png"
   "svg"  "image/svg+xml"
   "json" "application/json"
   "map"  "application/json"})

(def ^:private not-found {:status 404 :headers {"Content-Type" "text/plain"} :body "not found"})

(defn- requested-file
  "The file a raw request uri names under root, or nil when it is malformed
  or reaches outside root. Decoding comes first so %2e%2e is caught too."
  [root uri]
  (try
    (let [path     (URLDecoder/decode (first (str/split uri #"[?#]")) StandardCharsets/UTF_8)
          segments (remove str/blank? (str/split path #"[/\\]"))]
      (when-not (some #{".."} segments)
        (let [file (fs/normalize (apply fs/path root (if (seq segments) segments ["index.html"])))]
          (when (fs/starts-with? file root) file))))
    (catch IllegalArgumentException _ nil)))

(defn handler
  "Ring handler serving files under root."
  [root]
  (let [root (fs/normalize (fs/absolutize root))]
    (fn [{:keys [uri]}]
      (let [file (requested-file root uri)]
        (if (and file (fs/regular-file? file))
          {:status  200
           :headers {"Content-Type" (get content-types (fs/extension file) "application/octet-stream")}
           :body    (fs/file file)}
          not-found)))))

(defn start!
  "Serves root on port (a free one when omitted). Returns the stop function;
  its metadata carries the :port."
  [{:keys [root port]}]
  (let [port (or port (emulator/free-port))
        stop (http/run-server (handler root) {:port port :ip "127.0.0.1"})]
    (with-meta stop {:port port})))
