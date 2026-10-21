(ns halfahead.worker.engine
  "Engines turn an :ha/engine-request into raw text for core/parse-reply.
  Adding an engine is one defmethod."
  (:require [babashka.process :as process]
            [cheshire.core :as json]
            [clojure.string :as str]
            [halfahead.worker.core :as core]))

(defmulti invoke
  "-> {:raw string :cost-usd number}. Dispatches on (:engine config)."
  (fn [config _request] (:engine config)))

(defmethod invoke :mock
  [_ {:keys [history]}]
  (let [said (:text (last (filter #(= :human (:role %)) history)))]
    {:raw      (json/generate-string {:text     (str "Noted: " said)
                                      :delivery "aside"
                                      :emphasis []})
     :cost-usd 0}))

(def ^:private default-timeout-ms 180000)

(def ^:private isolation-flags
  ;; `claude -p` otherwise inherits the host session's built-in file and shell
  ;; tools and every MCP connector it has, and the agent must only talk.
  ["--tools" "" "--disallowedTools" "mcp__*"])

(defn claude-command
  "The argument vector that asks the claude CLI for one reply, with every
  tool disabled and the persona as the system prompt."
  [_config request]
  (into ["claude" "-p" (core/engine-transcript request)
         "--system-prompt" (core/engine-system-prompt request)]
        (concat isolation-flags ["--output-format" "json"])))

(defn- run-process
  "Runs argv; -> {:exit n :out s :err s}, or {:timeout? true} past timeout-ms."
  [argv timeout-ms]
  (let [p      (process/process argv {:out :string :err :string})
        result (deref p timeout-ms ::timeout)]
    (if (= ::timeout result)
      (do (process/destroy-tree p)
          {:timeout? true})
      (select-keys result [:exit :out :err]))))

(defn- failed! [err]
  (throw (ex-info "engine failed" {:type :ha/engine-failed :engine :claude :err err})))

(defmethod invoke :claude
  [{:keys [timeout-ms process-fn] :or {timeout-ms default-timeout-ms
                                       process-fn run-process}
    :as config} request]
  (let [{:keys [exit out err timeout?]} (process-fn (claude-command config request) timeout-ms)]
    (cond
      timeout?       (failed! (str "timed out after " timeout-ms " ms"))
      (not= 0 exit)  (failed! (if (str/blank? err) (str "exit " exit) (str/trim err)))
      :else
      (let [j (try (json/parse-string out true)
                   (catch Exception e (failed! (str "unreadable output: " (ex-message e)))))]
        (when-not (and (map? j) (string? (:result j)))
          (failed! "output has no result"))
        {:raw (:result j) :cost-usd (:total_cost_usd j)}))))
