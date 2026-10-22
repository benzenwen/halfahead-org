(ns halfahead.dev.play-test
  (:require [clojure.test :refer [deftest is testing]]
            [halfahead.dev.play :as play]))

(defn- bad-args? [args]
  (try (play/parse-args args) false
       (catch clojure.lang.ExceptionInfo e (= :ha/bad-args (:type (ex-data e))))))

(deftest parse-args-defaults-and-flags
  (is (= {:engine :claude :port 8000} (play/parse-args [])))
  (is (= {:engine :mock :port 8000} (play/parse-args ["--engine" "mock"])))
  (is (= {:engine :claude :port 9000} (play/parse-args ["--port" "9000"])))
  (is (= {:engine :mock :port 9000}
         (play/parse-args ["--port" "9000" "--engine" "mock"]))))

(deftest parse-args-rejects-bad-input
  (testing "each of these throws :ha/bad-args"
    (doseq [args [["--bogus"] ["--engine" "gpt"] ["--engine"] ["--port" "x"] ["--port"] ["--port" "0"]]]
      (is (bad-args? args) (pr-str args)))))

(deftest first-free-port-skips-busy-ports
  (is (= 8000 (play/first-free-port 8000 (constantly true))))
  (is (= 8002 (play/first-free-port 8000 (complement #{8000 8001})))))
