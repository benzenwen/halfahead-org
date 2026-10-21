(ns halfahead.app.core-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [halfahead.app.core :as core]
            [halfahead.schema :as schema]
            [halfahead.sound :as sound]))

;; --- fixtures ---------------------------------------------------------------

(def ben {:id "ben" :kind :human :display-name "Ben" :joined-at #inst "2026-01-01"})
(def bot {:id "bot" :kind :agent :display-name "Bot" :owner-id "ben"
          :joined-at #inst "2026-01-01"})
(def convo {:id "c1" :title "Hello" :member-ids ["ben" "bot"]
            :created-by "ben" :created-at #inst "2026-01-01"})

(defn msg
  ([id text] (msg id text :plain))
  ([id text delivery]
   {:id id :conversation-id "c1" :author-id "ben" :text text :delivery delivery
    :emphasis [] :sent-at #inst "2026-01-02"}))

(defn step [state action] (:state (core/handle state action)))

(def started
  (step core/initial-state
        [:session/started {:me "ben" :conversation convo :members [ben bot]
                           :now-ms 1000 :reduced-motion? false}]))

(defn live [state & ms]
  (step state [:messages/received {:messages (vec ms) :initial? false :now-ms 1000}]))

(defn text-of [frame] (apply str (map :text frame)))

;; --- actions ----------------------------------------------------------------

(deftest session-started
  (let [{:keys [state effects]}
        (core/handle core/initial-state
                     [:session/started {:me "ben" :conversation convo :members [ben bot]
                                        :now-ms 5 :reduced-motion? true}])]
    (is (= "ben" (:me state)))
    (is (= convo (:conversation state)))
    (is (= {"ben" ben "bot" bot} (:members state)))
    (is (= 5 (:now-ms state)))
    (is (= {:reduced-motion? true :sound? false} (:prefs state)))
    (is (= [] effects))))

(deftest composer-edited-and-presence
  (let [{:keys [state effects]} (core/handle started [:composer/edited {:text "he"}])]
    (is (= "he" (get-in state [:composer :text])))
    (is (= [] effects)))
  (let [p {:member-id "bot" :conversation-id "c1" :state :thinking :since #inst "2026-01-02"}
        {:keys [state effects]} (core/handle started [:presence/received {:presence [p]}])]
    (is (= {"bot" p} (:presence state)))
    (is (= [] effects))))

(deftest composer-sent
  (let [s (step started [:composer/edited {:text "  hi "}])
        {:keys [state effects]} (core/handle s [:composer/sent {:id "m1" :now #inst "2026-02-02"}])
        [[fx m] :as all] effects]
    (is (= 1 (count all)))
    (is (= :fx/write-message fx))
    (is (schema/valid? :ha/message m))
    (is (= {:id "m1" :conversation-id "c1" :author-id "ben" :text "hi" :delivery :plain
            :emphasis [] :sent-at #inst "2026-02-02"}
           m))
    (is (= "" (get-in state [:composer :text])))))

(defn- sent-with [text delivery]
  (let [s (-> started
              (step [:composer/edited {:text text}])
              (step [:composer/delivery-chosen {:delivery delivery}]))]
    (-> (core/handle s [:composer/sent {:id "m1" :now #inst "2026-02-02"}])
        :effects first second)))

(deftest sent-headline-carries-emphasis
  (let [m (sent-with "the *one* habit" :headline)]
    (is (= "the one habit" (:text m)))
    (is (= [[4 7]] (:emphasis m)))))

(deftest sent-asterisks-are-stripped-for-any-delivery
  (let [m (sent-with "a *quiet* word" :aside)]
    (is (= "a quiet word" (:text m)))
    (is (= [[2 7]] (:emphasis m)))
    (is (schema/valid? :ha/message m))))

(deftest sent-literal-asterisks-are-unchanged
  (let [m (sent-with "2 * 3 * 4" :plain)]
    (is (= "2 * 3 * 4" (:text m)))
    (is (= [] (:emphasis m)))))

(deftest composer-sent-unsendable
  (doseq [text ["" "   " "\n\t" (apply str (repeat 4001 "a"))]]
    (let [s (step started [:composer/edited {:text text}])
          r (core/handle s [:composer/sent {:id "m1" :now #inst "2026-02-02"}])]
      (is (= s (:state r)))
      (is (= [] (:effects r))))))

(deftest composer-delivery-chosen-then-sent
  (let [s (-> started
              (step [:composer/edited {:text "hm"}])
              (step [:composer/delivery-chosen {:delivery :aside}]))
        {:keys [state effects]} (core/handle s [:composer/sent {:id "m1" :now #inst "2026-02-02"}])
        [[fx m]] effects]
    (is (= :fx/write-message fx))
    (is (= :aside (:delivery m)))
    (is (= [] (:emphasis m)))
    (is (schema/valid? :ha/message m))
    (is (= {:text "" :delivery :plain} (:composer state)))))

(deftest delivery-chosen-sets-each-known-delivery
  (doseq [d [:plain :typed :headline :aside :credits]]
    (is (= d (get-in (step started [:composer/delivery-chosen {:delivery d}])
                     [:composer :delivery])))))

(deftest delivery-chosen-unknown-leaves-state-unchanged
  (let [s (step started [:composer/delivery-chosen {:delivery :typed}])]
    (doseq [a [[:composer/delivery-chosen {:delivery :fireworks}]
               [:composer/delivery-chosen {:delivery nil}]
               [:composer/delivery-chosen {:delivery "aside"}]
               [:composer/delivery-chosen]]]
      (let [r (core/handle s a)]
        (is (= s (:state r)) (pr-str a))
        (is (= [] (:effects r)))))))

(deftest unsendable-draft-keeps-delivery
  (let [s (step started [:composer/delivery-chosen {:delivery :credits}])
        r (core/handle s [:composer/sent {:id "m1" :now #inst "2026-02-02"}])]
    (is (= [] (:effects r)))
    (is (= :credits (get-in (:state r) [:composer :delivery])))))

(deftest messages-received-initial-performs-nothing
  (let [s (step started [:messages/received {:messages [(msg "a" "hi") (msg "b" "yo")]
                                             :initial? true :now-ms 2000}])]
    (is (= #{"a" "b"} (set (keys (:messages s)))))
    (is (= {} (:performing s)))))

(deftest messages-received-live
  (let [s1 (live started (msg "a" "hello there" :typed))]
    (is (= {"a" 1000} (:performing s1)))
    (let [s2 (step s1 [:messages/received {:messages [(msg "a" "hello there" :typed)
                                                      (msg "b" "yo" :typed)]
                                           :initial? false :now-ms 1500}])]
      (is (= {"a" 1000 "b" 1500} (:performing s2))))
    (let [settled (step started [:messages/received {:messages [(msg "a" "hi")]
                                                     :initial? true :now-ms 1}])
          s3 (step settled [:messages/received {:messages [(msg "a" "hi")]
                                                :initial? false :now-ms 9}])]
      (is (= {} (:performing s3))))))

(deftest clock-ticked-finishes-performances
  (let [s    (live started (msg "a" "hello there friend" :typed))
        dur  (let [fr (core/frames (assoc s :now-ms 1000)) ] fr)
        ;; find the duration by probing: performing until the frame is final
        end  (first (filter #(every? (comp #{1.0} :progress)
                                     (get (core/frames (assoc s :now-ms %)) "a"))
                            (range 1000 20000)))]
    (is (some? end))
    (is (pos? (count dur)))
    (testing "alive at exactly started + duration"
      (is (= {"a" 1000} (:performing (step s [:clock/ticked {:now-ms end}])))))
    (testing "removed one ms later"
      (let [s' (step s [:clock/ticked {:now-ms (inc end)}])
            fr (get (core/frames s') "a")]
        (is (= {} (:performing s')))
        (is (= (inc end) (:now-ms s')))
        (is (= "hello there friend" (text-of fr)))
        (is (every? #(= 1.0 (:progress %)) fr))))
    (testing "an earlier tick keeps it"
      (is (= {"a" 1000} (:performing (step s [:clock/ticked {:now-ms 1001}])))))))

(deftest reduced-motion-shows-everything-on-arrival
  (let [typed (msg "a" "hello there friend" :typed)
        calm  (live (step started [:prefs/changed {:reduced-motion? true}]) typed)
        busy  (live started typed)]
    (is (every? #(= 1.0 (:progress %)) (get (core/frames calm) "a")))
    (is (= "hello there friend" (text-of (get (core/frames calm) "a"))))
    (is (some #(< (:progress %) 1.0) (get (core/frames busy) "a")))))

(deftest message-skipped
  (let [s (live started (msg "a" "hello there" :typed))
        s' (step s [:message/skipped {:id "a"}])]
    (is (= {} (:performing s')))
    (is (every? #(= 1.0 (:progress %)) (get (core/frames s') "a")))
    (is (= s (step s [:message/skipped {:id "nope"}])))))

(deftest prefs-changed
  (let [a (step started [:prefs/changed {:reduced-motion? true}])
        b (step started [:prefs/changed {:sound? true}])
        c (step started [:prefs/changed {:reduced-motion? true :sound? true}])]
    (is (= {:reduced-motion? true :sound? false} (:prefs a)))
    (is (= {:reduced-motion? false :sound? true} (:prefs b)))
    (is (= {:reduced-motion? true :sound? true} (:prefs c)))
    (is (= [] (:effects (core/handle started [:prefs/changed {:sound? true}]))))))

(deftest replay-requested
  (let [s (step started [:messages/received {:messages [(msg "a" "hello there" :typed)]
                                             :initial? true :now-ms 10}])
        s' (step s [:replay/requested {:id "a" :now-ms 3000}])]
    (is (= {"a" 3000} (:performing s')))
    (is (= 3000 (:now-ms s')))
    (is (zero? (:progress (first (get (core/frames s') "a")))))
    (is (= s (step s [:replay/requested {:id "nope" :now-ms 3000}])))))

(deftest unknown-actions
  (doseq [a [[:nope/nothing {:x 1}] [:nope] nil :keyword "str" []]]
    (let [{:keys [state effects]} (core/handle started a)]
      (is (= started state))
      (is (= [] effects)))))

(deftest seeds
  (is (= (core/seed "m1") (core/seed "m1")))
  (is (not= (core/seed "m1") (core/seed "m2")))
  (is (every? #(and (integer? %) (<= 0 % 4294967295))
              (map core/seed ["" "m1" "a-very-long-message-id-0123456789" "日本語"])))
  (testing "pinned, so a host divergence fails"
    (is (= 7 (core/seed "")))
    (is (= 7 (core/seed nil)))
    (is (= 7 (core/seed "")))
    (is (= 3 (count (distinct (map core/seed ["a" "b" "c"])))))
    (is (= (+ (* 31 7) 97) (core/seed "a")))
    (is (= (mod (+ (* 31 (+ (* 31 7) 97)) 98) 4294967296) (core/seed "ab")))))

(deftest awaiting-agent
  (let [thinking {:member-id "bot" :conversation-id "c1" :state :thinking
                  :since #inst "2026-01-02"}
        idle (assoc thinking :state :idle)
        reply (assoc (msg "r" "ok") :author-id "bot" :sent-at #inst "2026-01-03")
        with (fn [s p] (step s [:presence/received {:presence [p]}]))]
    (is (false? (core/awaiting-agent? (with started thinking))))
    (let [s (live started (msg "a" "hi"))]
      (is (true? (core/awaiting-agent? (with s thinking))))
      (is (false? (core/awaiting-agent? (with s idle))))
      (is (false? (core/awaiting-agent? (with (live s reply) thinking)))))))

;; --- property: flair never alters content -----------------------------------

(def gen-token
  (gen/elements ["a" "word" "remarkable" "héllo" "😀" "日本語" "\n" " " "  " ". " "! "]))

(def gen-text (gen/such-that seq (gen/fmap str/join (gen/vector gen-token 1 20))))

(def gen-message
  (gen/bind gen-text
            (fn [text]
              (let [idx (gen/choose -3 (+ (count text) 3))]
                (gen/hash-map :id (gen/elements ["m1" "m2" "m3" "m4"])
                              :conversation-id (gen/return "c1")
                              :author-id (gen/elements ["ben" "bot"])
                              :text (gen/return text)
                              :delivery (gen/elements [:plain :typed :headline :aside
                                                       :credits :fireworks])
                              :emphasis (gen/vector (gen/tuple idx idx) 0 4)
                              :sent-at (gen/return #inst "2026-01-02"))))))

(def gen-action
  (gen/frequency
   [[4 (gen/fmap (fn [[ms init?]] [:messages/received {:messages ms :initial? init?}])
                 (gen/tuple (gen/vector gen-message 1 3) gen/boolean))]
    [3 (gen/return [:clock/ticked {}])]
    [1 (gen/fmap (fn [id] [:message/skipped {:id id}]) (gen/elements ["m1" "m2" "m3" "m4"]))]
    [2 (gen/fmap (fn [id] [:replay/requested {:id id}]) (gen/elements ["m1" "m2" "m3" "m4"]))]
    [1 (gen/fmap (fn [b] [:prefs/changed {:reduced-motion? b}]) gen/boolean)]
    [1 (gen/fmap (fn [t] [:composer/edited {:text t}]) gen/string)]
    [2 (gen/fmap (fn [d] [:composer/delivery-chosen {:delivery d}])
                 (gen/elements [:plain :typed :headline :aside :credits :fireworks nil]))]
    [2 (gen/return [:composer/sent {:id "s1" :now #inst "2026-02-02"}])]
    [1 (gen/return [:presence/received {:presence []}])]]))

(defn- with-time
  "Gives each action a non-decreasing now-ms from its deltas."
  [steps]
  (second
   (reduce (fn [[t acc] [delta [k args]]]
             (let [t' (+ t delta)]
               [t' (conj acc [k (cond-> args
                                  (#{:messages/received :clock/ticked :replay/requested} k)
                                  (assoc :now-ms t'))])]))
           [1000 []] steps)))

(def gen-actions
  (gen/fmap with-time (gen/vector (gen/tuple (gen/choose 0 3000) gen-action) 0 25)))

(defn- content-intact? [state]
  (every? (fn [[id {:keys [text]}]] (= text (text-of (get (core/frames state) id))))
          (:messages state)))

(defspec flair-never-alters-content 200
  (prop/for-all [actions gen-actions]
    (loop [s started [a & more] actions]
      (and (content-intact? s)
           (or (nil? a) (recur (step s a) more))))))

;; --- credits ----------------------------------------------------------------

(def ended (step (live started (msg "m1" "hi")) [:scene/ended {:now-ms 5000 :date "21 October 2026"}]))

(defn settled? [frame] (every? #(= 1.0 (:progress %)) frame))

(deftest scene-ended-sets-credits-and-performs-them
  (is (= 5000 (get-in ended [:credits :started-at])))
  (is (str/includes? (get-in ended [:credits :text]) "HELLO"))
  (is (contains? (:performing ended) :credits))
  (is (not (settled? (core/frames-credits ended))))
  (is (not (contains? (core/frames ended) :credits))))

(deftest credits-settle-after-their-duration
  (let [later (step ended [:clock/ticked {:now-ms 60000}])]
    (is (not (contains? (:performing later) :credits)))
    (is (some? (:credits later)))
    (is (settled? (core/frames-credits later)))))

(deftest dismissing-clears-credits
  (let [gone (step ended [:scene/dismissed])]
    (is (nil? (:credits gone)))
    (is (not (contains? (:performing gone) :credits)))
    (is (nil? (core/frames-credits gone)))))

(deftest reduced-motion-credits-are-settled-at-once
  (let [state (step (assoc-in started [:prefs :reduced-motion?] true)
                    [:scene/ended {:now-ms 5000 :date "d"}])]
    (is (settled? (core/frames-credits state)))))

(deftest credits-segments-concatenate-to-the-text
  (doseq [t [0 1 500 2000 10000 60000]]
    (let [state (assoc ended :now-ms (+ 5000 t))]
      (is (= (get-in ended [:credits :text])
             (text-of (core/frames-credits state)))))))

;; --- sound cues -------------------------------------------------------------

(defn agent-msg [id delivery]
  (assoc (msg id "hello" delivery) :author-id "bot"))

(def sound-on (step started [:prefs/changed {:sound? true}]))

(defn received [state ms & {:keys [initial?] :or {initial? false}}]
  (core/handle state [:messages/received {:messages ms :initial? initial? :now-ms 1000}]))

(deftest sound-cues
  (let [m (agent-msg "a" :headline)]
    (testing "off: silent"
      (is (= [] (:effects (received started [m])))))
    (testing "on: exactly one cue for a new agent headline"
      (is (= [[:fx/play-cue (sound/cue m)]] (:effects (received sound-on [m])))))
    (testing "initial history is silent"
      (is (= [] (:effects (received sound-on [m] :initial? true)))))
    (testing "redelivery of a known message is silent"
      (is (= [] (:effects (received (live sound-on m) [m])))))
    (testing "replay is silent"
      (is (= [] (:effects (core/handle (live sound-on m)
                                       [:replay/requested {:id "a" :now-ms 2000}])))))
    (testing "a human's message, including the reader's own echo, is silent"
      (is (= [] (:effects (received sound-on [(msg "h" "hi" :headline)]))))
      (let [sent (step (step sound-on [:composer/edited {:text "hi"}])
                       [:composer/sent {:id "mine" :now #inst "2026-01-03"}])
            own  (get-in sent [:messages "mine"])]
        (is (= [] (:effects (received sent [(or own (msg "mine" "hi"))]))))))
    (testing "plain agent message has no cue"
      (is (= [] (:effects (received sound-on [(agent-msg "p" :plain)])))))
    (testing "a burst yields one cue"
      (is (= 1 (count (:effects (received sound-on [m (agent-msg "b" :aside)]))))))))

;; --- scene replay -----------------------------------------------------------

(defn msg-at [id text sent-ms]
  (assoc (msg id text) :sent-at (#?(:clj java.util.Date. :cljs js/Date.) sent-ms)))

(def two-messages
  (step started [:messages/received
                 {:messages [(msg-at "a" "first" 0) (msg-at "b" "second" 1000)]
                  :initial? true :now-ms 1000}]))

(defn replaying [state now-ms] (step state [:scene/replay {:now-ms now-ms}]))

(defn visible [state]
  (set (filter #(core/reached? state %) (keys (:messages state)))))

(defn at-b [state] (get-in state [:scene :schedule 1 :at-ms]))

(deftest replay-hides-until-reached
  (let [s (replaying two-messages 5000)]
    (is (contains? (:performing s) :scene))
    (is (= #{"a"} (visible s)))
    (let [b-at (at-b s)
          early (step s [:clock/ticked {:now-ms (+ 5000 (dec b-at))}])
          later (step s [:clock/ticked {:now-ms (+ 5000 b-at)}])]
      (is (pos? b-at))
      (is (= #{"a"} (visible early)))
      (is (= #{"a" "b"} (visible later))))))

(deftest pause-freezes-and-resume-continues
  (let [s      (replaying two-messages 5000)
        b-at   (at-b s)
        paused (step s [:scene/paused {:now-ms 5100}])
        ticked (step paused [:clock/ticked {:now-ms (+ 5000 b-at 10000)}])]
    (is (= #{"a"} (visible ticked)))
    (is (contains? (:scene ticked) :schedule))
    (let [resumed (step ticked [:scene/resumed {:now-ms (+ 5000 b-at 10000)}])
          just-before (step resumed [:clock/ticked {:now-ms (+ 5000 b-at 10000 (- b-at 101))}])
          reached     (step resumed [:clock/ticked {:now-ms (+ 5000 b-at 10000 (- b-at 100))}])]
      (is (= #{"a"} (visible just-before)))
      (is (= #{"a" "b"} (visible reached))))))

(deftest stop-settles-everything
  (let [s (-> (replaying two-messages 5000) (step [:scene/stopped]))]
    (is (nil? (:scene s)))
    (is (not (contains? (:performing s) :scene)))
    (is (= #{"a" "b"} (visible s)))
    (is (= "second" (text-of (get (core/frames s) "b"))))
    (is (every? #(= 1.0 (:progress %)) (get (core/frames s) "b")))))

(deftest scene-ends-by-itself
  (let [s (replaying two-messages 5000)
        end (get-in s [:scene :end-ms])
        before (step s [:clock/ticked {:now-ms (+ 5000 (dec end))}])
        after  (step s [:clock/ticked {:now-ms (+ 5000 end)}])]
    (is (some? (:scene before)))
    (is (nil? (:scene after)))
    (is (not (contains? (:performing after) :scene)))))

(deftest late-arrival-is-not-in-the-replay
  (let [s (-> (replaying two-messages 5000)
              (step [:messages/received {:messages [(msg-at "z" "new" 9000)]
                                         :initial? false :now-ms 5100}]))]
    (is (contains? (:messages s) "z"))
    (is (not-any? #(= "z" (:id %)) (get-in s [:scene :schedule])))
    (is (contains? (visible s) "z"))))

(deftest reduced-motion-replay-settles-at-once
  (let [s (-> two-messages
              (step [:prefs/changed {:reduced-motion? true}])
              (replaying 5000))]
    (is (= #{"a" "b"} (visible s)))
    (is (nil? (:scene (step s [:clock/ticked {:now-ms 5001}]))))))

(deftest replay-without-messages-does-nothing
  (is (nil? (:scene (replaying started 5000)))))

(def gen-scene-action
  (gen/one-of
   [(gen/return [:scene/replay])
    (gen/return [:scene/paused])
    (gen/return [:scene/resumed])
    (gen/return [:scene/stopped])
    (gen/return [:clock/ticked])
    (gen/fmap (fn [n] [:messages/received n]) (gen/choose 0 9))]))

(defspec frames-always-concatenate-to-text 100
  (prop/for-all [reduced? gen/boolean
                 steps (gen/vector (gen/tuple gen-scene-action (gen/choose 0 4000)) 0 30)]
    (let [start (step two-messages [:prefs/changed {:reduced-motion? reduced?}])
          final (reduce (fn [[s t] [[kind arg] dt]]
                          (let [t' (+ t dt)
                                action (case kind
                                         :messages/received
                                         [kind {:messages [(msg-at (str "n" arg) (str "late " arg) (+ 2000 arg))]
                                                :initial? false :now-ms t'}]
                                         :scene/stopped [kind]
                                         [kind {:now-ms t'}])]
                            [(step s action) t']))
                        [start 5000] steps)
          s (first final)]
      (every? (fn [[id frame]] (= (:text (get (:messages s) id)) (text-of frame)))
              (core/frames s)))))
