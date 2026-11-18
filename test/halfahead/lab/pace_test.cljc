(ns halfahead.lab.pace-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [halfahead.lab.pace :as pace]
            [halfahead.lab.score :as score]
            [halfahead.schema :as schema]))

;; --- helpers ----------------------------------------------------------------

(def ^:private sentence "I loooove this (honestly). But maybe not. Ship it!")

(def ^:private lavish {:energy 0.5 :flourish 1.0 :softness 0.5 :warmth 0.5})

(defn- plain-score [text] {:text text :marks []})

(defn- mark [kind from to amount] {:span [from to] :kind kind :amount amount})

(defn- stays [reading] (mapv :ms (:stops reading)))

(defn- close-to? [a b] (< (Math/abs (- a b)) 1e-9))

;; --- words and beats ---------------------------------------------------------

(deftest words-are-runs-of-non-whitespace
  (is (= [[0 1] [2 9] [10 14] [15 26] [27 30] [31 36] [37 41] [42 46] [47 50]]
         (pace/words sentence)))
  (is (= [[2 4] [7 9] [10 14] [16 19]] (pace/words "  go   🚀 now!\t\nyes  "))
      "spans are in UTF-16 units: the rocket is two")
  (is (= [[0 1] [2 3] [4 5]] (pace/words "a\u00a0b c\u00a0")) "a no-break space is a space")
  (is (= [] (pace/words "")))
  (is (= [] (pace/words " \t\n "))))

(deftest a-beat-is-one-words-time
  (is (== 240 (pace/beat-ms {:wpm 250})))
  (is (== 150 (pace/beat-ms {:wpm 400})))
  (is (== 400 (pace/beat-ms {:wpm 150})))
  (is (close-to? 85.71428571428571 (pace/beat-ms {:wpm 700}))))

;; --- a paced reading ---------------------------------------------------------

(deftest reads-the-acceptance-sentence-at-250
  (let [r (pace/reading (score/from-text sentence lavish) {:wpm 250})]
    (is (schema/valid? :ha/reading r))
    (is (= {:duration-ms 3900
            :stops [{:span [0 1] :at-ms 0 :ms 240}
                    {:span [2 9] :at-ms 240 :ms 408}      ; held: 1.7 beats
                    {:span [10 14] :at-ms 648 :ms 240}
                    {:span [15 26] :at-ms 888 :ms 432}    ; long, ends a sentence
                    {:span [27 30] :at-ms 1320 :ms 240}
                    {:span [31 36] :at-ms 1560 :ms 240}
                    {:span [37 41] :at-ms 1800 :ms 360}
                    {:span [42 46] :at-ms 2160 :ms 240}
                    {:span [47 50] :at-ms 2400 :ms 360}]
            :entries [{:mark 0 :at-ms 240 :ms 1500}
                      {:mark 1 :at-ms 888 :ms 1500}
                      {:mark 2 :at-ms 1320 :ms 1500}
                      {:mark 3 :at-ms 1560 :ms 1500}
                      {:mark 4 :at-ms 2160 :ms 1500}
                      {:mark 5 :at-ms 2400 :ms 1500}]}
           (dissoc r :beat-ms)))
    (is (== 240 (:beat-ms r)))))

(deftest the-opts-may-be-left-out
  (let [s (score/from-text sentence lavish)]
    (is (= (pace/reading s {:wpm 250} {}) (pace/reading s {:wpm 250})))))

(deftest a-reading-with-no-marks-lasts-as-long-as-its-stops
  (is (= {:duration-ms 480 :beat-ms 240.0 :entries []
          :stops [{:span [0 2] :at-ms 0 :ms 240} {:span [3 8] :at-ms 240 :ms 240}]}
         (pace/reading (plain-score "hi there") {:wpm 250}))))

(deftest long-words-and-pauses-take-longer
  (is (= [240 312 240] (stays (pace/reading (plain-score "an extraordinary day") {:wpm 250})))
      "more than 8 units: 1.3 beats")
  (is (= [240 312] (stays (pace/reading (plain-score "12345678 123456789") {:wpm 250})))
      "8 units is not long; 9 are")
  (is (= [300 300 300 240] (stays (pace/reading (plain-score "one, two; three: four") {:wpm 250})))
      "a quarter beat at , ; and :")
  (is (= [360 360 360 360 240] (stays (pace/reading (plain-score "a. b! c? d… e") {:wpm 250})))
      "half a beat at the end of a sentence")
  (is (= [240 240] (stays (pace/reading (plain-score "\"what?\" (so.)") {:wpm 250})))
      "the pause goes by the word's last unit"))

(deftest a-hold-stretches-its-word
  (let [text "so very good, yes"
        held (fn [& holds] (stays (pace/reading {:text text :marks (vec holds)} {:wpm 250})))]
    (is (= [240 240 300 240] (held)))
    (is (= [240 360 300 240] (held (mark :hold 3 7 0.5))) "(1 + 0.5) beats")
    (is (= [240 240 600 240] (held (mark :hold 8 12 1))) "the pause is stretched too")
    (is (= [240 432 540 240] (held (mark :hold 3 12 0.8)))
        "a hold over two words stretches both")
    (is (= [240 432 300 240] (held (mark :hold 3 7 0.3) (mark :hold 5 7 0.8)))
        "the strongest hold on a word is the one that counts")
    (is (= [240 240 300 240] (held (mark :stress 3 7 1))) "only a hold holds")
    (testing "the stops stay contiguous"
      (is (= [0 240 672 1212]
             (mapv :at-ms (:stops (pace/reading {:text text :marks [(mark :hold 3 12 0.8)]}
                                                {:wpm 250}))))))))

(deftest a-mark-enters-with-the-first-word-it-touches
  (let [text "one two three four"
        r (pace/reading {:text text
                         :marks [(mark :build 5 12 0.5)       ; from inside 'two'
                                 (mark :stress 14 18 0.5)
                                 (mark :land 14 18 0.5)]}
                        {:wpm 250})]
    (is (= [{:mark 0 :at-ms 240 :ms 1500}
            {:mark 1 :at-ms 720 :ms 1500}
            {:mark 2 :at-ms 720 :ms 1500}]
           (:entries r))
        "by :at-ms, then by mark index")
    (is (= 2220 (:duration-ms r)))))

(deftest natural-lengths-are-cut-at-the-cap
  (let [text (str/join " " (repeat 16 "plain"))
        s {:text text
           :marks [(mark :stress 0 5 0.5) (mark :stress 60 65 0.6) (mark :stress 90 95 0.7)]}
        ms-of (fn [natural] (fn [m] (get natural (:amount m))))
        entries (fn [natural] (:entries (pace/reading s {:wpm 250} {:ms-of (ms-of natural)})))]
    (is (= [0 2400 3600] (mapv :at-ms (entries {0.5 1500 0.6 1500 0.7 1500}))))
    (is (= [{:mark 0 :at-ms 0 :ms 4000}
            {:mark 1 :at-ms 2400 :ms 2600}      ; 5000 - 2400
            {:mark 2 :at-ms 3600 :ms 1400}]     ; 5000 - 3600
           (entries {0.5 4000 0.6 4000 0.7 3500}))
        "exactly 0.4 of its natural length is enough")
    (is (= [{:mark 0 :at-ms 0 :ms 5000} {:mark 1 :at-ms 2400 :ms 2600}]
           (entries {0.5 9000 0.6 6500 0.7 3501}))
        "less than 0.4 is left out")
    (is (= 5000 (:duration-ms (pace/reading s {:wpm 250} {:ms-of (constantly 9000)}))))
    (is (= [{:mark 0 :at-ms 0 :ms 1200}]
           (:entries (pace/reading {:text "hi" :marks [(mark :stress 0 2 0.5)]}
                                   {:wpm 250} {:ms-of (constantly 1200.4)})))
        "a natural length is taken in whole ms")))

;; --- highlights --------------------------------------------------------------

(def ^:private sixty-words (str/join " " (repeat 60 "plain")))

(defn- nth-word [i] [(* 6 i) (+ 5 (* 6 i))])

(deftest a-long-message-gets-highlights
  (let [marks (mapv (fn [[i amount]] {:span (nth-word i) :kind :stress :amount amount})
                    [[2 0.5] [10 0.9] [20 0.5] [30 0.7] [40 0.2] [50 0.5]])
        r (pace/reading {:text sixty-words :marks marks} {:wpm 250})]
    (is (schema/valid? :ha/reading r))
    (is (= {:duration-ms 2700
            :beat-ms 240.0
            :stops []
            :entries [{:mark 0 :at-ms 0 :ms 1500}     ; 0.5, the earliest of three
                      {:mark 1 :at-ms 400 :ms 1500}   ; 0.9
                      {:mark 2 :at-ms 800 :ms 1500}   ; 0.5, the next earliest
                      {:mark 3 :at-ms 1200 :ms 1500}]} ; 0.7
           r))
    (is (= [{:mark 0 :at-ms 0 :ms 5000} {:mark 1 :at-ms 400 :ms 4600}
            {:mark 2 :at-ms 800 :ms 4200} {:mark 3 :at-ms 1200 :ms 3800}]
           (:entries (pace/reading {:text sixty-words :marks marks} {:wpm 250}
                                   {:ms-of (constantly 20000)})))
        "each is cut at the cap, however little of it is left")
    (is (= {:duration-ms 1900 :beat-ms 240.0 :stops []
            :entries [{:mark 0 :at-ms 0 :ms 1500} {:mark 1 :at-ms 400 :ms 1500}]}
           (pace/reading {:text sixty-words :marks (subvec marks 0 2)} {:wpm 250})))
    (is (= {:duration-ms 0 :beat-ms 240.0 :stops [] :entries []}
           (pace/reading (plain-score sixty-words) {:wpm 250})))))

(deftest the-paced-budget-is-4000-ms
  (let [n-words (fn [n] (plain-score (str/join " " (repeat n "plain"))))
        paced? (fn [n wpm] (= n (count (:stops (pace/reading (n-words n) {:wpm wpm})))))]
    (is (paced? 10 150))
    (is (not (paced? 11 150)))
    (is (paced? 16 250))
    (is (not (paced? 17 250)))
    (is (paced? 26 400))
    (is (not (paced? 27 400)))))

(deftest the-acceptance-sentence-is-too-long-at-150
  (is (= {:duration-ms 2700 :beat-ms 400.0 :stops []
          :entries [{:mark 0 :at-ms 0 :ms 1500} {:mark 1 :at-ms 400 :ms 1500}
                    {:mark 4 :at-ms 800 :ms 1500} {:mark 5 :at-ms 1200 :ms 1500}]}
         (pace/reading (score/from-text sentence lavish) {:wpm 150}))))

;; --- the head ----------------------------------------------------------------

(deftest the-head-moves-through-each-word
  (let [r (pace/reading (plain-score "hi there,  ok ") {:wpm 250})]
    (is (= [{:span [0 2] :at-ms 0 :ms 240}
            {:span [3 9] :at-ms 240 :ms 300}
            {:span [11 13] :at-ms 540 :ms 240}]
           (:stops r)))
    (is (== 0 (pace/head r -50)) "before the start it waits at the first word")
    (is (== 0 (pace/head r 0)))
    (is (== 1 (pace/head r 120)) "half way through 'hi'")
    (is (close-to? 1.5 (pace/head r 180)))
    (is (== 3 (pace/head r 240)) "at a boundary it is at the next word's start")
    (is (== 6 (pace/head r 390)))
    (is (== 11 (pace/head r 540)) "and it skips the gap between words")
    (is (close-to? 12.5 (pace/head r 720)))
    (is (== 13 (pace/head r 780)) "once the stops are over, the last word's end")
    (is (== 13 (pace/head r 60000))))
  (is (nil? (pace/head (pace/reading (plain-score sixty-words) {:wpm 250}) 100))
      "highlights have no head"))

;; --- properties (H6) ---------------------------------------------------------

(def ^:private gen-word
  (gen/frequency
   [[12 (gen/let [letters (gen/vector (gen/elements ["a" "e" "i" "n" "r" "s" "t" "É"]) 1 12)
                  end (gen/frequency [[6 (gen/return "")]
                                      [4 (gen/elements ["." "," "!" "?" ";" ":" "…"])]])]
          (str (str/join letters) end))]
    [1 (gen/elements ["🚀" "🎉🎉" "—"])]]))

(def ^:private gen-text
  (gen/let [n (gen/frequency [[3 (gen/choose 1 12)] [2 (gen/choose 1 80)]])
            ws (gen/vector gen-word n)
            gaps (gen/vector (gen/elements [" " " " " " "  " "   "]) (dec n))]
    (str/join (interleave ws (concat gaps [""])))))

(defn- gen-mark
  "A mark over one to four whole words of ws."
  [ws]
  (gen/let [i (gen/choose 0 (dec (count ws)))
            more (gen/choose 0 3)
            kind (gen/elements [:stress :hold :hold :aside :turn :doubt :build :land :voice])
            tenths (gen/choose 0 10)]
    (let [j (min (dec (count ws)) (+ i more))]
      {:span [(first (ws i)) (second (ws j))] :kind kind :amount (/ tenths 10.0)})))

(def ^:private gen-score
  "A normalized score: text of 1 to 80 words, marks on word-aligned spans."
  (gen/let [text gen-text
            marks (gen/vector (gen-mark (pace/words text)) 0 20)]
    (:score (score/normalize {:text text :marks marks}))))

(def ^:private gen-pace (gen/fmap #(hash-map :wpm %) (gen/choose 100 800)))

(def ^:private gen-ms-of
  (gen/fmap (fn [[base per]] (fn [m] (+ base (* per (:amount m)))))
            (gen/tuple (gen/choose 0 3000) (gen/choose 0 3000))))

(defn- end-ms [{:keys [at-ms ms]}] (+ at-ms ms))

(defspec a-reading-is-valid-and-within-the-cap 300
  (prop/for-all [s gen-score
                 p gen-pace
                 ms-of gen-ms-of]
    (let [{:keys [duration-ms beat-ms stops entries] :as r} (pace/reading s p {:ms-of ms-of})]
      (and (schema/valid? :ha/score s)
           (schema/valid? :ha/reading r)
           (= r (pace/reading s p {:ms-of ms-of}))
           (== beat-ms (pace/beat-ms p))
           (<= duration-ms 5000)
           (every? #(<= (end-ms %) 5000) entries)
           (= duration-ms (reduce max 0 (map end-ms (concat stops entries))))))))

(defspec paced-stops-cover-the-words-in-order 300
  (prop/for-all [s gen-score
                 p gen-pace]
    (let [{:keys [stops entries]} (pace/reading s p)]
      (if (seq stops)
        (and (= (pace/words (:text s)) (mapv :span stops))
             (zero? (:at-ms (first stops)))
             (every? (fn [[a b]] (= (end-ms a) (:at-ms b))) (partition 2 1 stops))
             (every? (comp pos? :ms) stops)
             (<= (end-ms (peek stops)) 4000)
             ;; a mark enters as the head reaches the first word it touches
             (every? (fn [{:keys [mark at-ms]}]
                       (let [[from to] (:span ((:marks s) mark))]
                         (= at-ms (:at-ms (first (filter (fn [{[a b] :span}]
                                                           (and (< from b) (< a to)))
                                                         stops))))))
                     entries))
        (and (<= (count entries) 4)
             (= (take (count entries) [0 400 800 1200]) (map :at-ms entries)))))))

(defspec entries-are-in-order 300
  (prop/for-all [s gen-score
                 p gen-pace
                 ms-of gen-ms-of]
    (let [{:keys [entries]} (pace/reading s p {:ms-of ms-of})
          ks (map (juxt :at-ms :mark) entries)]
      (and (= ks (sort ks))
           (apply distinct? -1 (map :mark entries))
           (every? #(< % (count (:marks s))) (map :mark entries))))))

(defspec a-faster-pace-never-gives-a-later-stop 300
  (prop/for-all [s gen-score
                 a (gen/choose 100 800)
                 b (gen/choose 100 800)]
    (let [slow (:stops (pace/reading s {:wpm (min a b)}))
          fast (:stops (pace/reading s {:wpm (max a b)}))]
      (or (empty? slow)
          (and (= (map :span slow) (map :span fast))
               (every? true? (map <= (map :at-ms fast) (map :at-ms slow)))
               (<= (end-ms (peek fast)) (end-ms (peek slow))))))))

(defspec raising-a-hold-never-shortens-its-words-stay 300
  (prop/for-all [[s i] (gen/bind gen-score
                                 (fn [s]
                                   (gen/tuple (gen/return s)
                                              (gen/choose 0 (dec (count (pace/words (:text s))))))))
                 x (gen/choose 0 10)
                 y (gen/choose 0 10)
                 p gen-pace]
    (let [word ((pace/words (:text s)) i)
          ;; the generated score's own holds are left out so that normalize
          ;; cannot drop this one for overlapping a stronger
          others (remove #(= :hold (:kind %)) (:marks s))
          stay (fn [tenths]
                 (let [held (:score (score/normalize
                                     (assoc s :marks (conj (vec (take 15 others))
                                                           {:span word :kind :hold
                                                            :amount (/ tenths 10.0)}))))]
                   (get-in (pace/reading held p) [:stops i :ms])))
          less (stay (min x y))
          more (stay (max x y))]
      (or (nil? less) (nil? more) (<= less more)))))

(defspec the-head-only-moves-forward 300
  (prop/for-all [s gen-score
                 p gen-pace
                 ts (gen/vector (gen/choose -100 5200) 1 12)]
    (let [{:keys [stops] :as r} (pace/reading s p)
          at (map #(pace/head r %) (sort ts))]
      (if (empty? stops)
        (every? nil? at)
        (let [ws (pace/words (:text s))
              start (first (first ws))
              end (second (peek ws))]
          (and (apply <= start (concat at [end]))
               (== start (pace/head r 0))
               (== end (pace/head r (end-ms (peek stops))))
               (every? (fn [{[from _] :span :keys [at-ms]}] (== from (pace/head r at-ms)))
                       stops)))))))

;; --- the cap (a read-back a reader asked for may pass a longer one) ----------

(deftest the-acceptance-sentence-is-paced-at-150-with-a-30-s-cap
  (let [s (score/from-text sentence lavish)
        r (pace/reading s {:wpm 150} {:cap-ms 30000})]
    (is (schema/valid? :ha/reading r))
    (is (= (pace/words sentence) (mapv :span (:stops r))) "every word has its stop")
    (is (= [0 400 1080 1480 2200 2600 3000 3600 4000] (mapv :at-ms (:stops r))))
    (is (= 4600 (end-ms (peek (:stops r)))))
    (is (= (count (:marks s)) (count (:entries r))))
    (is (every? #(= 1500 (:ms %)) (:entries r)) "nothing is hurried")
    (is (= 5500 (:duration-ms r)))))

(deftest the-default-cap-is-5000
  (let [s (score/from-text sentence lavish)]
    (doseq [wpm [150 250]
            opts [{} {:ms-of (constantly 4000)}]]
      (is (= (pace/reading s {:wpm wpm} opts)
             (pace/reading s {:wpm wpm} (assoc opts :cap-ms 5000)))))))

(defspec the-default-cap-is-5000-for-any-score 200
  (prop/for-all [s gen-score
                 p gen-pace
                 ms-of gen-ms-of]
    (= (pace/reading s p {:ms-of ms-of})
       (pace/reading s p {:ms-of ms-of :cap-ms 5000}))))

(def ^:private gen-cap (gen/choose 5000 30000))

(def ^:private gen-long-ms-of
  "Natural lengths long enough to meet a 30 s cap."
  (gen/fmap (fn [[base per]] (fn [m] (+ base (* per (:amount m)))))
            (gen/tuple (gen/choose 0 20000) (gen/choose 0 20000))))

(defspec a-reading-is-valid-and-within-any-cap 300
  (prop/for-all [s gen-score
                 p gen-pace
                 ms-of gen-long-ms-of
                 cap gen-cap]
    (let [opts {:ms-of ms-of :cap-ms cap}
          {:keys [duration-ms beat-ms stops entries] :as r} (pace/reading s p opts)]
      (and (schema/valid? :ha/reading r)
           (= r (pace/reading s p opts))
           (== beat-ms (pace/beat-ms p))
           (<= duration-ms cap)
           (every? #(<= (end-ms %) cap) entries)
           (= duration-ms (reduce max 0 (map end-ms (concat stops entries))))))))

(defspec a-reading-is-paced-when-its-stops-leave-a-second-of-any-cap 300
  (prop/for-all [s gen-score
                 p gen-pace
                 cap gen-cap]
    (let [{:keys [stops entries]} (pace/reading s p {:cap-ms cap})
          ;; a cap no text of 80 words reaches gives the stops themselves
          unbounded (:stops (pace/reading s p {:cap-ms 10000000}))
          crossing (end-ms (peek unbounded))]
      (if (<= crossing (- cap 1000))
        (and (= unbounded stops)
             (= (pace/words (:text s)) (mapv :span stops))
             (zero? (:at-ms (first stops)))
             (every? (fn [[a b]] (= (end-ms a) (:at-ms b))) (partition 2 1 stops))
             (every? (fn [{:keys [mark at-ms]}]
                       (let [[from to] (:span ((:marks s) mark))]
                         (= at-ms (:at-ms (first (filter (fn [{[a b] :span}]
                                                           (and (< from b) (< a to)))
                                                         stops))))))
                     entries))
        (and (empty? stops)
             (<= (count entries) 4)
             (= (take (count entries) [0 400 800 1200]) (map :at-ms entries)))))))

(defspec entries-are-in-order-and-never-squeezed-too-far-under-any-cap 300
  (prop/for-all [s gen-score
                 p gen-pace
                 ms-of gen-long-ms-of
                 cap gen-cap]
    (let [{:keys [stops entries]} (pace/reading s p {:ms-of ms-of :cap-ms cap})
          ks (map (juxt :at-ms :mark) entries)
          natural #(Math/round (double (ms-of ((:marks s) (:mark %)))))]
      (and (= ks (sort ks))
           (apply distinct? -1 (map :mark entries))
           (every? #(< % (count (:marks s))) (map :mark entries))
           (every? #(<= (:ms %) (natural %)) entries)
           ;; a paced mark is given its natural length, or what the cap
           ;; leaves when that is at least 0.4 of it
           (or (empty? stops)
               (every? #(and (= (:ms %) (min (natural %) (- cap (:at-ms %))))
                             (<= (* 0.4 (natural %)) (:ms %)))
                       entries))))))

(defspec the-head-only-moves-forward-under-any-cap 300
  (prop/for-all [s gen-score
                 p gen-pace
                 cap gen-cap
                 ts (gen/vector (gen/choose -100 31000) 1 12)]
    (let [{:keys [stops] :as r} (pace/reading s p {:cap-ms cap})
          at (map #(pace/head r %) (sort ts))]
      (if (empty? stops)
        (every? nil? at)
        (let [ws (pace/words (:text s))
              start (first (first ws))
              end (second (peek ws))]
          (and (apply <= start (concat at [end]))
               (== start (pace/head r 0))
               (== end (pace/head r (end-ms (peek stops))))
               (every? (fn [{[from _] :span :keys [at-ms]}] (== from (pace/head r at-ms)))
                       stops)))))))
