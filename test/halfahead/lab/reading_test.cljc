(ns halfahead.lab.reading-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [halfahead.lab.pace :as pace]
            [halfahead.lab.pose :as pose]
            [halfahead.lab.reading :as reading]
            [halfahead.lab.style :as style]
            [halfahead.schema :as schema]))

;; --- helpers ----------------------------------------------------------------

;; order 0044's acceptance sentence
(def ^:private sentence "I loooove this (honestly). But maybe not. Ship it!")

(def ^:private plain (style/point :plain))
(def ^:private elegant (style/point :elegant))

;; what lab.pose plays each with
(def ^:private plainly (style/params :plain))
(def ^:private elegantly (style/params :elegant))

(defn- glyphs-of
  "One glyph per character of chars (strings, so an emoji may be one glyph
  of two units), with ids that are not their indexes: 100, 103, 106..."
  [chars]
  (vec (map-indexed (fn [i c] {:id (+ 100 (* 3 i)) :char (str c) :born-ms 0}) chars)))

(defn- id-at [i] (+ 100 (* 3 i)))

(defn- close-to? [a b] (< (Math/abs (- (double a) (double b))) 1e-9))

(defn- in-order
  "The fills of glyphs, in glyph order."
  [fills glyphs]
  (mapv #(get fills (:id %)) glyphs))

(defn- a-lit-prefix-one-fraction-a-dim-suffix? [vs]
  (and (every? #(<= 0.0 % 1.0) vs)
       (apply >= 1.0 vs)
       (<= (count (filter #(< 0.0 % 1.0) vs)) 1)))

;; --- natural-ms --------------------------------------------------------------

(deftest a-gesture-lasts-its-patch-times-the-styles-tempo
  (is (== 3120 (reading/natural-ms plainly 7 :swell)) "2400 ms and a stagger over 7 letters")
  (is (== 4680 (reading/natural-ms elegantly 7 :swell))
      "Elegant plays at its params' tempo, 1.5, not its point's 1.36")
  (is (== 4244 (reading/natural-ms (style/from-point elegant) 7 :swell))
      "a point's own tempo, 4243.2 ms, rounded up so the grant covers it")
  (is (< (reading/natural-ms plainly 1 :swell) (reading/natural-ms plainly 7 :swell))
      "one letter has no stagger")
  (is (< (reading/natural-ms (style/from-point (assoc plain :energy 1.0)) 7 :swell)
         (reading/natural-ms plainly 7 :swell))
      "an energetic style is quicker"))

;; --- plan --------------------------------------------------------------------

(deftest plain-performs-no-mark-of-the-sentence
  (let [{:keys [score performing] r :reading} (reading/plan sentence plain plainly {:wpm 250})]
    (is (schema/valid? :ha/score score))
    (is (schema/valid? :ha/reading r))
    (is (= sentence (:text score)))
    (is (= [] (:marks score)) "flourish 0 passes over every mark")
    (is (= [] performing))
    (is (= [] (:entries r)))
    (is (= (pace/words sentence) (mapv :span (:stops r))) "paced: all nine words")
    (is (= {:span [2 9] :at-ms 240 :ms 240} (get-in r [:stops 1]))
        "a hold that is not performed does not hold the head")
    (is (= 2592 (:duration-ms r)))))

(deftest elegant-performs-four-marks-of-the-sentence
  (let [{:keys [score performing] r :reading} (reading/plan sentence elegant elegantly {:wpm 250})]
    (is (schema/valid? :ha/score score))
    (is (schema/valid? :ha/reading r))
    (is (= [[[2 9] :hold] [[16 24] :aside] [[42 49] :land] [[47 49] :stress]]
           (mapv (juxt :span :kind) (:marks score)))
        "the turn and the doubt (0.5) are below Elegant's threshold")
    (is (= [{:mark 0 :effect :swell :intensity 0.7}
            {:mark 1 :effect :hush :intensity 0.6}
            {:mark 2 :effect :nod :intensity 0.7}
            {:mark 3 :effect :glow :intensity 0.6}]
           performing))
    (is (= [{:mark 0 :at-ms 240 :ms 4680}
            {:mark 1 :at-ms 888 :ms 2250}
            {:mark 2 :at-ms 2160 :ms 1200}
            {:mark 3 :at-ms 2400 :ms 1538}]
           (:entries r))
        "each as the head reaches it, for its natural length at Elegant's tempo, 1.5")
    (is (= {:span [2 9] :at-ms 240 :ms 408} (get-in r [:stops 1])) "held: 1.7 beats")
    (is (= 4920 (:duration-ms r)))
    (is (== 240 (:beat-ms r)))))

(deftest a-slow-reader-is-paced-too
  (let [r (:reading (reading/plan sentence elegant elegantly {:wpm 150}))]
    (is (= 9 (count (:stops r))) "the read-back's cap is 30 s, not 5")
    (is (< 5000 (:duration-ms r)))))

(deftest text-that-is-not-a-message-has-no-plan
  (is (nil? (reading/plan "" elegant elegantly {:wpm 250})))
  (is (nil? (reading/plan nil elegant elegantly {:wpm 250})))
  (is (nil? (reading/plan (apply str (repeat 4001 "a")) elegant elegantly {:wpm 250}))))

;; --- cues --------------------------------------------------------------------

(deftest cues-are-placed-on-the-glyphs-of-each-mark
  (let [glyphs (glyphs-of sentence)
        cues (reading/cues (reading/plan sentence elegant elegantly {:wpm 250}) glyphs 1000)]
    (is (every? #(schema/valid? :ha/placed-cue %) cues))
    (is (= [{:glyph-ids (mapv id-at (range 2 9))
             :cue {:word "loooove" :effect :swell :intensity 0.7 :source :score}
             :at-ms 1240}
            {:glyph-ids (mapv id-at (range 16 24))
             :cue {:word "honestly" :effect :hush :intensity 0.6 :source :score}
             :at-ms 1888}
            {:glyph-ids (mapv id-at (range 42 49))
             :cue {:word "Ship it" :effect :nod :intensity 0.7 :source :score}
             :at-ms 3160}
            {:glyph-ids (mapv id-at (range 47 49))
             :cue {:word "it" :effect :glow :intensity 0.6 :source :score}
             :at-ms 3400}]
           cues)
        "unhurried, so none has a :tempo")
    (is (= [] (reading/cues (reading/plan sentence plain plainly {:wpm 250}) glyphs 1000)))
    (is (= [] (reading/cues (reading/plan sentence elegant elegantly {:wpm 250}) [] 1000))
        "a mark with no glyph is no cue")))

(def ^:private long-text
  "66 words whose last is held: at 150 wpm the head reaches it 26.4 s in."
  (str (str/join " " (repeat 65 "plain")) " loooove"))

(deftest a-late-swell-is-hurried
  (let [glyphs (glyphs-of long-text)
        {r :reading :as plan} (reading/plan long-text elegant elegantly {:wpm 150})
        [{:keys [at-ms ms]} :as entries] (:entries r)
        [cue :as cues] (reading/cues plan glyphs 500)]
    (is (= 66 (count (:stops r))))
    (is (= 1 (count entries) (count cues)))
    (is (= 26000 at-ms))
    (is (= 4000 ms) "what is left of the 30 s")
    (is (schema/valid? :ha/placed-cue cue))
    (is (= :swell (get-in cue [:cue :effect])))
    (is (= 26500 (:at-ms cue)))
    (is (= (/ 4000 4680.0) (:tempo cue)))
    (is (< 0.4 (:tempo cue) 1))
    (is (= (mapv id-at (range 390 397)) (:glyph-ids cue)))))

(deftest a-cues-word-is-at-most-64-units
  (let [text (str "(" (apply str (repeat 70 "a")) ") ok")
        ;; lavish, so the aside performs whatever its amount
        plan (reading/plan text (assoc plain :flourish 1.0) plainly {:wpm 250})
        cues (reading/cues plan (glyphs-of text) 0)
        aside (first (filter #(= 70 (count (:glyph-ids %))) cues))]
    (is (some? aside))
    (is (every? #(schema/valid? :ha/placed-cue %) cues))
    (is (= (apply str (repeat 64 "a")) (get-in aside [:cue :word])))))

;; --- fills -------------------------------------------------------------------

(deftest ink-fills-as-the-head-passes
  (let [glyphs (glyphs-of sentence)
        plan (reading/plan sentence elegant elegantly {:wpm 250})
        fills #(reading/fills plan glyphs 1000 %)]
    (testing "none before the reading or once the head has crossed"
      (is (nil? (fills 999)))
      (is (nil? (fills 3760)) "the stops end 2760 ms in")
      (is (nil? (fills 60000))))
    (testing "at its start everything but nothing is lit"
      (is (= (set (map :id glyphs)) (set (keys (fills 1000)))))
      (is (every? zero? (vals (fills 1000)))))
    (testing "half way through 'loooove' (240 + 204 ms in, 3.5 letters)"
      (let [vs (in-order (fills 1444) glyphs)]
        (is (= [1.0 1.0 1.0 1.0 1.0] (subvec vs 0 5)) "'I', the space and 'loo'")
        (is (close-to? 0.5 (vs 5)))
        (is (every? zero? (subvec vs 6)))))
    (testing "between words the space is lit and the next word is not"
      (let [vs (in-order (fills 1648) glyphs)]
        (is (every? #(== 1 %) (subvec vs 0 10)))
        (is (every? zero? (subvec vs 10)))))
    (testing "the boundary is the head"
      (doseq [t (range 1000 3760 37)]
        (let [vs (in-order (fills t) glyphs)]
          (is (a-lit-prefix-one-fraction-a-dim-suffix? vs))
          (is (close-to? (pace/head (:reading plan) (- t 1000)) (reduce + vs))
              "one unit per glyph here, so the lit length is the head"))))
    (testing "just before the end only the last letter is still filling"
      (let [vs (in-order (fills 3759) glyphs)]
        (is (every? #(== 1 %) (pop vs)))
        (is (< 0.9 (peek vs) 1.0))))))

(deftest highlights-are-not-dimmed
  (let [text (str/join " " (repeat 200 "plain"))
        plan (reading/plan text elegant elegantly {:wpm 250})]
    (is (= [] (:stops (:reading plan))) "too long to pace even in 30 s")
    (is (nil? (reading/fills plan (glyphs-of text) 0 0)))
    (is (nil? (reading/fills plan (glyphs-of text) 0 100)))))

(deftest offsets-count-utf-16-units
  (let [text "I 🚀 loooove it"
        glyphs (glyphs-of ["I" " " "🚀" " " "l" "o" "o" "o" "o" "v" "e" " " "i" "t"])
        plan (reading/plan text elegant elegantly {:wpm 250})
        [swell :as cues] (reading/cues plan glyphs 0)]
    (is (= [[0 1] [2 4] [5 12] [13 15]] (mapv :span (:stops (:reading plan)))))
    (is (= 1 (count cues)))
    (is (= "loooove" (get-in swell [:cue :word])))
    (is (= (mapv id-at (range 4 11)) (:glyph-ids swell))
        "the rocket is one glyph and two units")
    (testing "half way through the rocket's stop"
      (let [vs (in-order (reading/fills plan glyphs 0 360) glyphs)]
        (is (= [1.0 1.0] (subvec vs 0 2)))
        (is (close-to? 0.5 (vs 2)))
        (is (every? zero? (subvec vs 3)))))
    (testing "as the head reaches 'loooove' the rocket and its space are lit"
      (let [vs (in-order (reading/fills plan glyphs 0 480) glyphs)]
        (is (= [1.0 1.0 1.0 1.0] (subvec vs 0 4)))
        (is (every? zero? (subvec vs 4)))))))

(def ^:private gen-text
  (gen/fmap #(str/join " " %)
            (gen/vector (gen/elements ["I" "loooove" "this" "(honestly)." "But" "maybe" "not."
                                       "Ship" "it!" "🚀" "sooo" "WOW" "really?" "…"])
                        1 40)))

(defn- split-glyphs
  "text as the draft splits it: one glyph per unit, a rocket whole."
  [text]
  (glyphs-of (re-seq #"🚀|[^🚀]" text)))

(defspec the-ink-is-a-lit-prefix-one-fraction-and-a-dim-suffix 200
  (prop/for-all [text gen-text
                 wpm (gen/choose 150 450)
                 style (gen/elements style/styles)
                 share (gen/choose 0 999)]
    (let [glyphs (split-glyphs text)
          {r :reading :as plan} (reading/plan text (style/point style) (style/params style) {:wpm wpm})
          total (let [{:keys [at-ms ms]} (peek (:stops r))] (+ at-ms ms))
          t (+ 7000 (* total (/ share 1000.0)))
          fills (reading/fills plan glyphs 7000 t)
          vs (in-order fills glyphs)
          lengths (map (comp count :char) glyphs)]
      (and (= text (apply str (map :char glyphs)))
           (seq (:stops r))
           (= (count glyphs) (count fills))
           (a-lit-prefix-one-fraction-a-dim-suffix? vs)
           ;; the lit length, in units, is where the head is
           (close-to? (pace/head r (- t 7000)) (reduce + (map * vs lengths)))
           (nil? (reading/fills plan glyphs 7000 (+ 7000 total)))
           (nil? (reading/fills plan glyphs 7000 6999))
           (every? #(schema/valid? :ha/placed-cue %) (reading/cues plan glyphs 7000))))))

;; --- over? -------------------------------------------------------------------

(deftest a-reading-is-over-when-its-stops-and-cues-are
  (let [glyphs (glyphs-of sentence)
        in-elegant (reading/plan sentence elegant elegantly {:wpm 250})
        in-plain (reading/plan sentence plain plainly {:wpm 250})]
    (is (not (reading/over? in-elegant glyphs 1000 1000)))
    (is (not (reading/over? in-elegant glyphs 1000 3760)) "the head has crossed; the swell plays on")
    (is (not (reading/over? in-elegant glyphs 1000 5919)))
    (is (reading/over? in-elegant glyphs 1000 5920) "240 + 4680 ms in")
    (is (not (reading/over? in-plain glyphs 1000 3591)))
    (is (reading/over? in-plain glyphs 1000 3592) "no cues: when the stops end")
    (is (reading/over? (reading/plan (str/join " " (repeat 200 "plain")) plain plainly {:wpm 250}) [] 0 0)
        "highlights with nothing to perform are over at once")))

;; --- the tempo in force (a fix to order 0050) ---------------------------------

(defn- frame-of
  "The frame at t of the sentence's glyphs, long since born, with cues, in
  a named style on the full stage."
  [glyphs cues style t]
  (pose/frame {:text sentence :glyphs glyphs :ghosts [] :cues (vec cues) :next-id 1000}
              t {:style-choice style}))

(deftest cues-end-within-the-reading
  (doseq [named style/styles
          wpm [150 250 400]]
    (testing (str named " at " wpm " wpm")
      (let [glyphs (glyphs-of sentence)
            t0 20000
            {r :reading :as plan} (reading/plan sentence (style/point named) (style/params named)
                                                {:wpm wpm})
            cues (reading/cues plan glyphs t0)
            end (+ t0 (reduce max
                              (let [{:keys [at-ms ms]} (peek (:stops r))] (+ at-ms ms))
                              (map #(+ (:at-ms %) (:ms %)) (:entries r))))
            still #(frame-of glyphs [] named %)]
        (is (= (count (:entries r)) (count cues)) "every mark performed has its glyphs")
        (when (#{:elegant :caffeinated :drunken :coarse} named)
          (is (seq cues) "a style with flourish performs something"))
        (is (not (reading/over? plan glyphs t0 (dec end))))
        (is (reading/over? plan glyphs t0 end))
        (is (= (still end) (frame-of glyphs cues named end))
            "when the reading is over and its cues are withdrawn, every letter is at rest")
        (doseq [[{:keys [ms]} {:keys [at-ms] :as cue}] (map vector (:entries r) cues)]
          (is (<= (+ at-ms ms) end) "the cue's grant ends within the reading")
          (is (= (still (+ at-ms ms)) (frame-of glyphs [cue] named (+ at-ms ms)))
              (str (get-in cue [:cue :effect]) " has ended, by lab.pose's own timing, when its grant has"))
          (is (some #(not= (still %) (frame-of glyphs [cue] named %))
                    (map #(+ at-ms (* ms %)) [0.25 0.5 0.75]))
              (str (get-in cue [:cue :effect]) " moves a letter while it plays")))))))
