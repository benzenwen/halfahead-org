(ns halfahead.dev.deps
  "Decides whether node_modules still matches package-lock.json, so `bb deps`
  reinstalls after a pull changes the lock instead of only on a fresh clone."
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]))

(defn- required-versions
  "package path -> version for what npm must have installed. The root entry
  is the project itself and optional packages (platform-specific, like
  fsevents) are legitimately absent from the installed record."
  [lock]
  (into {}
        (for [[path {:strs [version optional]}] (get lock "packages")
              :when (and (seq path) (not optional))]
          [path version])))

(defn stale?
  "True when `installed` (the parsed node_modules/.package-lock.json, nil
  when the file is missing) lacks a package that `lock` (the parsed
  package-lock.json) has, or has it at a different version. Key order is
  irrelevant."
  [lock installed]
  (or (nil? installed)
      (let [have (get installed "packages")]
        (boolean (some (fn [[path version]]
                         (not= version (get-in have [path "version"])))
                       (required-versions lock))))))

(defn- read-json [path]
  (when (fs/exists? path)
    (json/parse-string (slurp (str path)))))

(defn stale-node-modules?
  "`stale?` for the lock and the npm install record found under `dir`."
  [dir]
  (stale? (read-json (fs/path dir "package-lock.json"))
          (read-json (fs/path dir "node_modules" ".package-lock.json"))))
