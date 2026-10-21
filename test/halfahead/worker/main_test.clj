(ns halfahead.worker.main-test
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [halfahead.schema :as schema]
            [halfahead.worker.main :as main])
  (:import (java.util Date)))

(def members
  [{:id "ann" :kind :human :display-name "Ann" :joined-at (Date. 0)}
   {:id "bot" :kind :agent :display-name "Bot" :owner-id "ann" :joined-at (Date. 0)}])

(defn- human-msg [cid n]
  {:id (str cid "-m" n) :conversation-id cid :author-id "ann" :text (str "hello " cid)
   :delivery :plain :emphasis [] :sent-at (Date. (* 1000 n))})

(defn- fake-deps
  "In-memory store; :store holds messages by conversation, :presence logs
  [conversation state]."
  [& {:as overrides}]
  (let [store (atom {"c1" [(human-msg "c1" 1)] "c2" [(human-msg "c2" 1)]})
        presence (atom [])
        logs (atom [])
        clock (atom 100)]
    (merge
     {:agent-id          "bot"
      :persona           "Be kind."
      :engine-config     {:engine :mock}
      :conversations-of! (fn [_] (for [id (sort (keys @store))] {:id id :member-ids ["ann" "bot"]}))
      :members!          (fn [_] members)
      :messages!         (fn [cid] (get @store cid))
      :write-message!    (fn [m] (swap! store update (:conversation-id m) conj m))
      :write-presence!   (fn [p] (swap! presence conj [(:conversation-id p) (:state p)]))
      :now               #(Date. (* 1000 (swap! clock inc)))
      :new-id            #(str "r" (swap! clock inc))
      :log!              #(swap! logs conj %)
      :store store :presence presence :logs logs}
     overrides)))

(deftest reply-and-presence-test
  (let [deps (fake-deps)
        out  (main/step! deps)
        [reply] (filter #(= "bot" (:author-id %)) (@(:store deps) "c1"))]
    (is (= 2 (count out)))
    (is (= 1 (count (filter #(= "bot" (:author-id %)) (@(:store deps) "c1")))))
    (is (schema/valid? :ha/message reply))
    (is (= "c1-m1" (:in-reply-to reply)))
    (is (= "Noted: hello c1" (:text reply)))
    (is (= [["c1" :thinking] ["c1" :idle] ["c2" :thinking] ["c2" :idle]] @(:presence deps)))
    (is (= {:conversation "c1" :replied-to "c1-m1" :cost-usd 0}
           (select-keys (first out) [:conversation :replied-to :cost-usd])))
    (testing "second pass writes nothing"
      (reset! (:presence deps) [])
      (is (= [] (main/step! deps)))
      (is (= [] @(:presence deps)))
      (is (= 2 (count (@(:store deps) "c1")))))))

(deftest failure-isolation-test
  (let [real (fn [cfg req] (throw (ex-info "x" {:type :ha/engine-failed :engine :claude :err "boom"})))
        calls (atom 0)
        deps (fake-deps :invoke (fn [cfg req]
                                  (if (= 1 (swap! calls inc))
                                    (real cfg req)
                                    (halfahead.worker.engine/invoke cfg req))))
        out  (main/step! deps)]
    (is (= {:type :ha/engine-failed :engine :claude :err "boom"} (:error (first out))))
    (is (= 1 (count (@(:store deps) "c1"))))
    (is (= "bot" (:author-id (last (@(:store deps) "c2")))))
    (is (= [["c1" :thinking] ["c1" :idle] ["c2" :thinking] ["c2" :idle]] @(:presence deps)))
    (is (some :error @(:logs deps)))
    (is (some :replied-to @(:logs deps)))))

(deftest failing-presence-write-test
  (let [deps (fake-deps)
        presence (:presence deps)
        deps (assoc deps :write-presence!
                    (fn [p] (if (= :idle (:state p))
                              (throw (ex-info "down" {}))
                              (swap! presence conj [(:conversation-id p) (:state p)]))))
        out (main/step! deps)]
    (is (= 2 (count out)))
    (is (= 2 (count (@(:store deps) "c2"))))))

(deftest default-log-test
  (let [dir (str (fs/create-temp-dir))
        path (str dir "/sub/ledger.jsonl")]
    (with-out-str (main/default-log! path {:conversation "c1" :replied-to "m" :engine :mock :cost-usd 0}))
    (let [row (json/parse-string (slurp path) true)]
      (is (= "c1" (:conversation row)))
      (is (= "m" (:replied-to row)))
      (is (string? (:at row))))))

(deftest worker-task-listed-test
  (let [tasks (:tasks (edn/read-string (slurp "bb.edn")))]
    (is (string? (:doc (get tasks 'worker))))))

(defn- ledger-entry [at cost] {:at at :cost-usd cost})
(def ^:private today-entry (ledger-entry "1970-01-01T00:00:10Z" 0.06))

(deftest capped-step-test
  (let [deps (fake-deps :cap-usd 0.05
                        :read-ledger! (constantly [today-entry])
                        :invoke (fn [& _] (throw (AssertionError. "engine called"))))
        out (main/step! deps)
        replies (fn [cid] (filter #(= "bot" (:author-id %)) (@(:store deps) cid)))]
    (is (= 2 (count out)))
    (is (= 1 (count (replies "c1"))))
    (let [r (first (replies "c1"))]
      (is (schema/valid? :ha/message r))
      (is (= :aside (:delivery r)))
      (is (= "I'm out of budget for today, so I'll stay quiet until tomorrow (UTC). My owner can raise the cap."
             (:text r))))
    (is (= [] @(:presence deps)))
    (is (= {:conversation "c1" :capped true :spent 0.06 :cap 0.05}
           (first @(:logs deps))))
    (testing "another human message in the same day gets silence"
      (swap! (:store deps) update "c1" conj (assoc (human-msg "c1" 2) :id "c1-m2"
                                                   :sent-at (Date. 500000)))
      (reset! (:logs deps) [])
      (is (= [] (main/step! deps)))
      (is (= 1 (count (replies "c1"))))
      (is (= [] @(:logs deps))))))

(deftest cap-releases-test
  (testing "spend only from yesterday does not count"
    (let [deps (fake-deps :cap-usd 0.05
                          :read-ledger! (constantly [(ledger-entry "1969-12-31T23:59:59Z" 5)]))]
      (is (= 2 (count (main/step! deps))))
      (is (= "Noted: hello c1" (:text (last (@(:store deps) "c1")))))
      (is (= [["c1" :thinking] ["c1" :idle] ["c2" :thinking] ["c2" :idle]] @(:presence deps)))))
  (testing "spend under the cap does not stop the agent"
    (let [deps (fake-deps :cap-usd 1 :read-ledger! (constantly [today-entry]))]
      (main/step! deps)
      (is (= "Noted: hello c1" (:text (last (@(:store deps) "c1"))))))))

(deftest no-cap-test
  (let [deps (fake-deps :read-ledger! (fn [] (throw (ex-info "ledger read" {}))))]
    (is (= 2 (count (main/step! deps))))
    (is (= "Noted: hello c1" (:text (last (@(:store deps) "c1")))))))

(deftest read-ledger-test
  (let [dir (str (fs/create-temp-dir))
        path (str dir "/ledger.jsonl")]
    (is (= [] (main/read-ledger! path)))
    (spit path (str "{\"at\":\"2026-10-21T00:00:00Z\",\"cost-usd\":0.5}\n"
                    "not json\n\n"))
    (is (= [{:at "2026-10-21T00:00:00Z" :cost-usd 0.5}] (main/read-ledger! path)))))

(deftest capped-log-line-test
  (let [dir (str (fs/create-temp-dir))
        path (str dir "/ledger.jsonl")
        out (with-out-str (main/default-log! path {:conversation "c1" :capped true :spent 0.06 :cap 0.05}))]
    (is (str/includes? out "capped in c1 ($0.06/$0.05)"))
    (is (nil? (:cost-usd (json/parse-string (slurp path) true))))))

(deftest parse-cap-test
  (is (nil? (main/parse-cap nil)))
  (is (nil? (main/parse-cap "  ")))
  (is (= 0.05 (main/parse-cap " 0.05 ")))
  (is (= 0.0 (main/parse-cap "0")))
  (doseq [bad ["NaN" "Infinity" "-Infinity" "-1" "abc"]]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"must be a number" (main/parse-cap bad)) bad)
    (is (= :ha/bad-args (:type (ex-data (try (main/parse-cap bad) (catch Exception e e))))))))
