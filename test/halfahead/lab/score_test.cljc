(ns halfahead.lab.score-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [halfahead.lab.score :as score]
            [halfahead.schema :as schema]))

;; --- helpers ----------------------------------------------------------------

(def ^:private sentence "I loooove this (honestly). But maybe not. Ship it!")

(def ^:private lavish {:energy 0.5 :flourish 1.0 :softness 0.5 :warmth 0.5})

(def ^:private safe-patch
  {:ms 300 :mods [{:channel :scale :amount 0.2 :curve :arch}]})

(def ^:private restless-patch
  {:ms 300 :mods [{:channel :scale :amount 0.2 :curve :rise}]})

(defn- stress [from to amount] {:span [from to] :kind :stress :amount amount})

(defn- whys [{:keys [dropped]}] (mapv (juxt :what :why) dropped))

(def ^:private known-whys
  #{:no-text :invalid :outside-text :blank :splits-surrogate :patch-problems
    :overlap :too-many :unknown-key})

(defn- in-order? [spanned]
  (let [ks (map (fn [{[from to] :span}] [from (- to)]) spanned)]
    (= ks (sort ks))))

;; --- normalize: what stays ---------------------------------------------------

(deftest a-valid-score-is-unchanged
  (let [s {:text "Ship it now, please"
           :marks [{:span [0 7] :kind :land :amount 0.7}
                   {:span [0 4] :kind :stress :amount 0.6 :energy 0.8 :patch safe-patch}
                   {:span [13 19] :kind :aside :amount 0.4 :gesture :hush}]
           :dress [{:span [0 4] :as :gilt}]
           :style :elegant
           :shot :card}]
    (is (schema/valid? :ha/score s))
    (is (= {:score s :dropped []} (score/normalize s))))
  (is (= {:score {:text "hi" :marks []} :dropped []}
         (score/normalize {:text "hi"}))
      "a score with no marks said has none")
  (is (= {:score {:text "hi" :marks [] :style lavish} :dropped []}
         (score/normalize {:text "hi" :marks () :style lavish}))
      "a style may be a point"))

(deftest marks-and-dress-come-back-in-reading-order
  (let [{:keys [score dropped]}
        (score/normalize {:text "one two three"
                          :marks [(stress 8 13 0.5)
                                  {:span [0 3] :kind :voice :amount 0.5}
                                  {:span [0 7] :kind :build :amount 0.5}
                                  (stress 0 3 0.5)]
                          :dress [{:span [4 7] :as :strong}
                                  {:span [0 3] :as :gilt}
                                  {:span [0 7] :as :whisper}]})]
    (is (= [] dropped))
    (is (= [[0 7] [0 3] [0 3] [8 13]] (mapv :span (:marks score))))
    (is (= [:build :stress :voice :stress] (mapv :kind (:marks score)))
        "one span, two kinds: in the schema's order of kinds")
    (is (= [[0 7] [0 3] [4 7]] (mapv :span (:dress score))))))

;; --- normalize: one example per :why -----------------------------------------

(deftest no-text
  (doseq [x [nil "a string" 42 [] {} {:text 5} {:text ""} {:text "   "}
             {:marks []} {:text (apply str (repeat 4001 "a"))}]]
    (is (= {:score nil :dropped [{:what :score :why :no-text}]}
           (score/normalize x)))))

(deftest unknown-key
  (is (= {:score {:text "hi" :marks []}
          :dropped [{:what :key :why :unknown-key :key :script}]}
         (score/normalize {:text "hi" :marks [] :script "alert(1)"}))))

(deftest invalid
  (testing "a mark"
    (doseq [bad [{:span [0 2] :kind :shout :amount 0.5}
                 {:span [0 2] :kind :stress :amount 1.5}
                 {:span [2 2] :kind :stress :amount 0.5}
                 {:span [0 2] :kind :stress}
                 {:span [0 2] :kind :stress :amount 0.5 :colour "red"}
                 "stress" nil 7]]
      (is (= {:score {:text "hi there" :marks [(stress 3 8 0.5)]}
              :dropped [{:what :mark :why :invalid :index 1}]}
             (score/normalize {:text "hi there" :marks [(stress 3 8 0.5) bad]})))))
  (testing "a dress"
    (is (= {:score {:text "hi" :marks [] :dress []}
            :dropped [{:what :dress :why :invalid :index 0}]}
           (score/normalize {:text "hi" :dress [{:span [0 2] :as :neon}]}))))
  (testing "marks or dress that are no sequence"
    (is (= {:score {:text "hi" :marks []}
            :dropped [{:what :marks :why :invalid} {:what :dress :why :invalid}]}
           (score/normalize {:text "hi" :marks {:span [0 2]} :dress "gilt"}))))
  (testing "a style and a shot"
    (is (= {:score {:text "hi" :marks []}
            :dropped [{:what :style :why :invalid} {:what :shot :why :invalid}]}
           (score/normalize {:text "hi" :marks [] :style :baroque :shot :zoom})))
    (is (= [[:style :invalid]]
           (whys (score/normalize {:text "hi" :style (assoc lavish :energy 2)}))))))

(deftest outside-text
  (is (= {:score {:text "hi" :marks [(stress 0 2 0.5)] :dress []}
          :dropped [{:what :mark :why :outside-text :index 1}
                    {:what :dress :why :outside-text :index 0}]}
         (score/normalize {:text "hi"
                           :marks [(stress 0 2 0.5) (stress 1 3 0.5)]
                           :dress [{:span [5 9] :as :gilt}]}))))

(deftest blank
  (is (= {:score {:text "hi \t there" :marks [(stress 1 4 0.5)]}
          :dropped [{:what :mark :why :blank :index 0}]}
         (score/normalize {:text "hi \t there"
                           :marks [(stress 2 5 0.5) (stress 1 4 0.5)]}))))

(deftest splits-surrogate
  (let [text "go 🚀 now"]
    (is (= 9 (count text)) "the rocket is two units")
    (is (= {:score {:text text :marks [(stress 3 5 0.5)] :dress []}
            :dropped [{:what :mark :why :splits-surrogate :index 0}
                      {:what :mark :why :splits-surrogate :index 1}
                      {:what :dress :why :splits-surrogate :index 0}]}
           (score/normalize {:text text
                             :marks [(stress 0 4 0.5) (stress 4 9 0.5) (stress 3 5 0.5)]
                             :dress [{:span [4 5] :as :gilt}]})))))

(deftest patch-problems
  (let [{:keys [score dropped]}
        (score/normalize {:text "hi there"
                          :marks [(assoc (stress 0 2 0.5) :patch restless-patch)
                                  (assoc (stress 3 8 0.5) :patch "wiggle")]})]
    (is (= [(stress 0 2 0.5) (stress 3 8 0.5)] (:marks score))
        "the marks stay, and the style chooses their gestures")
    (is (= [{:what :patch :why :patch-problems :index 0
             :problems [{:problem :not-at-rest :channel :scale :value 0.2}]}
            [:patch :patch-problems 1 :invalid]]
           [(first dropped)
            ((juxt :what :why :index #(:problem (first (:problems %)))) (second dropped))]))))

(deftest overlap
  (is (= {:score {:text "one two three"
                  :marks [(stress 0 7 0.9) {:span [4 13] :kind :build :amount 0.2}]}
          :dropped [{:what :mark :why :overlap :index 0}]}
         (score/normalize {:text "one two three"
                           :marks [(stress 4 13 0.5)
                                   (stress 0 7 0.9)
                                   {:span [4 13] :kind :build :amount 0.2}]}))
      "the stronger of one kind stays; another kind may overlap"))

(deftest too-many
  (let [text (str/join " " (repeat 20 "ab"))
        span (fn [i] [(* 3 i) (+ 2 (* 3 i))])]
    (testing "marks: the strongest 16 stay"
      (let [marks (mapv #(stress (* 3 %) (+ 2 (* 3 %)) (if (= 5 %) 0.1 0.5)) (range 17))
            {:keys [score dropped]} (score/normalize {:text text :marks marks})]
        (is (= (into (subvec marks 0 5) (subvec marks 6)) (:marks score)))
        (is (= [{:what :mark :why :too-many :index 5}] dropped))))
    (testing "dress: the first 8 stay"
      (let [dress (mapv #(hash-map :span (span %) :as :strong) (reverse (range 9)))
            {:keys [score dropped]} (score/normalize {:text text :dress dress})]
        (is (= (mapv span (range 1 9)) (mapv :span (:dress score))))
        (is (= [{:what :dress :why :too-many :index 8}] dropped))))))

;; --- normalize: junk ---------------------------------------------------------

(deftest junk-is-refused-or-cleaned
  (is (nil? (:score (score/normalize (sorted-map 1 2 3 4)))))
  (let [deep {:a [{:b #{1 "two" [nil {:c {:d [:e 5.5]}}]}}]}
        {:keys [score dropped]}
        (score/normalize {:text "still here"
                          :marks [deep [deep] (sorted-map 1 2) {:span deep :kind deep}
                                  {:span [0 5] :kind :stress :amount 0.5 :patch deep}]
                          :dress [deep nil {:span "0-5" :as :gilt}]
                          :style (sorted-map 1 deep)
                          :shot deep
                          deep deep})]
    (is (= {:text "still here" :marks [(stress 0 5 0.5)] :dress []} score))
    (is (= #{[:key :unknown-key] [:mark :invalid] [:patch :patch-problems]
             [:dress :invalid] [:style :invalid] [:shot :invalid]}
           (set (whys {:dropped dropped}))))))

;; --- from-text ---------------------------------------------------------------

(deftest from-text-scores-the-acceptance-sentence
  (let [s (score/from-text sentence lavish)]
    (is (schema/valid? :ha/score s))
    (is (= {:text sentence
            :marks [{:span [2 9] :kind :hold :amount 0.7 :warmth 0.3}
                    {:span [16 24] :kind :aside :amount 0.6}
                    {:span [27 30] :kind :turn :amount 0.5}
                    {:span [31 36] :kind :doubt :amount 0.5}
                    {:span [42 49] :kind :land :amount 0.7}
                    {:span [47 49] :kind :stress :amount 0.6 :energy 0.8}]
            :dress [{:span [16 24] :as :whisper}]
            :style lavish}
           s))
    (is (= {:score s :dropped []} (score/normalize s))))
  (is (= :plain (:style (score/from-text sentence :plain))) "a named style is kept")
  (doseq [text ["" "   " nil 5 [:text]]]
    (is (nil? (score/from-text text lavish)))))

;; --- properties --------------------------------------------------------------

(def ^:private gen-word
  (gen/frequency
   [[8 (gen/fmap str/join (gen/vector (gen/elements ["a" "e" "k" "t" "É" "." "!" ","]) 1 6))]
    [1 (gen/elements ["🚀" "🎉🎉" "(x)"])]]))

(def ^:private gen-text
  (gen/fmap (fn [[ws gaps]] (str/join (interleave ws (concat gaps [""]))))
            (gen/bind (gen/choose 1 12)
                      (fn [n]
                        (gen/tuple (gen/vector gen-word n)
                                   (gen/vector (gen/elements [" " "  " "\n" " \t "]) (dec n)))))))

(defn- gen-offered-mark
  "A mark as an agent might send it: often sound, often not."
  [n]
  (gen/let [from (gen/choose -1 (+ n 2))
            width (gen/choose 0 6)
            kind (gen/elements [:stress :stress :hold :aside :turn :land :shout])
            amount (gen/elements [0 0.2 0.5 0.5 0.9 1 1.5])
            extra (gen/elements [{} {} {:patch safe-patch} {:patch restless-patch}
                                 {:patch 5} {:colour "red"} {:energy 0.5}])]
    (merge {:span [from (+ from width)] :kind kind :amount amount} extra)))

(defn- gen-offered-dress [n]
  (gen/let [from (gen/choose -1 (+ n 2))
            width (gen/choose 0 6)
            as (gen/elements [:versal :rubric :gilt :strong :whisper :neon])]
    {:span [from (+ from width)] :as as}))

(def ^:private gen-offered-score
  (gen/let [text gen-text
            marks (gen/vector (gen/one-of [(gen-offered-mark (count text)) gen/any]) 0 24)
            dress (gen/vector (gen-offered-dress (count text)) 0 12)
            style (gen/elements [:plain :elegant :baroque lavish nil])
            extra (gen/elements [{} {:shot :card} {:shot 3} {:html "<b>"}])]
    (merge {:text text :marks marks :dress dress :style style} extra)))

(defn- normal?
  "Everything normalize promises of what it returns for x."
  [x]
  (let [{:keys [score dropped]} (score/normalize x)
        n (count (:text score))
        inside? (fn [{[_ to] :span}] (<= to n))]
    (and (vector? dropped)
         (every? (comp known-whys :why) dropped)
         (if (nil? score)
           (= [{:what :score :why :no-text}] dropped)
           (and (schema/valid? :ha/score score)
                (every? inside? (:marks score))
                (every? inside? (:dress score))
                (in-order? (:marks score))
                (in-order? (:dress score))
                (= {:score score :dropped []} (score/normalize score)))))))

(defspec normalize-never-throws-on-anything 300
  (prop/for-all [x gen/any]
    (normal? x)))

(defspec normalize-never-throws-on-anything-under-the-score-keys 200
  (prop/for-all [text (gen/one-of [gen-text gen/any])
                 marks gen/any
                 dress gen/any
                 style gen/any
                 shot gen/any]
    (normal? {:text text :marks marks :dress dress :style style :shot shot})))

(defspec normalize-makes-offered-scores-safe-and-is-idempotent 300
  (prop/for-all [x gen-offered-score]
    (let [{:keys [score dropped]} (score/normalize x)]
      (and (normal? x)
           (some? score)
           (= (:text x) (:text score))
           ;; every mark offered is either kept or accounted for
           (= (count (:marks x))
              (+ (count (:marks score))
                 (count (filter #(= :mark (:what %)) dropped))))
           (= (count (:dress x))
              (+ (count (:dress score))
                 (count (filter #(= :dress (:what %)) dropped))))))))
