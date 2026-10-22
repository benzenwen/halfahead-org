(ns halfahead.worker.main
  "The agent's turn: watch the agent's conversations and answer the ones
  waiting on it, showing presence while it works. All effects arrive in
  `deps`, so step! is tested with in-memory fakes."
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.string :as str]
            [halfahead.worker.core :as core]
            [halfahead.worker.engine :as engine]
            [halfahead.worker.store :as store]))

(def ledger-path ".halfahead/worker-ledger.jsonl")

(defn read-ledger!
  "The ledger's entries; a missing file is an empty ledger and lines that
  are not JSON are dropped."
  ([] (read-ledger! ledger-path))
  ([path]
   (if (fs/exists? path)
     (into []
           (keep #(try (json/parse-string % true) (catch Exception _ nil)))
           (str/split-lines (slurp path)))
     [])))

(defn default-log!
  "Prints one line and appends a JSON line to the ledger."
  ([event] (default-log! ledger-path event))
  ([path event]
   (let [line (json/generate-string (assoc event :at (str (java.time.Instant/now))))]
     (when-let [dir (fs/parent path)] (fs/create-dirs dir))
     (spit path (str line "\n") :append true)
     (println (str "[worker] "
                   (cond (:replied-to event) (:replied-to event)
                         (:capped event)     "capped"
                         :else               "error")
                   " in " (:conversation event)
                   (when (:capped event) (str " ($" (:spent event) "/$" (:cap event) ")"))
                   (when-let [c (:cost-usd event)] (str " ($" c ")"))
                   (when-let [e (:error event)] (str " " (pr-str e))))))))

(defn- error-info [e]
  (or (some-> (ex-data e) (select-keys [:type :engine :err :entity]))
      {:message (ex-message e)}))

(defn- presence [agent-id conversation-id state now]
  {:member-id agent-id :conversation-id conversation-id :state state :since (now)})

(defn- answer!
  "Replies to `to`; -> the result entry. Throws on any failure."
  [{:keys [agent-id persona engine-config members! write-message! invoke new-id now]}
   conversation messages to]
  (let [request (core/engine-request persona (members! (:member-ids conversation)) messages)
        {:keys [raw cost-usd]} (invoke engine-config request)
        reply   (core/parse-reply raw)
        msg     (core/reply-message {:reply reply :to to :agent-id agent-id
                                     :id (new-id) :now (now)})]
    (write-message! msg)
    (cond-> {:conversation (:id conversation) :replied-to (:id to) :reply-id (:id msg)}
      cost-usd (assoc :cost-usd cost-usd))))

(defn- take-turn!
  [{:keys [agent-id write-presence! log! now engine-config] :as deps} conversation to messages]
  (let [cid (:id conversation)
        entry (try
                (write-presence! (presence agent-id cid :thinking now))
                (answer! deps conversation messages to)
                (catch Exception e
                  {:conversation cid :error (error-info e)}))]
    (try (write-presence! (presence agent-id cid :idle now))
         (catch Exception e
           (log! {:conversation cid :error (assoc (error-info e) :while :idle)})))
    (log! (assoc entry :engine (:engine engine-config)))
    entry))

(defn- cap-turn!
  "Out of budget: says so once per UTC day, without the engine or presence.
  -> the log entry, or nil when it already said so. Quiet turns are not logged:
  the waiting message re-triggers this on every pass."
  [{:keys [agent-id write-message! log! new-id now]} conversation to messages spent cap]
  (let [cid (:id conversation)
        day (core/utc-day (now))]
    (when-not (core/budget-reply-sent? agent-id messages day)
      (let [entry (try
                    (write-message! (core/budget-reply {:to to :agent-id agent-id
                                                        :id (new-id) :now (now)}))
                    {:conversation cid :capped true :spent spent :cap cap}
                    (catch Exception e
                      {:conversation cid :error (error-info e)}))]
        (log! entry)
        entry))))

(defn step!
  "One pass over the agent's conversations. -> a vector of what happened."
  [{:keys [agent-id conversations-of! messages!] :as deps}]
  (let [{:keys [cap-usd read-ledger! now] :as deps}
        (merge {:invoke engine/invoke :now #(java.util.Date.)
                :new-id #(str (random-uuid)) :log! default-log!
                :read-ledger! read-ledger!}
               deps)]
    (into []
          (keep (fn [conversation]
                  (let [messages (messages! (:id conversation))]
                    (when-let [to (core/awaiting-reply agent-id messages)]
                      (let [spent (when cap-usd
                                    (core/spent-today (read-ledger!) (core/utc-day (now))))]
                        (if (core/over-cap? spent cap-usd)
                          (cap-turn! deps conversation to messages spent cap-usd)
                          (take-turn! deps conversation to messages)))))))
          (conversations-of! agent-id))))

(defn run!
  "Loops step! forever. A failed pass (say, the emulator is down) is logged
  and retried on the next."
  [deps {:keys [interval-ms] :or {interval-ms 2000}}]
  (let [log! (or (:log! deps) default-log!)]
    (loop []
      (try (step! deps)
           (catch Exception e (log! {:conversation nil :error (error-info e)})))
      (Thread/sleep (long interval-ms))
      (recur))))

(defn- parse-args [args]
  (loop [[k v & more :as args] args, opts {}]
    (if (empty? args)
      opts
      (case k
        "--agent"   (recur more (assoc opts :agent v))
        "--engine"  (recur more (assoc opts :engine (keyword v)))
        "--persona" (recur more (assoc opts :persona v))
        (throw (ex-info (str "unknown argument " k) {:type :ha/bad-args}))))))

(defn parse-cap
  "The daily cap from its raw text. nil/blank = no cap. Anything but a finite
  number >= 0 is refused, so a typo can never switch the cap off."
  [raw]
  (let [raw (some-> raw str/trim)]
    (when-not (str/blank? raw)
      (let [n (try (parse-double raw) (catch Exception _ nil))]
        (if (and n (not (Double/isNaN n)) (not (Double/isInfinite n)) (>= n 0))
          n
          (throw (ex-info (str "HALFAHEAD_WORKER_CAP_USD must be a number >= 0: " raw)
                          {:type :ha/bad-args})))))))

(defn- bind-store [cfg f] (fn [& args] (apply f cfg args)))

(defn -main [& args]
  (let [{:keys [agent engine persona]
         :or {engine :mock persona "resources/agent/persona.md"}} (parse-args args)
        cfg {:project-id store/default-project}]
    (when-not agent
      (throw (ex-info "usage: bb worker --agent <id> [--engine mock|claude] [--persona path]"
                      {:type :ha/bad-args})))
    (when-not (#{:mock :claude} engine)
      (throw (ex-info (str "unknown engine " engine) {:type :ha/bad-args})))
    (when-not (fs/exists? persona)
      (throw (ex-info (str "persona file not found: " persona) {:type :ha/bad-args})))
    (store/assert-emulator! cfg)
    (let [cap (parse-cap (System/getenv "HALFAHEAD_WORKER_CAP_USD"))]
      (println (if cap (str "spend cap: $" cap "/day") "spend cap: none"))
      (run! {:agent-id          agent
             :cap-usd           cap
             :persona           (str/trim (slurp persona))
             :engine-config     {:engine engine}
             :conversations-of! (bind-store cfg store/conversations-of!)
             :members!          (bind-store cfg store/members!)
             :messages!         (bind-store cfg store/messages!)
             :write-message!    (bind-store cfg store/write-message!)
             :write-presence!   (bind-store cfg store/write-presence!)}
            {}))))
