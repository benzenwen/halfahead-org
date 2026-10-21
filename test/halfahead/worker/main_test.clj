(ns halfahead.worker.main-test
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.edn :as edn]
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
