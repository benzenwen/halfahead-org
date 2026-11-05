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

(defn- raw-location
  "The Location header of a raw GET, or nil."
  [port path]
  (with-open [s (Socket. "127.0.0.1" (int port))]
    (.write (.getOutputStream s)
            (.getBytes (str "GET " path " HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n")))
    (.flush (.getOutputStream s))
    (second (re-find #"(?i)location: ([^\r\n]+)" (slurp (.getInputStream s))))))

(deftest directories-serve-their-index-test
  (let [dir  (fs/create-temp-dir {:prefix "serve-test"})
        lab  (fs/create-dirs (fs/path dir "lab"))
        _    (spit (str (fs/path lab "index.html")) "<p>lab</p>")
        _    (fs/create-dirs (fs/path dir "empty"))
        stop (serve/start! {:root (str dir)})
        port (:port (meta stop))]
    (try
      (let [[status type] (raw-get port "/lab/")]
        (is (= 200 status) "a directory with a trailing slash serves its index.html")
        (is (str/starts-with? type "text/html")))
      (is (= 301 (first (raw-get port "/lab"))) "without the slash it redirects")
      (is (= "/lab/" (raw-location port "/lab")))
      (is (= 404 (first (raw-get port "/lab/index.htm"))) "a clipped file name is still not found")
      (is (= 404 (first (raw-get port "/empty/"))) "a directory without index.html is not found")
      (finally
        (stop)
        (fs/delete-tree dir)))))
