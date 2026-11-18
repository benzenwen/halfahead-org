(ns halfahead.dev.score-eval-test
  "No test here makes a model call: replies are canned and the process is
  a fake."
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [halfahead.dev.score-eval :as score-eval]
            [halfahead.lab.score :as score]
            [halfahead.schema :as schema]))

(def ^:private mark-case
  {:id "plan-tender" :mode :mark :feeling :tender
   :text "So that's the plan. We ship on Friday."})

(def ^:private compose-case
  {:id "compose-tests" :mode :compose :said "Give me one sentence on why tests matter."})

(def ^:private patch-case
  {:id "patch-gravity" :mode :patch :text "Gravity always wins."
   :brief "Make 'Gravity' sag under its own weight and bounce back. Write the motion as a patch."})

;; --- prompt ------------------------------------------------------------------

(deftest prompt-test
  (let [reference "# Writing a score\n\nThe page, verbatim.\n"
        opening "You are an agent in a conversation where text is performed, not only shown."]
    (testing "the system prompt is the one line and then the reference, for every mode"
      (doseq [c [mark-case compose-case patch-case]]
        (is (= (str opening "\n\n" reference) (:system (score-eval/prompt c reference))))))
    (testing ":mark gives the text, to be kept, and the feeling as words"
      (let [user (:user (score-eval/prompt mark-case reference))]
        (is (str/includes? user (:text mark-case)))
        (is (str/includes? user "unchanged as \"text\""))
        (is (str/includes? user "What you mean by it: tender: calm and warm"))))
    (testing "the words follow the feeling's two numbers"
      (is (str/includes? (:user (score-eval/prompt (assoc mark-case :feeling :furious) reference))
                         "What you mean by it: furious: excited and harsh"))
      (is (str/includes? (:user (score-eval/prompt (assoc mark-case :feeling :wry) reference))
                         "What you mean by it: wry: slightly excited and slightly harsh")))
    (testing ":compose gives what was said and asks for a reply"
      (is (= (str "Someone said:\n" (:said compose-case)
                  "\n\nReply in one to three sentences, as a score.")
             (:user (score-eval/prompt compose-case reference)))))
    (testing ":patch gives the text, to be kept, and the brief"
      (let [user (:user (score-eval/prompt patch-case reference))]
        (is (str/includes? user (:text patch-case)))
        (is (str/includes? user "unchanged as \"text\""))
        (is (str/ends-with? user (:brief patch-case)))))))

;; --- parse-reply -------------------------------------------------------------

(deftest parse-reply-test
  (let [object {"text" "It holds.", "marks" [{"phrase" "holds" "kind" "hold" "amount" 0.6}]}
        bare (json/generate-string object)]
    (testing "a bare object"
      (is (= object (score-eval/parse-reply bare))))
    (testing "a fenced one"
      (is (= object (score-eval/parse-reply (str "```json\n" bare "\n```")))))
    (testing "one with prose around it"
      (is (= object (score-eval/parse-reply (str "Here is the score:\n\n" bare "\n\nHope it helps.")))))
    (testing "braces in the prose that are not JSON are passed over"
      (is (= object (score-eval/parse-reply (str "A score looks like {this}. " bare)))))
    (testing "braces and escaped quotes inside a JSON string do not end the object"
      (is (= {"text" "a } \"b\" { c"}
             (score-eval/parse-reply "ok: {\"text\": \"a } \\\"b\\\" { c\"} done"))))
    (testing "junk"
      (is (= {:error :no-json} (score-eval/parse-reply "I would rather not.")))
      (is (= {:error :no-json} (score-eval/parse-reply "{\"text\": \"never closed")))
      (is (= {:error :no-json} (score-eval/parse-reply "")))
      (is (= {:error :no-reply} (score-eval/parse-reply nil))))))

;; --- ->score -----------------------------------------------------------------

(def ^:private rich-reply
  {"text" "🚀 Round and round we go."
   "style" "elegant"
   "shot" "card"
   "marks" [{"phrase" "round" "kind" "hold" "amount" 0.6 "warmth" 0.5 "gesture" "swell"}
            {"phrase" "we go" "kind" "land" "amount" 0.8
             "patch" {"ms" 400
                      "stagger" {"ms-per-glyph" 40 "order" "forward"}
                      "mods" [{"channel" "y" "amount" -0.3 "curve" "arch"
                               "alternate" true "each" "even"
                               "osc" {"wave" "sine" "period-ms" 200
                                      "phase" "index" "abs" true}}]}}
            {"phrase" "nowhere" "kind" "stress" "amount" 0.5}]
   "dress" [{"phrase" "Round" "as" "gilt"}
            {"phrase" "square" "as" "strong"}]})

(deftest ->score-test
  (testing "keywords, renamed flags, spans in UTF-16 units and the unfound phrases"
    (is (= {:score {:text "🚀 Round and round we go."
                    :style :elegant
                    :shot :card
                    ;; the rocket is two units, so 'Round' starts at 3
                    :marks [{:span [13 18] :kind :hold :amount 0.6 :warmth 0.5 :gesture :swell}
                            {:span [19 24] :kind :land :amount 0.8
                             :patch {:ms 400
                                     :stagger {:ms-per-glyph 40 :order :forward}
                                     :mods [{:channel :y :amount -0.3 :curve :arch
                                             :alternate? true :each :even
                                             :osc {:wave :sine :period-ms 200
                                                   :phase :index :abs? true}}]}}]
                    :dress [{:span [3 8] :as :gilt}]}
            :unfound ["nowhere" "square"]}
           (score-eval/->score rich-reply))))
  (testing "what it gives is what normalize takes: nothing is dropped"
    (let [{:keys [score dropped]} (score/normalize (:score (score-eval/->score rich-reply)))]
      (is (schema/valid? :ha/score score))
      (is (= [] dropped))
      (is (= 2 (count (:marks score))))))
  (testing "a repeated phrase takes its first occurrence"
    (is (= [{:span [0 2] :kind :stress :amount 1}]
           (get-in (score-eval/->score {"text" "no no no"
                                        "marks" [{"phrase" "no" "kind" "stress" "amount" 1}]})
                   [:score :marks]))))
  (testing "a style that is a point keeps its numbers"
    (is (= {:energy 0.3 :flourish 0.5 :softness 0.8 :warmth 0.7}
           (get-in (score-eval/->score {"text" "a" "style" {"energy" 0.3 "flourish" 0.5
                                                            "softness" 0.8 "warmth" 0.7}})
                   [:score :style]))))
  (testing "never throws, and always gives both keys, whatever it is handed"
    (is (every? (fn [x]
                  (let [result (score-eval/->score x)]
                    (and (contains? result :score)
                         (vector? (:unfound result))
                         (map? (score/normalize (:score result))))))
                [nil 1 "x" [1 2] {} {"marks" 3} {"text" 5 "marks" [1 nil {"phrase" 2}]}
                 {"text" "a" "dress" [{"phrase" ""}] "marks" [{"phrase" "a"}]}
                 {"text" "a" "marks" {"phrase" "a"}} {"style" 4 "shot" ["x"]}
                 {:text "a" 3 4 nil nil} {"kind" {"kind" "x"}}]))))

;; --- measure -----------------------------------------------------------------

(def ^:private clean-reply
  (json/generate-string
   {"text" (:text mark-case)
    "marks" [{"phrase" "the plan" "kind" "hold" "amount" 0.6 "energy" -0.5 "warmth" 0.75}
             {"phrase" "Friday" "kind" "land" "amount" 0.75 "warmth" 0.25 "gesture" "bloom"}]
    "dress" [{"phrase" "Friday" "as" "gilt"}]
    "style" "elegant"
    "shot" "line"}))

(defn- gravity-reply [curve]
  (json/generate-string
   {"text" (:text patch-case)
    "marks" [{"phrase" "Gravity" "kind" "stress" "amount" 0.75
              "patch" {"ms" 800 "mods" [{"channel" "y" "amount" 0.5 "curve" curve}]}}]}))

(deftest measure-test
  (testing "a clean reply"
    (is (= {:id "plan-tender" :mode :mark :feeling :tender
            :parsed? true :valid? true :text-kept? true
            :marks 2 :dropped [] :unfound []
            :kinds {:hold 1 :land 1}
            :energy -0.25 :warmth 0.5
            :gestures {:bloom 1}
            :patches 0 :patch-problems 0 :problems []
            :dress 1 :style :elegant :shot :line}
           (score-eval/measure mark-case (str "```json\n" clean-reply "\n```")))))
  (testing "a reply whose text was changed is still a valid score"
    (let [changed (str/replace clean-reply "We ship on Friday." "We ship on Friday!")
          result (score-eval/measure mark-case changed)]
      (is (false? (:text-kept? result)))
      (is (true? (:valid? result)))
      (is (= 2 (:marks result)))))
  (testing "whether the text was kept is not asked of a composed reply"
    (is (nil? (:text-kept? (score-eval/measure compose-case clean-reply)))))
  (testing "a patch that ends at rest is kept"
    (let [result (score-eval/measure patch-case (gravity-reply "arch"))]
      (is (= [1 0 [] []] ((juxt :patches :patch-problems :problems :dropped) result)))
      (is (true? (:text-kept? result)))))
  (testing "a patch that does not end at rest is counted and the mark kept"
    (let [result (score-eval/measure patch-case (gravity-reply "rise"))]
      (is (true? (:valid? result)))
      (is (= 1 (:marks result)))
      (is (= {:stress 1} (:kinds result)))
      (is (= 0 (:patches result)))
      (is (= 1 (:patch-problems result)))
      (is (= [[:not-at-rest :y]]
             (map (juxt :problem :channel) (first (:problems result)))))
      (is (= [{:what :patch :why :patch-problems :index 0}]
             (map #(dissoc % :problems) (:dropped result))))))
  (testing "an unfound phrase and an invalid mark are listed, not hidden"
    (let [result (score-eval/measure
                  compose-case
                  (json/generate-string
                   {"text" "Tests are how you know."
                    "marks" [{"phrase" "certainty" "kind" "stress" "amount" 0.5}
                             {"phrase" "know" "kind" "shout" "amount" 0.5}]}))]
      (is (true? (:valid? result)))
      (is (= 0 (:marks result)))
      (is (= ["certainty"] (:unfound result)))
      (is (= [{:what :mark :why :invalid :index 0}] (:dropped result)))
      (is (nil? (:energy result)))))
  (testing "a reply that is not JSON"
    (is (= {:id "plan-tender" :mode :mark :feeling :tender
            :parsed? false :valid? false :text-kept? false :error :no-json
            :marks 0 :dropped [] :unfound [] :kinds {}
            :energy nil :warmth nil :gestures {}
            :patches 0 :patch-problems 0 :problems []
            :dress 0 :style nil :shot nil}
           (score-eval/measure mark-case "I'd rather just say it plainly."))))
  (testing "JSON with no text to perform parses but is not a score"
    (let [result (score-eval/measure compose-case "{\"marks\": []}")]
      (is (true? (:parsed? result)))
      (is (false? (:valid? result)))
      (is (= [{:what :score :why :no-text}] (:dropped result)))))
  (testing "no reply at all"
    (is (= [false false :no-reply]
           ((juxt :parsed? :valid? :error) (score-eval/measure mark-case nil))))))

;; --- summary -----------------------------------------------------------------

(defn- result
  "A result as measure gives one, with the given differences."
  [id mode & {:as differences}]
  (merge {:id id :mode mode :parsed? true :valid? true
          :text-kept? (when (#{:mark :patch} mode) true)
          :marks 0 :dropped [] :unfound [] :kinds {} :energy nil :warmth nil
          :gestures {} :patches 0 :patch-problems 0 :problems [] :dress 0
          :style nil :shot nil}
         differences))

(def ^:private results
  [(result "a-tender" :mark :feeling :tender :marks 2 :kinds {:hold 2}
           :energy -0.5 :warmth 0.75 :cost-usd 0.25)
   (result "b-tender" :mark :feeling :tender :marks 1 :kinds {:stress 1}
           :energy 0.5 :warmth 0.25 :cost-usd 0.25 :text-kept? false)
   ;; no marks: it shows neither sign, and stays out of the means
   (result "c-tender" :mark :feeling :tender)
   (result "a-furious" :mark :feeling :furious :marks 4 :kinds {:stress 3 :land 1}
           :energy 0.75 :warmth -0.5 :cost-usd 0.5)
   (result "b-furious" :mark :feeling :furious :parsed? false :valid? false
           :text-kept? false :error :no-json)
   (result "a-wry" :mark :feeling :wry :marks 1 :kinds {:doubt 1}
           :energy 0.25 :warmth -0.25 :unfound ["gone"])
   (result "compose-a" :compose :marks 3 :kinds {:stress 1 :hold 1 :land 1}
           :energy 0.0 :warmth 0.5 :dropped [{:what :style :why :invalid}])
   (result "patch-a" :patch :marks 1 :kinds {:stress 1} :energy 0.0 :warmth 0.0
           :patches 1 :cost-usd 1)
   (result "patch-b" :patch :marks 2 :kinds {:stress 1 :hold 1} :energy 0.0 :warmth 0.0
           :patches 1 :patch-problems 1
           :dropped [{:what :patch :why :patch-problems :index 0 :problems []}])
   (result "patch-c" :patch :marks 1 :kinds {:hold 1} :energy 0.0 :warmth 0.0)])

(deftest summary-test
  (testing "a hand-made list, with exact numbers"
    (is (= {:n 10
            :parsed 0.9
            :valid 0.9
            ;; not b-furious, a-wry (unfound), compose-a or patch-b (dropped)
            :clean 0.6
            ;; of the 9 :mark and :patch results, b-tender and b-furious
            ;; changed the text; 7/9 is not exact in binary
            :text-kept (/ 7.0 9)
            ;; 2 1 0 4 1 3 1 2 1 over the nine valid replies
            :marks {:mean (/ 15.0 9) :min 0 :max 4}
            :no-marks (/ 1.0 9)
            :kinds {:hold 5 :stress 7 :land 2 :doubt 1}
            :feelings {:furious {:n 2 :marked 1 :energy 0.75 :warmth -0.5
                                 :energy-sign 0.5 :warmth-sign 0.5}
                       :tender {:n 3 :marked 2 :energy 0.0 :warmth 0.5
                                :energy-sign (/ 1.0 3) :warmth-sign (/ 2.0 3)}
                       :wry {:n 1 :marked 1 :energy 0.25 :warmth -0.25
                             :energy-sign nil :warmth-sign nil}}
            :patch {:n 3 :carried 2 :written 3 :sound 2}
            :cost-usd 2.0}
           (score-eval/summary results))))
  (testing "nothing to count gives no rates, not a division by zero"
    (is (= {:n 0 :parsed nil :valid nil :clean nil :text-kept nil
            :marks nil :no-marks nil :kinds {} :feelings {}
            :patch {:n 0 :carried 0 :written 0 :sound 0}
            :cost-usd 0}
           (score-eval/summary []))))
  (testing "measure's results are what summary takes"
    (is (= {:n 2 :parsed 0.5 :valid 0.5 :clean 0.5 :text-kept 0.5
            :marks {:mean 2.0 :min 2 :max 2} :no-marks 0.0
            :kinds {:hold 1 :land 1}
            :feelings {:tender {:n 2 :marked 1 :energy -0.25 :warmth 0.5
                                :energy-sign 0.5 :warmth-sign 0.5}}
            :patch {:n 0 :carried 0 :written 0 :sound 0}
            :cost-usd 0}
           (score-eval/summary [(score-eval/measure mark-case clean-reply)
                                (score-eval/measure mark-case "junk")])))))

;; --- ask! --------------------------------------------------------------------

(defn- claude-output [text cost]
  (json/generate-string {:type "result" :result text :total_cost_usd cost}))

(deftest ask!-test
  (let [asked {:system "the system prompt" :user "the user prompt"}]
    (testing "success: the result text and the cost, from one call with every tool disabled"
      (let [calls (atom [])
            process-fn (fn [argv timeout-ms]
                         (swap! calls conj [argv timeout-ms])
                         {:exit 0 :out (claude-output "{\"text\": \"hi\"}" 0.02) :err ""})]
        (is (= {:raw "{\"text\": \"hi\"}" :cost-usd 0.02}
               (score-eval/ask! asked {:process-fn process-fn})))
        (is (= [[["claude" "-p" "the user prompt" "--system-prompt" "the system prompt"
                  "--tools" "" "--disallowedTools" "mcp__*" "--output-format" "json"]
                 180000]]
               @calls))))
    (testing "the timeout can be set"
      (let [seen (atom nil)]
        (score-eval/ask! asked {:timeout-ms 5
                                :process-fn (fn [_ timeout-ms]
                                              (reset! seen timeout-ms)
                                              {:timeout? true})})
        (is (= 5 @seen))))
    (testing "a non-zero exit is a result, not an exception"
      (is (= {:error :exit :detail "not logged in"}
             (score-eval/ask! asked {:process-fn (fn [_ _] {:exit 1 :out "" :err "not logged in\n"})})))
      (is (= {:error :exit :detail "exit 2"}
             (score-eval/ask! asked {:process-fn (fn [_ _] {:exit 2 :out "" :err ""})}))))
    (testing "a timeout"
      (is (= {:error :timeout :detail "timed out after 180000 ms"}
             (score-eval/ask! asked {:process-fn (fn [_ _] {:timeout? true})}))))
    (testing "output that is not the CLI's JSON"
      (is (= :unreadable-output
             (:error (score-eval/ask! asked {:process-fn (fn [_ _] {:exit 0 :out "{oops" :err ""})}))))
      (is (= {:error :no-result :detail "output has no result"}
             (score-eval/ask! asked {:process-fn (fn [_ _] {:exit 0 :out "{\"is_error\": true}" :err ""})}))))
    (testing "a process that cannot start"
      (is (= {:error :process-failed :detail "no claude"}
             (score-eval/ask! asked {:process-fn (fn [_ _] (throw (ex-info "no claude" {})))}))))))

;; --- run ---------------------------------------------------------------------

(deftest run-test
  (let [out-dir (fs/create-temp-dir {:prefix "score-eval-test"})
        calls (atom [])
        ;; the first call fails; the others answer with a bare text
        process-fn (fn [argv _]
                     (swap! calls conj argv)
                     (if (= 1 (count @calls))
                       {:exit 1 :out "" :err "overloaded"}
                       {:exit 0
                        :out (claude-output
                              (json/generate-string
                               {"text" "So that's the plan. We ship on Friday."
                                "marks" [{"phrase" "Friday" "kind" "land" "amount" 0.75
                                          "warmth" 0.5}]})
                              0.25)
                        :err ""}))]
    (try
      (let [printed (with-out-str
                      (score-eval/run {:only "plan-" :limit 2 :repeat 2
                                       :process-fn process-fn :out-dir (str out-dir)}))
            report (edn/read-string (slurp (str (fs/path out-dir "report.edn"))))
            results (:results report)]
        (testing "the count is printed before anything is asked"
          (is (str/starts-with? printed "4 model calls\n")))
        (testing "one call per case and repeat, in order, each with the reference"
          (is (= 4 (count @calls)))
          (is (= [["plan-tender" 1] ["plan-tender" 2] ["plan-furious" 1] ["plan-furious" 2]]
                 (map (juxt :id :rep) results)))
          (is (every? #(str/includes? (nth % 2) "So that's the plan.") @calls))
          (is (str/includes? (nth (nth @calls 0) 2) "tender: calm and warm"))
          (is (str/includes? (nth (nth @calls 2) 2) "furious: excited and harsh"))
          (is (every? #(str/includes? (nth % 4) "# Writing a score") @calls)))
        (testing "a failed call is a result that did not parse"
          (is (= {:parsed? false :valid? false :error :exit :detail "overloaded" :raw nil}
                 (select-keys (first results) [:parsed? :valid? :error :detail :raw]))))
        (testing "every other result carries the raw reply and its cost"
          (is (every? #(and (:valid? %) (string? (:raw %)) (= 0.25 (:cost-usd %)))
                      (rest results))))
        (testing "the report holds the summary of its results"
          (is (= (score-eval/summary results) (:summary report)))
          (is (= [4 0.75 0.75] ((juxt :n :parsed :cost-usd) (:summary report))))
          (is (= {:n 2 :marked 2 :energy 0.0 :warmth 0.5 :energy-sign 0.0 :warmth-sign 0.0}
                 (get-in report [:summary :feelings :furious]))))
        (testing "a line per case and the summary are printed"
          (is (= 4 (count (filter #(str/starts-with? % "plan-") (str/split-lines printed)))))
          (is (str/includes? printed "cost (USD)"))))
      (finally
        (fs/delete-tree out-dir)))))
