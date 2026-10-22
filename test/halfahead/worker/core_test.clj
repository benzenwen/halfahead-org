(ns halfahead.worker.core-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [halfahead.schema :as schema]
            [halfahead.worker.core :as core])
  (:import (java.util Date)))

(defn- at [n] (Date. (long (* n 1000))))

(def members
  [{:id "ann" :kind :human :display-name "Ann" :joined-at (at 0)}
   {:id "bot" :kind :agent :display-name "Bot" :owner-id "ann" :joined-at (at 0)}])

(defn- msg [n author text]
  {:id (str "m" n) :conversation-id "c1" :author-id author :text text
   :delivery :plain :emphasis [] :sent-at (at n)})

(defn- shuffles [xs]
  (let [r (java.util.Random. 42)]
    (cons (reverse xs)
          (repeatedly 10 #(let [l (java.util.ArrayList. ^java.util.Collection xs)]
                            (java.util.Collections/shuffle l r)
                            (vec l))))))

(deftest awaiting-reply-test
  (let [h1 (msg 1 "ann" "hi") b2 (msg 2 "bot" "hello") h3 (msg 3 "ann" "more")]
    (is (= h3 (core/awaiting-reply "bot" [h1 b2 h3])))
    (is (nil? (core/awaiting-reply "bot" [h1 b2])))
    (is (= h1 (core/awaiting-reply "bot" [h1])))
    (is (nil? (core/awaiting-reply "bot" [])))
    (testing "order of input does not matter"
      (doseq [ms (shuffles [h1 b2 h3])]
        (is (= h3 (core/awaiting-reply "bot" ms))))
      (doseq [ms (shuffles [h1 b2])]
        (is (nil? (core/awaiting-reply "bot" ms)))))))

(deftest engine-request-test
  (let [ms [(msg 1 "ann" "hi") (msg 2 "bot" "hello") (msg 3 "zed" "who?")]
        req (core/engine-request "Be kind." members (reverse ms))]
    (is (schema/valid? :ha/engine-request req))
    (is (= [:human :agent :human] (mapv :role (:history req))))
    (is (= ["hi" "hello" "who?"] (mapv :text (:history req)))))
  (testing "at most 200 turns, the most recent, in time order"
    (let [ms (mapv #(msg % "ann" (str "t" %)) (range 1 251))
          req (core/engine-request "P" members (reverse ms))]
      (is (schema/valid? :ha/engine-request req))
      (is (= 200 (count (:history req))))
      (is (= (mapv :text (drop 50 ms)) (mapv :text (:history req)))))))

(deftest engine-prompt-test
  (let [req (core/engine-request
             "I am Bot." members
             [(msg 1 "ann" "first") (msg 2 "bot" "second") (msg 3 "ann" "third")])
        p (core/engine-prompt req)
        idx #(str/index-of p %)]
    (is (str/includes? p "I am Bot."))
    (doseq [d ["plain" "typed" "headline" "aside" "credits"]]
      (is (str/includes? p d)))
    (is (str/includes? p "{\"text\": ..., \"delivery\": ..., \"emphasis\": [...]}"))
    (is (< (idx "Human: first") (idx "You: second") (idx "Human: third")))
    (is (< (idx "I am Bot.") (idx "Human: first")))))

(deftest engine-system-prompt-test
  (let [req (core/engine-request
             "I am Bot." members
             [(msg 1 "ann" "first") (msg 2 "bot" "second")])
        p   (core/engine-system-prompt req)
        idx #(str/index-of p %)]
    (is (< (idx "I am Bot.") (idx "- plain") (idx "- typed") (idx "- headline")
           (idx "- aside") (idx "- credits")))
    (is (str/includes? p "{\"text\": ..., \"delivery\": ..., \"emphasis\": [...]}"))
    (is (not (str/includes? p "first")))
    (is (not (str/includes? p "You:")))))

(deftest engine-transcript-test
  (let [req (core/engine-request
             "I am Bot." members
             [(msg 1 "ann" "first") (msg 2 "bot" "second") (msg 3 "ann" "third")])]
    (is (= "Human: first\nYou: second\nHuman: third\nYou:" (core/engine-transcript req)))
    (is (= "You:" (core/engine-transcript {:persona "P" :history []})))
    (is (not (str/includes? (core/engine-transcript req) "I am Bot.")))))

(deftest engine-prompt-concatenation-test
  (doseq [ms [[] [(msg 1 "ann" "hi")] [(msg 1 "ann" "hi") (msg 2 "bot" "yo") (msg 3 "ann" "?")]]]
    (let [req (core/engine-request "I am Bot." members ms)]
      (is (= (core/engine-prompt req)
             (str (core/engine-system-prompt req) "\n\n" (core/engine-transcript req)))))))

(deftest parse-reply-test
  (testing "examples"
    (is (= {:text "Hi there" :delivery :aside :emphasis []}
           (core/parse-reply "Sure!\n```json\n{\"text\": \"Hi there\", \"delivery\": \"aside\", \"emphasis\": []}\n```")))
    (is (= {:text "just words" :delivery :plain :emphasis []}
           (core/parse-reply "just words"))))
  (testing "bare object, unknown delivery"
    (is (= {:text "x" :delivery :typed :emphasis ["x"]}
           (core/parse-reply "{\"text\":\"x\",\"delivery\":\"typed\",\"emphasis\":[\"x\"]}")))
    (is (= :plain (:delivery (core/parse-reply "{\"text\":\"x\",\"delivery\":\"sparkle\"}")))))
  (testing "truncation"
    (is (= 4000 (count (:text (core/parse-reply (str "{\"text\":\"" (apply str (repeat 5000 "a")) "\"}"))))))
    (is (= 4000 (count (:text (core/parse-reply (apply str (repeat 5000 "a"))))))))
  (testing "empty"
    (doseq [s ["" "  \n" nil]]
      (is (= :ha/empty-reply
             (:type (try (core/parse-reply s) (catch Exception e (ex-data e))))))))
  (testing "a stray brace in the prose does not hide the object"
    (is (= {:text "hi" :delivery :aside :emphasis []}
           (core/parse-reply "I'd say { oops. {\"text\":\"hi\",\"delivery\":\"aside\"}"))))
  (testing "emphasis tolerance"
    (is (= ["a"] (:emphasis (core/parse-reply "{\"text\":\"a\",\"emphasis\":[\"a\",1,null,\"\"]}"))))
    (is (= 8 (count (:emphasis (core/parse-reply
                                (str "{\"text\":\"a\",\"emphasis\":"
                                     (cheshire.core/generate-string (mapv str (range 20))) "}"))))))
    (is (= [] (:emphasis (core/parse-reply "{\"text\":\"a\",\"emphasis\":\"a\"}")))))
  (testing "every non-throwing result is a valid reply"
    (doseq [s ["plain" "```json\n{\"text\":\"a\"}\n```" "{\"text\":\"a\"}"
               "{\"text\": }" "{\"text\":\"  \"}" "see {braces} here"
               "{\"text\":\"a } b\",\"delivery\":\"headline\"} trailing"
               "{\"text\":1}" "[1,2]"]]
      (is (schema/valid? :ha/engine-reply (core/parse-reply s)) s))
    (is (= "a } b" (:text (core/parse-reply "{\"text\":\"a } b\"} tail"))))))

(deftest resolve-emphasis-test
  (is (= [[4 7] [15 22]]
         (core/resolve-emphasis "the cat sat on the mat" ["cat" "dog" "the mat"])))
  (is (= [[0 3]] (core/resolve-emphasis "the cat" ["the" "the" "he"])))
  (is (= [[0 3] [4 7]] (core/resolve-emphasis "the cat" ["cat" "the"])))
  (testing "spans are ordered, disjoint, bounded, at most 8"
    (let [r (java.util.Random. 7)
          rs #(apply str (repeatedly (inc (.nextInt r %)) (fn [] (rand-nth "abc "))))]
      (dotimes [_ 200]
        (let [text (rs 40)
              spans (core/resolve-emphasis text (repeatedly 12 #(rs 3)))]
          (is (<= (count spans) 8))
          (is (every? (fn [[f t]] (and (<= 0 f) (< f t) (<= t (count text)))) spans))
          (is (every? (fn [[[_ t1] [f2 _]]] (<= t1 f2)) (partition 2 1 spans))))))))

(deftest reply-message-test
  (let [to (msg 3 "ann" "Say it loud")
        m (core/reply-message {:reply {:text "Loud and clear" :delivery :headline
                                       :emphasis ["clear" "nope"]}
                               :to to :agent-id "bot" :id "r1" :now (at 4)})]
    (is (schema/valid? :ha/message m))
    (is (= "m3" (:in-reply-to m)))
    (is (= "c1" (:conversation-id m)))
    (is (= [[9 14]] (:emphasis m)))
    (is (= (at 4) (:sent-at m)))))

(deftest spent-today-test
  (let [entries [{:at "2026-10-20T23:59:59Z" :cost-usd 1}
                 {:at "2026-10-21T00:00:00Z" :cost-usd 0.25}
                 {:at "2026-10-21T23:59:59.900Z" :cost-usd 0.5}
                 {:at "2026-10-21T12:00:00Z"}
                 {:at "2026-10-21T12:00:00Z" :cost-usd "9"}
                 {:cost-usd 100}
                 {:at "garbage" :cost-usd 100}
                 "not a map"]]
    (is (= 0.75 (core/spent-today entries "2026-10-21")))
    (is (= 1 (core/spent-today entries "2026-10-20")))
    (is (= 0 (core/spent-today [] "2026-10-21")))
    (testing "days partition the valid entries"
      (is (= 1.75 (+ (core/spent-today entries "2026-10-20")
                     (core/spent-today entries "2026-10-21")))))))

(deftest over-cap-test
  (is (not (core/over-cap? 1000 nil)))
  (is (core/over-cap? 0.05 0.05))
  (is (not (core/over-cap? 0.04 0.05)))
  (is (core/over-cap? 0 0)))

(deftest budget-reply-test
  (let [to {:id "m1" :conversation-id "c1"}
        r (core/budget-reply {:to to :agent-id "bot" :id "r1" :now (java.util.Date. 0)})]
    (is (schema/valid? :ha/message r))
    (is (= :aside (:delivery r)))
    (is (= "bot" (:author-id r)))
    (is (= "m1" (:in-reply-to r)))
    (is (= "I'm out of budget for today, so I'll stay quiet until tomorrow (UTC). My owner can raise the cap."
           (:text r)))))

(deftest budget-reply-sent-test
  (let [mk (fn [author at] (assoc (core/budget-reply {:to {:id "m" :conversation-id "c"}
                                                      :agent-id author :id "r" :now at})
                                  :author-id author))
        day1 (java.util.Date. 0)
        day2 (java.util.Date. (* 86400 1000))]
    (is (core/budget-reply-sent? "bot" [(mk "bot" day1)] "1970-01-01"))
    (is (not (core/budget-reply-sent? "bot" [(mk "bot" day1)] "1970-01-02")))
    (is (not (core/budget-reply-sent? "bot" [(mk "ann" day1)] "1970-01-01")))
    (is (not (core/budget-reply-sent? "bot" [(assoc (mk "bot" day1) :text "hi")] "1970-01-01")))
    (is (core/budget-reply-sent? "bot" [(mk "bot" day2)] "1970-01-02"))))
