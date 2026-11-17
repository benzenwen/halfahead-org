(ns halfahead.dev.site-test
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [halfahead.dev.site :as site]))

(defn- write! [root path content]
  (let [file (fs/path root path)]
    (fs/create-dirs (fs/parent file))
    (spit (str file) content)))

(defn- fake-root
  "A repository in miniature: the site's sources, a lab build, and the app."
  []
  (let [root (fs/create-temp-dir {:prefix "site-test"})]
    (write! root "resources/site/index.html" "<h1>landing</h1>")
    (write! root "public/lab/index.html" "<p>lab</p>")
    (write! root "public/lab/js/main.js" "lab();")
    (write! root "public/lab/js/sub/chunk.js" "chunk();")
    (write! root "public/index.html" "<p>app</p>")
    (write! root "public/js/app/main.js" "app();")
    (write! root "public/css/halfahead.css" "body {}")
    root))

(defn- files-under [dir]
  (set (map #(fs/unixify (fs/relativize dir %))
            (filter fs/regular-file? (fs/glob dir "**" {:hidden true})))))

(def ^:private site-files
  #{"index.html" "lab/index.html" "lab/js/main.js" "lab/js/sub/chunk.js" ".nojekyll"})

(deftest plan-includes-the-site-and-excludes-the-app-test
  (let [root (fake-root)]
    (try
      (let [plan (site/plan (str root))
            from (into {} (map (juxt :to :from)) plan)]
        ;; Exact equality: everything of the site, and nothing of the app.
        (is (= site-files (set (map :to plan))))
        (is (= (count site-files) (count plan)))
        (is (= (str (fs/path root "resources/site/index.html")) (from "index.html")))
        (is (= (str (fs/path root "public/lab/index.html")) (from "lab/index.html")))
        (is (= (str (fs/path root "public/lab/js/sub/chunk.js")) (from "lab/js/sub/chunk.js")))
        (is (nil? (from ".nojekyll"))))
      (finally (fs/delete-tree root)))))

(deftest plan-without-a-lab-build-throws-test
  (let [root (fake-root)]
    (try
      (fs/delete-tree (fs/path root "public/lab/js"))
      (is (= :ha.site/no-lab-build
             (try (site/plan (str root))
                  (catch clojure.lang.ExceptionInfo e (:type (ex-data e))))))
      (finally (fs/delete-tree root)))))

(deftest landing-page-test
  (let [file "resources/site/index.html"]
    (is (fs/regular-file? file))
    (let [html (slurp file)]
      (is (str/includes? html "<h1>Half Ahead</h1>"))
      (is (str/includes? html "<p>A community for AI agentic engineers. Part tradecraft, part support group.</p>"))
      (is (str/includes? html "<a href=\"lab/\">Try the typing lab</a>"))
      (is (str/includes? html "The lab runs in your browser. What you type never leaves it."))
      (is (str/includes? html "<title>Half Ahead</title>"))
      (is (str/includes? html (str "<meta name=\"description\" content=\"A community for AI agentic "
                                   "engineers. Part tradecraft, part support group.\">")))
      (is (str/includes? html "prefers-color-scheme: dark"))
      (is (not (re-find #"(?i)<script" html)))
      (is (not (re-find #"(?i)https?:" html))))))

(deftest assemble-reproduces-the-plan-and-empties-the-target-test
  (let [root (fake-root)
        out  (fs/path root "out/site")]
    (try
      (write! out "stale.txt" "old")
      (write! out "old/x.js" "old();")
      (write! out "lab/js/cljs-runtime/dev.js" "dev();")
      (let [plan   (site/plan (str root))
            result (site/assemble! plan (str out))]
        (is (= site-files (files-under out)))
        (is (= "<h1>landing</h1>" (slurp (str (fs/path out "index.html")))))
        (is (= "<p>lab</p>" (slurp (str (fs/path out "lab/index.html")))))
        (is (= "lab();" (slurp (str (fs/path out "lab/js/main.js")))))
        (is (= "chunk();" (slurp (str (fs/path out "lab/js/sub/chunk.js")))))
        (is (zero? (fs/size (fs/path out ".nojekyll"))))
        (is (= {:files 5
                :bytes (count (str "<h1>landing</h1>" "<p>lab</p>" "lab();" "chunk();"))}
               result)))
      (finally (fs/delete-tree root)))))
