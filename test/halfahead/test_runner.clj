(ns halfahead.test-runner
  "Discovers every test/**/*_test.clj and *_test.cljc namespace and runs it
  under Babashka, so parallel work orders add test files without editing a
  shared runner."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [clojure.test :as t]))

(defn- file->ns-sym [root f]
  (-> (str (fs/relativize root f))
      (str/replace #"\.cljc?$" "")
      (str/replace "/" ".")
      (str/replace "_" "-")
      symbol))

(defn test-namespaces
  "Sorted namespace symbols for every *_test.clj or *_test.cljc under root."
  [root]
  (->> (concat (fs/glob root "**_test.clj") (fs/glob root "**_test.cljc"))
       (map #(file->ns-sym root %))
       distinct
       sort
       vec))

(defn run []
  (let [nss (test-namespaces "test")]
    (when (empty? nss)
      (println "no test namespaces found under test/")
      (System/exit 1))
    (apply require nss)
    (let [{:keys [fail error]} (apply t/run-tests nss)]
      (when (pos? (+ fail error))
        (System/exit 1)))))
