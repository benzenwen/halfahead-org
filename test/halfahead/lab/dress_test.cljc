(ns halfahead.lab.dress-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [halfahead.lab.dress :as dress]
            [halfahead.lab.marks :as marks]
            [halfahead.lab.style :as style]
            [halfahead.schema :as schema]))

;; --- helpers ----------------------------------------------------------------

(defn- glyphs-of
  "One glyph per string of chars (an emoji is one glyph of two units), with
  ids that are not their indexes: 100, 103, 106..."
  [chars]
  (vec (map-indexed (fn [i c] {:id (+ 100 (* 3 i)) :char (str c) :born-ms 0}) chars)))

(defn- id-at [i] (+ 100 (* 3 i)))

(defn- close? [a b] (< (abs (- a b)) 1e-9))

(def ^:private all-dress [:versal :rubric :gilt :strong :whisper])

;; "🚀 So *gold* (hm)": the rocket is one glyph and two units, so every span
;; below starts one unit later than its first glyph's index.
(def ^:private glyphs
  (glyphs-of ["🚀" " " "S" "o" " " "*" "g" "o" "l" "d" "*" " " "(" "h" "m" ")"]))

(def ^:private pose
  {:id 7 :char "g" :ghost? false :x 2.0 :y -1.35 :z 0.0 :rx 0.0 :ry 0.0 :rz 0.0
   :scale 1.0 :opacity 1.0 :glow 0.0 :wobble 0.0})

;; --- looks -------------------------------------------------------------------

(deftest the-five-looks
  (is (= {:versal  {:size 1.8}
          :rubric  {:ink :signal}
          :gilt    {:ink :gold :glint true}
          :strong  {:weight 0.55}
          :whisper {:size 0.85 :ink :muted :weight -0.2}}
         dress/looks))
  (is (= (set all-dress) (set (keys dress/looks))) "one for every :ha/dress")
  (is (every? #(schema/valid? :ha/ink %) (keep :ink (vals dress/looks)))))

;; --- placed ------------------------------------------------------------------

(deftest each-dress-is-placed-on-the-glyphs-of-its-span
  (doseq [as all-dress]
    (testing (name as)
      (is (= [{:as as :glyph-ids (mapv id-at (range 6 10))}]
             (dress/placed [{:span [7 11] :as as}] glyphs))
          "'gold' is units 7 to 11 and glyphs 6 to 9: the rocket before it is two units"))))

(deftest plain-wears-gilt-and-a-whisper
  (let [text "This is *the one thing* that matters (trust me)."
        worn (dress/placed (marks/dress-for (style/point :plain) text (marks/read text))
                           (glyphs-of text))]
    (is (= [{:as :gilt :glyph-ids (mapv id-at (range 9 22))}
            {:as :whisper :glyph-ids (mapv id-at (range 38 46))}]
           worn))
    (is (= ["the one thing" "trust me"]
           (mapv #(subs text (/ (- (first (:glyph-ids %)) 100) 3)
                        (inc (/ (- (peek (:glyph-ids %)) 100) 3)))
                 worn)))))

(deftest placed-keeps-order-and-drops-what-holds-no-glyph
  (is (= [{:as :versal :glyph-ids [(id-at 2)]}
          {:as :gilt :glyph-ids (mapv id-at (range 6 10))}
          {:as :whisper :glyph-ids (mapv id-at (range 13 15))}]
         (dress/placed [{:span [3 4] :as :versal}
                        {:span [7 11] :as :gilt}
                        {:span [40 44] :as :strong}
                        {:span [14 16] :as :whisper}]
                       glyphs))
      "the span past the end is left out")
  (is (= [] (dress/placed [{:span [1 2] :as :rubric}] glyphs))
      "half of the rocket is no glyph")
  (is (= [{:as :rubric :glyph-ids [(id-at 0)]}] (dress/placed [{:span [0 2] :as :rubric}] glyphs)))
  (is (= [] (dress/placed [{:span [0 4] :as :gilt}] [])))
  (is (= [] (dress/placed [] glyphs)))
  (is (= [] (dress/placed nil glyphs))))

;; --- seen --------------------------------------------------------------------

(deftest seen-keeps-adds-and-drops
  (let [gilt {:as :gilt :glyph-ids [118 121 124 127]}
        whisper {:as :whisper :glyph-ids [139 142]}
        strong {:as :strong :glyph-ids [118 121]}]
    (is (= {} (dress/seen nil [] 500)))
    (is (= {[:gilt 118] 500} (dress/seen nil [gilt] 500)) "new: seen now")
    (is (= {[:gilt 118] 500 [:whisper 139] 900}
           (dress/seen {[:gilt 118] 500} [gilt whisper] 900))
        "the earlier time is kept, the new dress added")
    (is (= {[:whisper 139] 900}
           (dress/seen {[:gilt 118] 500 [:whisper 139] 900} [whisper] 1500))
        "dress that is gone is dropped")
    (is (= {[:gilt 118] 2000} (dress/seen (dress/seen {[:gilt 118] 500} [] 1000) [gilt] 2000))
        "and is new again if it comes back")
    (is (= {[:gilt 118] 500 [:strong 118] 700}
           (dress/seen {[:gilt 118] 500} [gilt strong] 700))
        "two dress beginning on one glyph are told apart by what they are")
    (is (= {[:gilt 118] 500} (dress/seen {[:gilt 118] 500} [(update gilt :glyph-ids conj 130)] 800))
        "a dress that grew at its end is the same dress")))

;; --- sizes -------------------------------------------------------------------

(deftest sizes-are-for-the-dress-that-has-one
  (let [ids (mapv id-at (range 6 10))
        placed-as (fn [as] (dress/placed [{:span [7 11] :as as}] glyphs))]
    (is (= (zipmap ids (repeat 1.8)) (dress/sizes (placed-as :versal) glyphs)))
    (is (= (zipmap ids (repeat 0.85)) (dress/sizes (placed-as :whisper) glyphs)))
    (doseq [as [:rubric :gilt :strong]]
      (is (= {} (dress/sizes (placed-as as) glyphs)) (name as)))
    (is (= {} (dress/sizes [] glyphs)))))

(deftest a-glyph-in-two-sized-dress-takes-the-larger-change
  (let [both [{:as :whisper :glyph-ids [100 103]} {:as :versal :glyph-ids [100]}]]
    (is (= {100 1.8 103 0.85} (dress/sizes both (glyphs-of ["a" "b"]))))
    (is (= {100 1.8 103 0.85} (dress/sizes (vec (reverse both)) (glyphs-of ["a" "b"])))
        "whichever comes first"))
  (is (= {100 1.8} (dress/sizes [{:as :versal :glyph-ids [100 999]}] (glyphs-of ["a"])))
      "a glyph that is not in the draft has no size"))

;; --- at-rest -----------------------------------------------------------------

(deftest each-look-at-rest
  (testing "at weight 0 a look changes nothing that is drawn"
    (doseq [as all-dress
            :let [p (dress/at-rest pose (dress/looks as) 0.0 nil)]]
      (is (= pose (dissoc p :ink :tint)) (name as))
      (is (zero? (:tint p 0.0)) (name as))))
  (testing "a versal is only a size, which the layout owns"
    (doseq [w [0.0 0.5 1.0]]
      (is (= pose (dress/at-rest pose (:versal dress/looks) w {:x 2.0 :y -1.35})))))
  (testing "rubric is the signal ink"
    (doseq [w [0.0 0.5 1.0]]
      (is (= (assoc pose :ink :signal :tint w) (dress/at-rest pose (:rubric dress/looks) w nil)))))
  (testing "gilt is gold, and glows a little without any light"
    (doseq [w [0.0 0.5 1.0]
            :let [p (dress/at-rest pose (:gilt dress/looks) w nil)]]
      (is (= (assoc pose :ink :gold :tint w) (assoc p :glow 0.0)))
      (is (close? (* 0.12 w) (:glow p)))))
  (testing "strong is heavier"
    (is (= pose (dress/at-rest pose (:strong dress/looks) 0.0 nil)) "no :weight at rest")
    (is (close? 0.275 (:weight (dress/at-rest pose (:strong dress/looks) 0.5 nil))))
    (is (= (assoc pose :weight 0.55) (dress/at-rest pose (:strong dress/looks) 1.0 nil))))
  (testing "a whisper is muted and lighter"
    (is (= (assoc pose :ink :muted :tint 0.0) (dress/at-rest pose (:whisper dress/looks) 0.0 nil)))
    (let [p (dress/at-rest pose (:whisper dress/looks) 0.5 nil)]
      (is (= (assoc pose :ink :muted :tint 0.5) (dissoc p :weight)))
      (is (close? -0.1 (:weight p))))
    (is (= (assoc pose :ink :muted :tint 1.0 :weight -0.2)
           (dress/at-rest pose (:whisper dress/looks) 1.0 nil))))
  (testing "every dressed pose is still a pose"
    (doseq [as all-dress, w [0.0 0.5 1.0], light [nil {:x 2.0 :y -1.35}]]
      (is (schema/valid? :ha/glyph-pose (dress/at-rest pose (dress/looks as) w light))))))

(deftest looks-stack
  (let [gilt-strong (-> pose
                        (dress/at-rest (:gilt dress/looks) 1.0 nil)
                        (dress/at-rest (:strong dress/looks) 1.0 nil))]
    (is (= :gold (:ink gilt-strong)))
    (is (close? 0.55 (:weight gilt-strong))))
  (is (close? 0.35 (:weight (-> pose
                                (dress/at-rest (:strong dress/looks) 1.0 nil)
                                (dress/at-rest (:whisper dress/looks) 1.0 nil))))
      "weights add")
  (is (== 1.0 (:weight (dress/at-rest (assoc pose :weight 0.9) (:strong dress/looks) 1.0 nil)))
      "and stay in range")
  (is (== 1.0 (:glow (dress/at-rest (assoc pose :glow 0.9) (:gilt dress/looks) 1.0 {:x 2.0 :y -1.35})))))

(defn- glint
  "The glow of the gilt pose at weight w with a light d em away from it."
  [w d]
  (:glow (dress/at-rest pose (:gilt dress/looks) w {:x (+ 2.0 d) :y -1.35})))

(deftest the-glint-falls-with-distance
  (is (close? 0.62 (glint 1.0 0.0)) "0.12 + 0.5 with the light on the letter")
  (is (close? (+ 0.12 (* 0.5 (Math/exp (/ -1.0 2.88)))) (glint 1.0 1.0)))
  (is (close? (glint 1.0 1.5) (glint 1.0 -1.5)) "the same on either side")
  (is (close? (glint 1.0 1.0)
              (:glow (dress/at-rest pose (:gilt dress/looks) 1.0 {:x 2.0 :y -0.35})))
      "and above as beside")
  (is (apply > (map #(glint 1.0 %) [0.0 0.3 0.6 1.0 1.5 2.0 3.0 4.0])) "strictly falling")
  (is (< 0.12 (glint 1.0 4.0) 0.125) "four em away it is the gold's own 0.12 again")
  (is (close? 0.12 (glint 1.0 40.0)))
  (is (close? 0.31 (glint 0.5 0.0)) "scaled by the weight")
  (is (zero? (glint 0.0 0.0)))
  (testing "only gold catches the light"
    (doseq [as [:versal :rubric :strong :whisper]]
      (is (zero? (:glow (dress/at-rest pose (dress/looks as) 1.0 {:x 2.0 :y -1.35}))) (name as)))))

;; --- property ----------------------------------------------------------------

(def ^:private gen-chars
  (gen/vector (gen/elements ["a" "b" "W" " " "😀" "🚀" "\n" "é" "*"]) 0 30))

(def ^:private gen-dress
  (gen/vector (gen/fmap (fn [[from len as]] {:span [from (+ from len)] :as as})
                        (gen/tuple (gen/choose 0 40) (gen/choose 1 12) (gen/elements all-dress)))
              0 8))

(defn- in-order? [ids order]
  (apply < (map order ids)))

(defspec placed-dress-lies-on-the-glyphs 300
  (prop/for-all [chars gen-chars
                 dress gen-dress
                 t (gen/choose 0 5000)]
    (let [glyphs (glyphs-of chars)
          order (into {} (map-indexed (fn [i g] [(:id g) i])) glyphs)
          placed (dress/placed dress glyphs)
          sizes (dress/sizes placed glyphs)
          seen (dress/seen {} placed t)]
      (and (every? #(schema/valid? :ha/dress %) dress)
           (<= (count placed) (count dress))
           (every? (fn [{:keys [as glyph-ids]}]
                     (and (dress/looks as)
                          (seq glyph-ids)
                          (every? order glyph-ids)
                          (in-order? glyph-ids order)
                          ;; consecutive glyphs: a span is one run of the text
                          (= (count glyph-ids)
                             (inc (- (order (peek glyph-ids)) (order (first glyph-ids)))))))
                   placed)
           (every? (set (mapcat :glyph-ids placed)) (keys sizes))
           (every? #{1.8 0.85} (vals sizes))
           (= (set (keys seen)) (set (map (fn [{:keys [as glyph-ids]}] [as (first glyph-ids)]) placed)))
           (every? #(= t %) (vals seen))))))
