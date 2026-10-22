(ns halfahead.config-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [halfahead.config :refer [config]]))

(deftest project-is-a-demo-project
  (is (str/starts-with? (get-in config [:firebase :projectId]) "demo-")))

(deftest every-emulator-endpoint-is-local
  (is (= "127.0.0.1" (get-in config [:emulators :firestore :host])))
  (is (str/starts-with? (get-in config [:emulators :auth]) "http://127.0.0.1:")))
