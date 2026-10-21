(ns halfahead.worker.engine-test
  (:require [clojure.test :refer [deftest is testing]]
            [halfahead.schema :as schema]
            [halfahead.worker.core :as core]
            [halfahead.worker.engine :as engine]))

(def request
  {:persona "Be kind."
   :history [{:role :human :text "first"}
             {:role :agent :text "ok"}
             {:role :human :text "second"}
             {:role :agent :text "sure"}]})

(deftest mock-test
  (let [result (engine/invoke {:engine :mock} request)
        reply  (core/parse-reply (:raw result))]
    (is (= result (engine/invoke {:engine :mock} request)))
    (is (schema/valid? :ha/engine-reply reply))
    (is (= "Noted: second" (:text reply)))
    (is (= :aside (:delivery reply)))
    (is (= [] (:emphasis reply)))
    (is (= 0 (:cost-usd result)))))

(deftest claude-command-test
  (let [argv (engine/claude-command {:engine :claude} request)]
    (is (= ["claude" "-p" (core/engine-prompt request) "--output-format" "json"] argv))))

(deftest claude-invoke-test
  (let [seen (atom nil)
        run  (fn [out]
               (fn [argv timeout]
                 (reset! seen [argv timeout])
                 out))
        cfg  (fn [out] {:engine :claude :process-fn (run out)})]
    (testing "reads result and cost from the CLI JSON"
      (is (= {:raw "hi" :cost-usd 0.02}
             (engine/invoke (cfg {:exit 0 :out "{\"result\": \"hi\", \"total_cost_usd\": 0.02}"})
                            request)))
      (is (= (engine/claude-command {} request) (first @seen)))
      (is (= 180000 (second @seen))))
    (testing "timeout comes from config"
      (engine/invoke (assoc (cfg {:exit 0 :out "{\"result\": \"x\"}"}) :timeout-ms 5) request)
      (is (= 5 (second @seen))))
    (testing "failures are :ha/engine-failed"
      (doseq [out [{:exit 1 :out "" :err "boom"}
                   {:timeout? true}
                   {:exit 0 :out "not json"}
                   {:exit 0 :out "{\"nope\": 1}"}]]
        (let [e (try (engine/invoke (cfg out) request) nil (catch Exception e e))]
          (is (= {:type :ha/engine-failed :engine :claude} (select-keys (ex-data e) [:type :engine])))
          (is (string? (:err (ex-data e)))))))))

(deftest unknown-engine-test
  (is (thrown? IllegalArgumentException (engine/invoke {:engine :nope} request))))
