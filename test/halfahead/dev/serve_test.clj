(ns halfahead.dev.serve-test
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [halfahead.dev.serve :as serve])
  (:import (java.net Socket)))

(defn- raw-get
  "Sends the request line verbatim, so no client normalises the path.
  -> [status content-type]"
  [port path]
  (with-open [s (Socket. "127.0.0.1" (int port))]
    (.write (.getOutputStream s)
            (.getBytes (str "GET " path " HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n")))
    (.flush (.getOutputStream s))
    (let [text (slurp (.getInputStream s))]
      [(parse-long (second (re-find #"^HTTP/1\.1 (\d+)" text)))
       (second (re-find #"(?i)content-type: ([^\r\n]+)" text))])))

(deftest serves-files-under-root-only-test
  (let [dir    (fs/create-temp-dir {:prefix "serve-test"})
        root   (fs/create-dirs (fs/path dir "root"))
        _      (spit (str (fs/path dir "bb.edn")) "secret")
        _      (spit (str (fs/path root "index.html")) "<p>hi</p>")
        stop   (serve/start! {:root (str root)})
        port   (:port (meta stop))]
    (try
      (let [[status type] (raw-get port "/index.html")]
        (is (= 200 status))
        (is (str/starts-with? type "text/html")))
      (is (= 200 (first (raw-get port "/"))))
      (is (= 404 (first (raw-get port "/missing.js"))))
      (is (= 404 (first (raw-get port "/../bb.edn"))))
      (is (= 404 (first (raw-get port "/%2e%2e/bb.edn"))))
      (finally
        (stop)
        (fs/delete-tree dir)))))
