(ns halfahead.dev.deps-test
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.test :refer [deftest is testing]]
            [halfahead.dev.deps :as deps]))

(defn- record [& pkg-versions]
  {"packages" (into {} (for [[p v] (partition 2 pkg-versions)]
                         [(str "node_modules/" p) {"version" v}]))})

(deftest stale-when-lock-has-a-package-the-install-lacks
  (is (true? (deps/stale? (record "a" "1.0.0" "b" "2.0.0") (record "a" "1.0.0")))))

(deftest stale-when-version-differs
  (is (true? (deps/stale? (record "a" "1.1.0") (record "a" "1.0.0")))))

(deftest fresh-when-records-match-regardless-of-key-order
  (is (false? (deps/stale? (record "a" "1.0.0" "b" "2.0.0")
                           (record "b" "2.0.0" "a" "1.0.0")))))

(deftest stale-when-install-record-is-missing
  (is (true? (deps/stale? (record "a" "1.0.0") nil))))

(deftest fresh-when-install-has-extra-packages
  (is (false? (deps/stale? (record "a" "1.0.0") (record "a" "1.0.0" "x" "9.9.9")))))

(deftest root-and-optional-packages-are-not-required-in-the-install
  (let [lock {"packages" {""                      {"name" "halfahead"}
                          "node_modules/a"        {"version" "1.0.0"}
                          "node_modules/fsevents" {"version" "2.0.0" "optional" true}}}]
    (is (false? (deps/stale? lock (record "a" "1.0.0"))))))

(deftest stale-node-modules?-reads-both-files-from-the-given-directory
  (fs/with-temp-dir [dir {}]
    (let [write! (fn [path m]
                   (fs/create-dirs (fs/parent (fs/path dir path)))
                   (spit (str (fs/path dir path)) (json/generate-string m)))]
      (testing "no install record"
        (write! "package-lock.json" (record "a" "1.0.0"))
        (is (true? (deps/stale-node-modules? dir))))
      (testing "matching install record"
        (write! "node_modules/.package-lock.json" (record "a" "1.0.0"))
        (is (false? (deps/stale-node-modules? dir))))
      (testing "lock moved on"
        (write! "package-lock.json" (record "a" "1.0.1"))
        (is (true? (deps/stale-node-modules? dir)))))))
