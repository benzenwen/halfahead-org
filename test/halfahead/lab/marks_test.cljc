(ns halfahead.lab.marks-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [halfahead.lab.marks :as marks]
            [halfahead.lab.style :as style]
            [halfahead.schema :as schema]))

;; --- helpers ----------------------------------------------------------------

(defn- of-kind [kind ms] (filterv #(= kind (:kind %)) ms))

(defn- read-kind [kind text] (of-kind kind (marks/read text)))

(defn- span-of
  "The span of the first sub in text."
  [text sub]
  (let [from (str/index-of text sub)]
    [from (+ from (count sub))]))

(defn- unit-code [s i]
  #?(:clj (int (.charAt ^String s i)) :cljs (.charCodeAt s i)))

(defn- splits-pair?
  "Whether index i falls between a high and a low surrogate of text."
  [text i]
  (and (< 0 i (count text))
       (<= 0xD800 (unit-code text (dec i)) 0xDBFF)
       (<= 0xDC00 (unit-code text i) 0xDFFF)))

(defn- in-order? [spanned]
  (let [ks (map (fn [{[from to] :span}] [from (- to)]) spanned)]
    (= ks (sort ks))))

(defn- lavish
  "A point that performs every mark, at this energy and softness."
  [energy softness]
  {:energy energy :flourish 1.0 :softness softness :warmth 0.5})

(defn- effect-at [point mark] (:effect (marks/gesture-for point mark)))

(defn- close-to? [a b] (< (Math/abs (- a b)) 1e-9))

;; --- read: the acceptance sentence -------------------------------------------

(deftest reads-the-acceptance-sentence
  (is (= [{:span [2 9] :kind :hold :amount 0.7 :warmth 0.3}
          {:span [16 24] :kind :aside :amount 0.6}
          {:span [27 30] :kind :turn :amount 0.5}
          {:span [31 36] :kind :doubt :amount 0.5}
          {:span [42 49] :kind :land :amount 0.7}
          {:span [47 49] :kind :stress :amount 0.6 :energy 0.8}]
         (marks/read "I loooove this (honestly). But maybe not. Ship it!"))))

;; --- read: one tell at a time ------------------------------------------------

(deftest repeated-letters-hold
  (is (= [{:span [0 4] :kind :hold :amount 0.6 :warmth 0.3}]
         (marks/read "sooo close")))
  (is (= [{:span [3 7] :kind :hold :amount 0.6 :warmth 0.3}]
         (marks/read "oh SoOo close"))
      "case does not matter")
  (is (= [{:span [0 10] :kind :hold :amount 1.0 :warmth 0.3}]
         (marks/read "nooooooooo"))
      "the amount stops at 1")
  (is (= [] (marks/read "too good")) "two in a row is spelling")
  (is (= [] (marks/read "1000 --- ~~~")) "only letters count")
  (is (= [] (marks/read "哈哈哈")) "a word of one letter is not drawn out")
  (is (= [{:span [0 4] :kind :hold :amount 0.6 :warmth 0.3}] (marks/read "好哈哈哈"))
      "a letter need not have a case"))

(deftest a-word-of-one-letter-is-not-held
  (doseq [text ["www" "WWW" "III" "XXX" "zzz" "ooo!" "zzz😴" "www🎉" "'www'"
                "‘III’" "zzz-" "www2"]]
    (is (= [] (read-kind :hold text)) text))
  (doseq [text ["sooo" "nooo" "sooo😴" "III's" "XXX-rated"]]
    (is (= [{:span [0 (count text)] :kind :hold :amount 0.6 :warmth 0.3}]
           (read-kind :hold text))
        text))
  (doseq [text ["WWW" "III" "XXX"]]
    (is (= [{:span [0 3] :kind :stress :amount 0.8 :energy 0.7}] (marks/read text))
        "capitals still stress")))

(deftest urls-paths-and-addresses-are-not-held
  (doseq [text ["www.example.com" "see http://sooo.com now" "mail a@booo.com"
                "/usr/looool/x"]]
    (is (= [] (read-kind :hold text)) text))
  (doseq [[text word] [["nooo." "nooo"] ["nooo: yes" "nooo"] ["sooo...good" "sooo"]]]
    (is (= [{:span (span-of text word) :kind :hold :amount 0.6 :warmth 0.3}]
           (read-kind :hold text))
        "punctuation at the word's edge is not an address"))
  (is (= [] (read-kind :hold "nooo:D"))
      "known miss: an emoticon glued to the word reads as an address"))

(deftest combining-marks-inside-a-hold
  (let [o "o\u0301"]
    (is (= [{:span [0 7] :kind :hold :amount 0.6 :warmth 0.3}]
           (marks/read (str "s" o o o))))
    (is (= [{:span [0 5] :kind :hold :amount 0.6 :warmth 0.3}]
           (marks/read (str "so" o "o")))
        "an accent on one of them does not break the run")
    (is (= [] (marks/read (str o o o))) "one letter, however accented")))

(deftest capitals-stress
  (is (= [{:span [3 6] :kind :stress :amount 0.8 :energy 0.7}]
         (marks/read "go NOW ok")))
  (is (= [] (marks/read "I am a B student")) "one capital letter is not shouting")
  (is (= [{:span [0 11] :kind :stress :amount 0.8 :energy 0.7}]
         (marks/read "SHIP IT NOW"))
      "neighbours are one mark")
  (is (= [[0 4] [6 8]] (mapv :span (marks/read "SHIP  IT")))
      "two spaces keep them apart")
  (is (= [[0 4] [8 10]] (mapv :span (marks/read "SHIP an IT")))))

(deftest asterisks-stress
  (is (= [{:span [7 18] :kind :stress :amount 0.7}]
         (marks/read "it is *really good* now")))
  (is (= "really good" (subs "it is *really good* now" 7 18)))
  (is (= [] (marks/read "2 * 3 * 4")) "a space just inside is arithmetic")
  (is (= [{:span [2 6] :kind :stress :amount 0.7}] (marks/read "**bold**")))
  (is (= [] (marks/read "a lone * star"))))

(deftest parentheses-aside
  (is (= [{:span [6 16] :kind :aside :amount 0.6}]
         (marks/read "fine (not really) ok")))
  (is (= [] (marks/read "()")))
  (is (= [{:span [2 4] :kind :aside :amount 0.6}] (marks/read "( hm )")))
  (is (= [{:span [6 7] :kind :aside :amount 0.6}] (marks/read "a (b (c) d"))
      "the innermost pair")
  (is (= [] (marks/read "never closed (like this"))))

(deftest ellipsis-holds
  (is (= [{:span [0 4] :kind :hold :amount 0.5 :energy -0.4}]
         (read-kind :hold "wait… what")))
  (is (= [{:span [4 8] :kind :hold :amount 0.5 :energy -0.4}]
         (read-kind :hold "oh, well.... never mind that now")))
  (is (= [] (read-kind :hold "wait.. what")) "two full stops are not an ellipsis")
  (is (= [] (read-kind :hold "wait … what")) "it has to touch the word"))

(deftest hedges-doubt
  (doseq [hedge ["maybe" "perhaps" "probably" "i guess" "sort of" "kind of"]]
    (is (= [{:span [5 (+ 5 (count hedge))] :kind :doubt :amount 0.5}]
           (marks/read (str "well " hedge " so")))
        hedge))
  (is (= [{:span [0 7] :kind :doubt :amount 0.5}] (marks/read "I Guess so")))
  (is (= [] (marks/read "two maybes")) "whole words only")
  (is (= [] (read-kind :doubt "sort, of")) "the words of a hedge stand together"))

(deftest question-doubts-its-last-word
  (is (= [{:span [6 10] :kind :doubt :amount 0.4}] (marks/read "is it done?")))
  (is (= [{:span [1 7] :kind :doubt :amount 0.4}]
         (read-kind :doubt "(really?) I see"))
      "a closing bracket after the mark still ends the sentence")
  (is (= [{:span [0 4] :kind :doubt :amount 0.4}] (read-kind :doubt "what!?"))
      "the last character decides"))

(deftest exclamation-stresses-its-last-word
  (is (= [{:span [6 9] :kind :stress :amount 0.6 :energy 0.8}]
         (marks/read "do it now!")))
  (is (= [{:span [6 9] :kind :stress :amount 0.9 :energy 0.8}]
         (marks/read "do it now!!")))
  (is (= [{:span [0 4] :kind :stress :amount 0.6 :energy 0.8}]
         (marks/read "what?!")))
  (is (= [] (marks/read "do it now.")))
  (is (= [] (read-kind :stress "3!4 is not a sentence end"))))

(deftest turning-words-turn
  (doseq [word ["but" "however" "yet" "although" "instead" "actually"]]
    (is (= [{:span [3 (+ 3 (count word))] :kind :turn :amount 0.5}]
           (marks/read (str "ok " word " fine")))
        word))
  (is (= [{:span [0 7] :kind :turn :amount 0.5}] (marks/read "However it went")))
  (is (= [] (marks/read "the button and the yeti")) "whole words only"))

(deftest quotes-voice
  (is (= [{:span [10 17] :kind :voice :amount 0.5}]
         (marks/read "she said \"not now\" twice")))
  (is (= [{:span [3 10] :kind :voice :amount 0.5}]
         (marks/read "x “ok then” y")))
  (is (= [[1 2] [7 8]] (mapv :span (marks/read "\"a\" b \"c\"")))
      "straight quotes pair left to right")
  (is (= [] (marks/read "an open \"quote"))))

(deftest short-last-sentence-lands
  (is (= [{:span [10 17] :kind :land :amount 0.7}] (marks/read "It works. Ship it.")))
  (is (= [{:span [10 14] :kind :land :amount 0.7}] (marks/read "It works. Done")))
  (is (= [] (marks/read "Ship it.")) "a lone sentence is not a landing")
  (is (= [] (marks/read "It works. Now we ship it.")) "four words is a sentence"))

(deftest an-ellipsis-does-not-land
  (is (= [{:span [10 12] :kind :hold :amount 0.5 :energy -0.4}]
         (marks/read "It works. So... yes.")))
  (let [text "one. two...\nthree"]
    (is (= [] (read-kind :land text)))
    (is (= [{:span (span-of text "two") :kind :hold :amount 0.5 :energy -0.4}]
           (read-kind :hold text))))
  (doseq [text ["Ok. I don't know... maybe" "It works. Done..." "It works. Done…"
                "It works. Done….\"" "It works. Done...)" "It works. sooo...good"
                "Ok. Really...?"]]
    (is (= [] (read-kind :land text)) text))
  (let [text "So...? Yes."]
    (is (= [{:span (span-of text "Yes") :kind :land :amount 0.7}] (read-kind :land text))
        "'?' after an ellipsis ends the sentence"))
  (let [text "Hmm... ok. Ship it."]
    (is (= [{:span (span-of text "Ship it") :kind :land :amount 0.7}]
           (read-kind :land text))
        "an ellipsis in an earlier sentence does not matter"))
  (is (= [{:span [10 17] :kind :land :amount 0.7}] (marks/read "It works. Ship it.")))
  (is (= [] (read-kind :build "one, two... three, four"))
      "a list still stops at an ellipsis"))

(deftest comma-lists-build
  (is (= [{:span [0 24] :kind :build :amount 0.6}]
         (marks/read "bigger, faster, stranger")))
  (is (= [] (marks/read "red, blue")) "two items are a pair")
  (is (= [{:span [16 32] :kind :build :amount 0.6}]
         (marks/read "we want it all, red, green, blue."))
      "a long clause is not an item")
  (is (= [] (marks/read "we went to the shop, bought eggs, came home"))
      "nor does it leave three behind")
  (is (= [{:span [5 12] :kind :build :amount 0.6}] (marks/read "a, , b, c, d"))
      "an empty item breaks the run")
  (is (= [] (marks/read "It cost 1,000,000 dollars."))
      "a comma with no space after it separates nothing")
  (is (= [{:span [0 14] :kind :build :amount 0.6}] (marks/read "1,000, 2, 3,50")))
  (is (= [] (read-kind :build "one, two. three, four."))
      "a list stays inside its sentence"))

(deftest tells-after-emoji-and-combining-marks
  (let [prefix "👩\u200d🚀 e\u0301te\u0301 "
        text (str prefix "sooo (ok) BIG DEAL")]
    (is (= 12 (count prefix)) "two pairs, a joiner and two combining accents")
    (is (= [{:span [12 16] :kind :hold :amount 0.6 :warmth 0.3}
            {:span [18 20] :kind :aside :amount 0.6}
            {:span [22 30] :kind :stress :amount 0.8 :energy 0.7}]
           (marks/read text)))
    (is (= ["sooo" "ok" "BIG DEAL"]
           (mapv (fn [{[from to] :span}] (subs text from to)) (marks/read text)))))
  (is (= [{:span [0 2] :kind :stress :amount 0.9 :energy 0.8}]
         (marks/read "go 😀!!"))
      "an emoji is not a word: the last word before it is stressed")
  (let [text "🎉🎉 cafe\u0301… maybe"]
    (is (= [{:span (span-of text "cafe\u0301") :kind :hold :amount 0.5 :energy -0.4}
            {:span (span-of text "maybe") :kind :doubt :amount 0.5}]
           (marks/read text))
        "trailing off into 'maybe' is not a landing")
    (is (= [5 10] (span-of text "cafe\u0301")))))

;; A run of emoji, dashes or symbols is not a word, and does not hold a
;; sentence open when it follows the closing mark directly.
(deftest emoji-and-symbols-are-not-words
  (let [text "It works. Ship it! 🚀"]
    (is (= [{:span (span-of text "Ship it") :kind :land :amount 0.7}
            {:span [15 17] :kind :stress :amount 0.6 :energy 0.8}]
           (marks/read text))))
  (let [text "Thanks so much for this. See you tomorrow! 🎉"]
    (is (= [{:span (span-of text "See you tomorrow") :kind :land :amount 0.7}
            {:span (span-of text "tomorrow") :kind :stress :amount 0.6 :energy 0.8}]
           (marks/read text))
        "the landing is the three words, never the emoji"))
  (let [text "Good. Ship it — now"]
    (is (= [{:span (span-of text "Ship it — now") :kind :land :amount 0.7}]
           (marks/read text))
        "a dash is not a fourth word"))
  (is (= [] (marks/read "It works. We ship it now 🚀 — ✨"))
      "four words, however many symbols")
  (is (= [{:span [7 9] :kind :stress :amount 0.6 :energy 0.8}]
         (marks/read "I love it!❤\ufe0f")))
  (is (= [{:span [6 10] :kind :doubt :amount 0.4}] (marks/read "Is it done?😀")))
  (let [text "It works. Ship it!🚀"]
    (is (= [{:span (span-of text "Ship it") :kind :land :amount 0.7}
            {:span [15 17] :kind :stress :amount 0.6 :energy 0.8}]
           (marks/read text))))
  (is (= [] (marks/read "version 3.5 of e.g this")) "a word right after keeps it whole")
  (is (= [] (marks/read "🚀 ✨ — !!")) "no word, nothing to mark"))

(deftest a-quoted-question-inside-a-sentence
  (let [text "\"what?\", she said"]
    (is (= [{:span [1 6] :kind :voice :amount 0.5}
            {:span [1 5] :kind :doubt :amount 0.4}]
           (marks/read text))
        "the question doubts, and the sentence goes on: nothing lands"))
  (is (= [:voice :stress]
         (mapv :kind (marks/read "\"Stop!\"; then he left the room quietly.")))))

;; --- read: overlap, the cap, totality ----------------------------------------

(deftest same-kind-overlap-keeps-stronger
  (is (= [{:span [0 7] :kind :stress :amount 0.8 :energy 0.7}]
         (marks/read "SHIP IT!"))
      "capitals outweigh one '!'")
  (is (= [{:span [5 7] :kind :stress :amount 0.9 :energy 0.8}]
         (marks/read "SHIP IT!!"))
      "two '!' outweigh capitals")
  (is (= [{:span [0 4] :kind :hold :amount 0.6 :warmth 0.3}] (marks/read "sooo…")))
  (is (= [{:span [1 6] :kind :stress :amount 0.8 :energy 0.7}
          {:span [1 6] :kind :aside :amount 0.6}
          {:span [1 6] :kind :doubt :amount 0.5}]
         (marks/read "(MAYBE)"))
      "different kinds share a span"))

(deftest at-most-sixteen-strongest
  (let [text (str/join " " (repeat 10 "(a) maybe NO sooo"))
        ms (marks/read text)]
    (is (= 16 (count ms)))
    (is (= {:stress 10 :aside 3 :hold 3} (frequencies (map :kind ms)))
        "all ten at 0.8, then the earliest at 0.6; none at 0.5")
    (is (in-order? ms))
    (is (every? #(schema/valid? :ha/mark %) ms))))

(deftest read-is-total
  (is (= [] (marks/read "")))
  (is (= [] (marks/read "   \n\t ")))
  (is (= [] (marks/read "?!… ** () \"\" ,,,")))
  (is (vector? (marks/read (str (char 0xDC00) "!!" (char 0xD83D))))))

;; --- feelings ----------------------------------------------------------------

(deftest feelings-and-feel
  (is (= {:tender [-0.3 0.9] :joyful [0.6 0.8] :giddy [0.9 0.7]
          :calm [-0.7 0.4] :wistful [-0.5 0.2] :awed [0.3 0.6]
          :grave [-0.4 -0.2] :anxious [0.6 -0.5] :furious [0.9 -0.9]
          :cold [-0.3 -0.8] :wry [0.1 -0.2] :urgent [0.9 -0.1]}
         marks/feelings))
  (is (= {:energy -0.3 :warmth 0.9} (marks/feel :tender)))
  (is (= {:energy 0.9 :warmth -0.9} (marks/feel :furious)))
  (is (nil? (marks/feel :nope)))
  (doseq [k (keys marks/feelings)]
    (is (schema/valid? :ha/mark (merge {:span [0 1] :kind :stress :amount 0.5}
                                       (marks/feel k)))
        (str k))))

;; --- gesture-for -------------------------------------------------------------

(deftest gesture-table
  (let [m (fn [kind & {:as more}] (merge {:span [0 3] :kind kind :amount 0.6} more))
        crisp-calm (lavish 0.3 0.2)
        soft-calm (lavish 0.3 0.8)
        crisp-lively (lavish 0.8 0.2)
        soft-lively (lavish 0.8 0.8)]
    (testing ":stress"
      (is (= :bloom (effect-at crisp-lively (m :stress :warmth 0.4))) "tender")
      (is (= :bloom (effect-at soft-calm (m :stress :warmth 0.9 :energy 0.9))))
      (is (= :slam (effect-at crisp-calm (m :stress :warmth -0.4))) "harsh, crisp")
      (is (= :slam (effect-at crisp-calm (m :stress :energy 0.6))) "excited, crisp")
      (is (= :glow (effect-at soft-calm (m :stress :warmth -0.4))) "harsh, soft")
      (is (= :glow (effect-at soft-calm (m :stress :energy 0.6))) "excited, soft")
      (is (= :glow (effect-at soft-calm (m :stress))) "mild, soft")
      (is (= :glow (effect-at (lavish 0.3 0.5) (m :stress))) "soft starts at 0.5")
      (is (= :pop (effect-at crisp-calm (m :stress))) "mild, crisp")
      (is (= :pop (effect-at crisp-calm (m :stress :warmth 0.39 :energy 0.59)))))
    (testing "kinds with one gesture"
      (doseq [point [crisp-calm soft-calm crisp-lively soft-lively]]
        (is (= :swell (effect-at point (m :hold))))
        (is (= :hush (effect-at point (m :aside))))
        (is (= :draw (effect-at point (m :voice))))))
    (testing ":turn"
      (is (= :rewind (effect-at (lavish 0.6 0.5) (m :turn))))
      (is (= :nod (effect-at (lavish 0.59 0.5) (m :turn)))))
    (testing ":doubt"
      (is (= :shiver (effect-at soft-calm (m :doubt :energy 0.5 :warmth -0.3))))
      (is (= :shiver (effect-at crisp-lively (m :doubt :energy 0.5 :warmth -0.3))))
      (is (= :wobble (effect-at crisp-lively (m :doubt))))
      (is (= :wobble (effect-at crisp-lively (m :doubt :energy 0.5 :warmth -0.2))))
      (is (= :lean (effect-at soft-calm (m :doubt))))
      (is (= :lean (effect-at soft-calm (m :doubt :energy 0.4 :warmth -0.9)))))
    (testing ":build"
      (is (= :launch (effect-at crisp-lively (m :build))))
      (is (= :shimmer (effect-at crisp-calm (m :build)))))
    (testing ":land"
      (is (= :slam (effect-at crisp-lively (m :land))))
      (is (= :nod (effect-at soft-lively (m :land))))
      (is (= :nod (effect-at crisp-calm (m :land)))))
    (testing "a spare style performs only strong marks"
      (is (some? (marks/gesture-for :plain (m :hold :amount 0.62))))
      (is (nil? (marks/gesture-for :plain (m :hold :amount 0.61))))
      (is (nil? (marks/gesture-for (style/point :plain) (m :hold :amount 0.61))))
      (is (some? (marks/gesture-for (assoc (style/point :plain) :flourish 0.0)
                                    (m :hold :amount 0.9))))
      (is (nil? (marks/gesture-for (assoc (style/point :plain) :flourish 0.0)
                                   (m :hold :amount 0.89)))
          "with no flourish at all, only 0.9 and over")
      (is (some? (marks/gesture-for :elegant (m :hold :amount 0.54))))
      (is (nil? (marks/gesture-for :elegant (m :hold :amount 0.53)))))
    (testing "a mark's own gesture always performs, and is the effect"
      (is (= :quake (effect-at :plain (m :hold :amount 0.1 :gesture :quake))))
      (is (= :quake (effect-at crisp-lively (m :stress :warmth 0.9 :gesture :quake)))))
    (testing "intensity"
      (doseq [energy [0.5 1.0 0.0]]
        (is (= 0.6 (:intensity (marks/gesture-for (lavish energy 0.5) (m :hold))))
            "the amount, whatever the energy"))
      (is (= 1.0 (:intensity (marks/gesture-for (lavish 1.0 0.5) (m :hold :amount 1.0)))))
      (is (= 0.2 (:intensity (marks/gesture-for (lavish 0.0 0.5) (m :hold :amount 0.2)))))
      (is (= 0.1 (:intensity (marks/gesture-for :plain (m :hold :amount 0.0 :gesture :pop))))))
    (testing "what read finds, as the named styles perform it"
      (let [ms (marks/read "I loooove this (honestly). But maybe not. Ship it!")]
        (is (= [:swell nil nil nil :nod nil] (mapv #(effect-at :plain %) ms))
            "plain performs the hold and the landing (0.7), nothing fainter")
        (is (= [:swell nil nil nil :nod nil] (mapv #(effect-at (style/point :plain) %) ms)))
        (is (= [nil nil nil nil nil nil]
               (mapv #(effect-at (assoc (style/point :plain) :flourish 0.0) %) ms))
            "no flourish performs nothing here")
        (is (= [:swell :hush :rewind :wobble :slam :slam]
               (mapv #(effect-at :caffeinated %) ms)))
        (is (= [:swell :hush nil nil :nod :glow]
               (mapv #(effect-at :elegant %) ms)))
        (is (= [0.7 0.6 0.7 0.6]
               (keep #(:intensity (marks/gesture-for :elegant %)) ms))
            "elegant plays them at their amounts")))))

(deftest calm-styles-keep-their-strength
  (let [held {:span [0 7] :kind :hold :amount 0.7}]
    (is (= {:effect :swell :intensity 0.7}
           (marks/gesture-for (style/point :elegant) held)))
    (is (= 0.7 (:intensity (marks/gesture-for (style/point :caffeinated) held))))))

(deftest a-mark-on-the-threshold-performs
  (let [at (fn [flourish amount]
             (marks/gesture-for {:energy 0.5 :flourish flourish :softness 0.5 :warmth 0.5}
                                {:span [0 3] :kind :hold :amount amount}))]
    (is (some? (at 0.6 0.42)) "0.9 - 0.8 * 0.6 computes to 0.42000000000000004")
    (is (some? (at 0.7 0.34)) "0.9 - 0.8 * 0.7 computes to 0.3400000000000001")
    (is (nil? (at 0.6 0.419)) "the slack is not a loosening")
    (is (nil? (at 0.7 0.339)))))

;; --- dress-for ---------------------------------------------------------------

(def ^:private long-text
  "one two three four five six seven eight nine ten eleven twelve")

(deftest dress-rules
  (let [warm {:energy 0.5 :flourish 1.0 :softness 0.5 :warmth 0.5}
        cool (assoc warm :warmth 0.49)
        flourish (fn [f] (assoc warm :flourish f))
        aside {:span [20 30] :kind :aside :amount 0.6}
        strong {:span [0 4] :kind :stress :amount 0.7}
        stronger {:span [10 14] :kind :stress :amount 0.9}
        faint {:span [40 44] :kind :stress :amount 0.69}
        ms [strong stronger aside faint]]
    (testing "asides whisper; the strongest stress is gilt or rubric; others strong"
      (is (= [{:span [0 4] :as :strong}
              {:span [10 14] :as :gilt}
              {:span [20 30] :as :whisper}]
             (marks/dress-for warm "" ms)))
      (is (= [{:span [0 4] :as :strong}
              {:span [10 14] :as :rubric}
              {:span [20 30] :as :whisper}]
             (marks/dress-for cool "" ms)))
      (is (= [{:span [0 4] :as :gilt} {:span [50 54] :as :strong}]
             (marks/dress-for warm "" [(assoc strong :span [50 54]) strong]))
          "of two as strong, the earlier is the strongest"))
    (testing "other kinds leave nothing"
      (is (= [] (marks/dress-for warm "" (mapv #(assoc aside :kind %)
                                               [:hold :turn :doubt :build :land :voice])))))
    (testing "flourish gates"
      (is (= [] (marks/dress-for (flourish 0.19) long-text ms)))
      (is (= [:whisper] (mapv :as (marks/dress-for (flourish 0.2) long-text ms))))
      (is (= [:whisper] (mapv :as (marks/dress-for (flourish 0.29) long-text ms))))
      (is (= [:strong :gilt :whisper]
             (mapv :as (marks/dress-for (flourish 0.3) long-text ms))))
      (is (= [:strong :gilt :whisper]
             (mapv :as (marks/dress-for (flourish 0.59) long-text ms))))
      (is (= [{:span [0 4] :as :strong} {:span [0 1] :as :versal}
              {:span [10 14] :as :gilt} {:span [20 30] :as :whisper}]
             (marks/dress-for (flourish 0.6) long-text ms))
          "ordered like marks: by start, the longer span first"))
    (testing "the versal"
      (is (= [{:span [0 1] :as :versal}] (marks/dress-for warm long-text [])))
      (is (= [] (marks/dress-for warm (subs long-text 4) [])) "eleven words")
      (is (= [] (marks/dress-for warm (str "(" long-text ")") [])) "no leading letter")
      (is (= [] (marks/dress-for warm (str "1 " long-text) [])))
      (is (= [] (marks/dress-for warm "" [])))
      (is (= [] (marks/dress-for warm (str "one 🚀 — ✨ " (subs long-text 8)) []))
          "emoji and dashes are not words")
      (is (= [{:span [0 2] :as :versal}]
             (marks/dress-for warm (str "e\u0301 " (subs long-text 4)) []))
          "the accent stays on its letter")
      (is (= [{:span [0 1] :as :versal}]
             (marks/dress-for warm (str "שלום " long-text) []))
          "a letter need not have a case"))
    (testing "a span is dressed once"
      (is (= [{:span [0 4] :as :gilt}]
             (marks/dress-for warm "" [strong (assoc strong :amount 0.8)])))
      (is (= [{:span [0 1] :as :versal}]
             (marks/dress-for warm long-text [(assoc stronger :span [0 1])]))))
    (testing "named styles are accepted"
      (is (= [{:span [10 14] :as :gilt}] (marks/dress-for :elegant "" [stronger])))
      (is (= [{:span [10 14] :as :rubric}] (marks/dress-for :coarse "" [stronger])))
      (is (= [{:span [0 4] :as :strong} {:span [10 14] :as :gilt} {:span [20 30] :as :whisper}]
             (marks/dress-for :plain long-text ms))
          "Plain whispers, gilds (its warmth is 0.5) and sets strong, but gives no versal"))
    (testing "Plain's point, on what read finds"
      (let [sentence "I loooove this (honestly). But maybe not. Ship it!"]
        (is (= [{:span [16 24] :as :whisper}]
               (marks/dress-for (style/point :plain) sentence (marks/read sentence)))
            "the aside's whisper: its stress (0.6) is not strong enough to dress")))
    (testing "at most 8: whispers go first"
      (let [stresses (mapv (fn [i] {:span [(* 10 i) (+ 4 (* 10 i))] :kind :stress
                                    :amount (+ 0.7 (* 0.02 i))})
                           (range 6))
            asides (mapv (fn [i] {:span [(+ 5 (* 10 i)) (+ 8 (* 10 i))] :kind :aside
                                  :amount (- 0.9 (* 0.1 i))})
                         (range 4))
            dress (marks/dress-for warm long-text (into asides stresses))]
        (is (= 8 (count dress)))
        (is (= {:versal 1 :gilt 1 :strong 5 :whisper 1} (frequencies (map :as dress))))
        (is (= [50 54] (:span (first (filter #(= :gilt (:as %)) dress)))))
        (is (= [5 8] (:span (first (filter #(= :whisper (:as %)) dress))))
            "the strongest aside is the one kept")
        (is (in-order? dress))
        (is (every? #(schema/valid? :ha/dress %) dress))))))

;; --- properties --------------------------------------------------------------

(def ^:private gen-text
  (gen/one-of
   [gen/string
    (gen/fmap str/join
              (gen/vector
               (gen/elements ["a" "o" "ooo" "B" "IT" "NOW" "maybe" "but" "i guess"
                              " " " " "  " "\n" "*" "(" ")" "\"" "“" "”" "…" "."
                              "..." "," "!" "?" "😀" "\u0301" "é" "ß" "—" "7" "哈" "❤\ufe0f"
                              (str (char 0xD83D)) (str (char 0xDE00))])
               0 60))]))

(def ^:private gen-unit
  (gen/double* {:min 0.0 :max 1.0 :NaN? false :infinite? false}))

(def ^:private gen-signed
  (gen/double* {:min -1.0 :max 1.0 :NaN? false :infinite? false}))

(def ^:private gen-point
  (gen/hash-map :energy gen-unit :flourish gen-unit :softness gen-unit :warmth gen-unit))

(def ^:private gen-mark
  (gen/let [from (gen/choose 0 60)
            length (gen/choose 1 20)
            kind (gen/elements [:stress :hold :aside :turn :doubt :build :land :voice])
            amount gen-unit
            affect (gen/one-of [(gen/return {})
                                (gen/hash-map :energy gen-signed)
                                (gen/hash-map :energy gen-signed :warmth gen-signed)])
            gesture (gen/one-of [(gen/return {})
                                 (gen/hash-map :gesture (gen/elements [:pop :slam :draw]))])]
    (merge {:span [from (+ from length)] :kind kind :amount amount} affect gesture)))

(defspec read-is-valid-and-inside 300
  (prop/for-all [text gen-text]
    (let [ms (marks/read text)]
      (and (vector? ms)
           (<= (count ms) 16)
           (every? #(schema/valid? :ha/mark %) ms)
           (every? (fn [{[from to] :span}]
                     (and (<= 0 from) (< from to) (<= to (count text))
                          (not (splits-pair? text from))
                          (not (splits-pair? text to))))
                   ms)
           (in-order? ms)))))

(defspec read-keeps-no-same-kind-overlap 200
  (prop/for-all [text gen-text]
    (let [ms (marks/read text)]
      (every? (fn [[i a]]
                (every? (fn [b]
                          (let [[a-from a-to] (:span a) [b-from b-to] (:span b)]
                            (or (not= (:kind a) (:kind b))
                                (<= a-to b-from)
                                (<= b-to a-from))))
                        (subvec ms (inc i))))
              (map-indexed vector ms)))))

(defspec gesture-is-nil-or-valid 300
  (prop/for-all [point gen-point
                 mark gen-mark]
    (let [g (marks/gesture-for point mark)]
      (and (schema/valid? :ha/mark mark)
           (or (nil? g)
               (and (= #{:effect :intensity} (set (keys g)))
                    (schema/valid? :ha/word-effect (:effect g))
                    (schema/valid? :ha/unit (:intensity g))
                    (<= 0.1 (:intensity g))))
           (or (nil? (:gesture mark))
               (= (:gesture mark) (:effect g)))))))

(defspec intensity-is-the-amount-clamped 300
  (prop/for-all [point gen-point
                 energy gen-unit
                 mark gen-mark]
    (let [g (marks/gesture-for point mark)
          other (marks/gesture-for (assoc point :energy energy) mark)]
      (and (or (nil? g)
               (= (max 0.1 (min 1.0 (:amount mark))) (:intensity g)))
           (= (:intensity g) (:intensity other))))))

;; The threshold at flourish f/100 is the decimal 0.9 - 0.8 f/100, here
;; computed exactly in thousandths before it becomes a double.
(defspec threshold-grid-performs 400
  (prop/for-all [f (gen/choose 0 100)]
    (let [point {:energy 0.5 :flourish (/ f 100.0) :softness 0.5 :warmth 0.5}
          held (fn [thousandths]
                 {:span [0 3] :kind :hold :amount (/ thousandths 1000.0)})]
      (and (some? (marks/gesture-for point (held (- 900 (* 8 f)))))
           (nil? (marks/gesture-for point (held (- 899 (* 8 f)))))))))

(defspec flourish-is-monotone 300
  (prop/for-all [point gen-point
                 other gen-unit
                 mark gen-mark]
    (let [[lo hi] (sort [(:flourish point) other])]
      (or (nil? (marks/gesture-for (assoc point :flourish lo) mark))
          (some? (marks/gesture-for (assoc point :flourish hi) mark))))))

(defspec dress-is-valid 200
  (prop/for-all [point gen-point
                 text gen-text
                 written (gen/vector gen-mark 0 24)]
    (every? (fn [dress]
              (and (vector? dress)
                   (<= (count dress) 8)
                   (every? #(schema/valid? :ha/dress %) dress)
                   (in-order? dress)))
            [(marks/dress-for point text (marks/read text))
             (marks/dress-for point text written)])))
