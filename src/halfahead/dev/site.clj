(ns halfahead.dev.site
  "What would go to halfahead.org, as a build product that can be checked
  before anything is deployed: the landing page at / and the typing lab's
  release build at /lab/. Nothing of the app, which cannot work without a
  backend. Deploys nothing.

    bb site
    bb site:e2e"
  (:require [babashka.fs :as fs]
            [babashka.process :refer [shell]]
            [halfahead.dev.serve :as serve]))

(def ^:private out-dir "out/site")
(def ^:private lab-js "public/lab/js")

(defn- plan-of
  "The site's files given the files the release build wrote under
  root/public/lab/js. An allowlist: nothing else under public/ is the
  site's. An entry without :from is an empty file; .nojekyll stops GitHub
  Pages from running the site through Jekyll."
  [root js-files]
  (let [js-dir (fs/path root lab-js)]
    (-> [{:from (str (fs/path root "resources/site/index.html")) :to "index.html"}
         {:from (str (fs/path root "public/lab/index.html")) :to "lab/index.html"}]
        (into (map (fn [file]
                     {:from (str file)
                      :to   (str "lab/js/" (fs/unixify (fs/relativize js-dir file)))}))
              js-files)
        (conj {:to ".nojekyll"}))))

(defn plan
  "The files of the site under root as [{:from path :to path}], :to relative
  to the site's root. Throws :ha.site/no-lab-build when the lab has not
  been compiled, so a site without its lab is never assembled."
  [root]
  (let [js-dir   (fs/path root lab-js)
        js-files (when (fs/directory? js-dir)
                   (sort (filter fs/regular-file? (fs/glob js-dir "**"))))]
    (when (empty? js-files)
      (throw (ex-info (str "No lab build under " js-dir "; run bb site")
                      {:type :ha.site/no-lab-build :dir (str js-dir)})))
    (plan-of root js-files)))

(defn assemble!
  "Empties out-dir and copies the plan into it. -> {:files n :bytes n} of
  what was written."
  [plan out-dir]
  (fs/delete-tree out-dir)
  (fs/create-dirs out-dir)
  (doseq [{:keys [from to]} plan
          :let [target (fs/path out-dir to)]]
    (fs/create-dirs (fs/parent target))
    (if from
      (fs/copy from target)
      (fs/create-file target)))
  {:files (count plan)
   :bytes (transduce (map #(fs/size (fs/path out-dir (:to %)))) + 0 plan)})

(defn- build!
  "Release-compiles the lab and assembles the site into out/site."
  []
  ;; A development build leaves cljs-runtime/ here, which would otherwise
  ;; be planned into the site beside the release's main.js.
  (fs/delete-tree lab-js)
  (shell "npx" "shadow-cljs" "release" "lab")
  (let [{:keys [files bytes]} (assemble! (plan ".") out-dir)]
    (println (str "Site: " out-dir " (" files " files, " bytes " bytes)"))
    (println (str lab-js " now holds the release build; `bb lab` and `bb lab:e2e` recompile it."))))

(defn e2e!
  "Builds the site, serves out/site, runs the lab's browser check
  (e2e/lab.mjs) against the release build at /lab/, stops the server.
  Exits with the script's exit code."
  []
  (build!)
  ;; e2e/lab.mjs writes its screenshots here, relative to the working directory.
  (fs/create-dirs "out/lab")
  (let [stop (serve/start! {:root out-dir})
        exit (try
               (:exit (shell {:continue true} "node" "e2e/lab.mjs"
                             (str "http://127.0.0.1:" (:port (meta stop)) "/lab/")))
               (finally
                 (stop)
                 (println "static server stopped")))]
    (System/exit exit)))

(defn -main [& _]
  (build!))
