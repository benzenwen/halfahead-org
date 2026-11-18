(ns halfahead.lab.board-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [halfahead.lab.board :as board]
            [halfahead.lab.marks :as marks]
            [halfahead.lab.style :as style]
            [halfahead.schema :as schema]))

;; --- helpers ----------------------------------------------------------------

(defn- glyphs-of
  "One glyph per character of chars (strings, so an emoji may be one glyph
  of two units), with ids that are not their indexes: 100, 103, 106..."
  [chars]
  (vec (map-indexed (fn [i c] {:id (+ 100 (* 3 i)) :char c :born-ms 0}) chars)))

(defn- glyphs-for [text] (glyphs-of (map str text)))

(defn- targeted
  "The text a pad would act on."
  ([text] (targeted text nil))
  ([text selection]
   (when-let [[from to] (board/target text selection)]
     (subs text from to))))

(def ^:private plain (style/point :plain))
(def ^:private caffeinated (style/point :caffeinated))
(def ^:private mark-pads [:stress :hold :aside :turn :doubt :land])

;; --- pads -------------------------------------------------------------------

(deftest the-pads-are-the-designs
  (is (= [{:id :stress :label "STRESS" :key "1"}
          {:id :hold :label "HOLD" :key "2"}
          {:id :aside :label "ASIDE" :key "3"}
          {:id :turn :label "TURN" :key "4"}
          {:id :doubt :label "DOUBT" :key "5"}
          {:id :land :label "LAND" :key "6"}
          {:id :boom :label "BOOM" :key "7"}]
         board/pads))
  (doseq [{:keys [id]} (butlast board/pads)]
    (is (schema/valid? :ha/mark-kind id) (str id))))

;; --- target -----------------------------------------------------------------

(deftest target-of-a-selection
  (is (= [3 6] (board/target "we did it" {:from 3 :to 6})))
  (is (= "did" (targeted "we did it" {:from 3 :to 6})))
  (testing "trimmed of whitespace at both ends"
    (is (= [3 6] (board/target "we did it" {:from 2 :to 7}))))
  (testing "given backwards, it is the same span"
    (is (= [3 6] (board/target "we did it" {:from 6 :to 3}))))
  (testing "an empty selection is a caret: the last phrase"
    (is (= "we did it" (targeted "we did it" {:from 4 :to 4}))))
  (testing "a selection of nothing but spaces marks nothing of its own"
    (is (= "we did  it" (targeted "we did  it" {:from 6 :to 8}))))
  (testing "a selection past the text is kept inside it"
    (is (= [3 9] (board/target "we did it" {:from 3 :to 99})))))

(deftest target-of-the-last-phrase
  (testing "text ending mid-sentence"
    (is (= [0 9] (board/target "we did it" nil)))
    (is (= "we did it" (targeted "Start here. we did it"))))
  (testing "after a full stop, the sentence it ended"
    (is (= "we did it." (targeted "we did it.")))
    (is (= "we did it." (targeted "Start here. we did it.")))
    (is (= [12 22] (board/target "Start here. we did it." nil))))
  (testing "after ! ? and …"
    (is (= "we did it" (targeted "Wow! we did it")))
    (is (= "we did it" (targeted "Really? we did it")))
    (is (= "we did it" (targeted "So… we did it"))))
  (testing "after a newline"
    (is (= "we did it" (targeted "first line\nwe did it")))
    (is (= "we did it" (targeted "first line\n  we did it"))))
  (testing "with trailing spaces"
    (is (= [0 9] (board/target "we did it   " nil)))
    (is (= "we did it." (targeted "we did it. \n")))
    (is (= "we did it" (targeted "Start here.   we did it \t")))))

(deftest a-stop-inside-a-word-is-no-sentence-end
  (is (= "it cost 3.5 million" (targeted "it cost 3.5 million")))
  (is (= "see halfahead.org today" (targeted "see halfahead.org today"))))

(deftest target-is-at-most-twelve-words
  (let [text "one two three four five six seven eight nine ten eleven twelve thirteen fourteen"]
    (is (= "three four five six seven eight nine ten eleven twelve thirteen fourteen"
           (targeted text)))
    (is (= 12 (count (str/split (targeted text) #" ")))))
  (testing "twelve words are taken whole"
    (let [text "a b c d e f g h i j k l"]
      (is (= text (targeted text)))))
  (testing "a selection is not cut"
    (let [text "one two three four five six seven eight nine ten eleven twelve thirteen fourteen"]
      (is (= text (targeted text {:from 0 :to (count text)}))))))

(deftest nothing-to-mark
  (is (nil? (board/target "" nil)))
  (is (nil? (board/target "" {:from 0 :to 0})))
  (is (nil? (board/target "   \n " nil)))
  (is (nil? (board/target "  " {:from 0 :to 2}))))

(deftest target-counts-utf-16-units
  ;; 😀 is two units
  (is (= [4 13] (board/target "😀! we did it" nil)))
  (is (= "😀 did it" (targeted "ok. 😀 did it"))))

(defspec target-is-a-trimmed-span-of-the-text 300
  (prop/for-all [text (gen/fmap str/join
                                (gen/vector (gen/elements ["a" "b" " " " " "." "!" "\n" "…" "😀" "?"])
                                            0 60))
                 a (gen/choose 0 70)
                 b (gen/choose 0 70)]
    (let [span (board/target text {:from a :to b})]
      (if (str/blank? text)
        (nil? span)
        (let [[from to] span
              s (subs text from to)]
          (and (schema/valid? :ha/span span)
               (<= to (count text))
               (= s (str/trim s))
               (not (str/blank? s))))))))

;; --- press ------------------------------------------------------------------

(deftest a-mark-pad-places-a-board-cue
  (let [glyphs (glyphs-for "we did it")]
    (doseq [point [plain caffeinated]
            pad mark-pads]
      (testing (str pad " at " point)
        (let [{:keys [cues] :as added} (board/press pad point glyphs [0 9] 2000 0.7)
              [{:keys [glyph-ids cue at-ms]}] cues]
          (is (= [:cues] (keys added)))
          (is (= 1 (count cues)))
          (is (schema/valid? :ha/placed-cue (first cues)))
          (is (= (mapv :id glyphs) glyph-ids))
          (is (= 2000 at-ms))
          (is (= {:word "we did it" :source :board :intensity 0.7}
                 (select-keys cue [:word :source :intensity])))
          (is (= (:effect (marks/gesture-for point {:span [0 9] :kind pad :amount 0.7}))
                 (:effect cue))
              "the gesture the style performs that mark with"))))))

(deftest the-gestures-at-plain-and-caffeinated
  (let [glyphs (glyphs-for "we did it")
        effect (fn [point pad] (-> (board/press pad point glyphs [0 9] 0 0.7) :cues first :cue :effect))]
    (is (= {:stress :glow :hold :swell :aside :hush :turn :nod :doubt :lean :land :nod}
           (into {} (map (juxt identity #(effect plain %))) mark-pads)))
    (is (= {:stress :pop :hold :swell :aside :hush :turn :rewind :doubt :wobble :land :slam}
           (into {} (map (juxt identity #(effect caffeinated %))) mark-pads)))))

(deftest a-pad-performs-even-in-a-spare-style
  (let [glyphs (glyphs-for "we did it")]
    (doseq [point [plain caffeinated]
            pad mark-pads
            :let [spare (assoc point :flourish 0.0)
                  mark {:span [0 9] :kind pad :amount 0.7}]]
      (testing (str pad " at " spare)
        (is (nil? (marks/gesture-for spare mark)) "too spare to perform it unasked")
        (is (= (marks/gesture-for (assoc point :flourish 1.0) mark)
               (-> (board/press pad spare glyphs [0 9] 0 0.7)
                   :cues first :cue (select-keys [:effect :intensity])))
            "the gesture it would choose at flourish 1")))))

(deftest a-named-style-is-its-point
  (let [glyphs (glyphs-for "we did it")]
    (is (= (board/press :turn caffeinated glyphs [0 9] 5 0.7)
           (board/press :turn :caffeinated glyphs [0 9] 5 0.7)))))

(deftest the-amount-is-the-cues-intensity
  (let [glyphs (glyphs-for "we did it")
        intensity #(-> (board/press :stress plain glyphs [0 9] 0 %) :cues first :cue :intensity)]
    (is (= 0.7 (intensity (board/amount 0))))
    (is (= 1.0 (intensity (board/amount 800))))))

(deftest a-selection-moves-only-its-glyphs
  (let [glyphs (glyphs-for "we did it")
        span (board/target "we did it" {:from 3 :to 6})
        [{:keys [glyph-ids cue]}] (:cues (board/press :land plain glyphs span 0 0.7))]
    (is (= [109 112 115] glyph-ids))
    (is (= "did" (:word cue))))
  (testing "a glyph of two units is in only when the span holds both"
    (let [glyphs (glyphs-of ["a" "😀" "b"])]
      (is (= [100 103] (-> (board/press :stress plain glyphs [0 3] 0 0.7) :cues first :glyph-ids)))
      (is (= [100] (-> (board/press :stress plain glyphs [0 2] 0 0.7) :cues first :glyph-ids))))))

(deftest the-word-is-at-most-64-units
  (let [text (apply str (repeat 40 "ab "))
        glyphs (glyphs-for text)
        [{:keys [glyph-ids cue]}] (:cues (board/press :stress plain glyphs [0 119] 0 0.7))]
    (is (= (subs text 0 64) (:word cue)))
    (is (= 119 (count glyph-ids)) "the cue still moves the whole span"))
  (testing "cut between glyphs, never inside one"
    (let [glyphs (glyphs-of (concat (repeat 63 "a") ["😀" "b"]))
          word (-> (board/press :stress plain glyphs [0 66] 0 0.7) :cues first :cue :word)]
      (is (= (apply str (repeat 63 "a")) word)))))

(deftest boom-places-a-card
  (let [glyphs (glyphs-for "we did it")
        added (board/press :boom plain glyphs [0 9] 3000 0.7)]
    (is (= {:directions [{:glyph-ids (mapv :id glyphs) :shot :card :energy 0.7
                          :at-ms 3000 :source :rules}]}
           added))
    (is (schema/valid? :ha/direction (first (:directions added))))
    (is (= 1.0 (-> (board/press :boom plain glyphs [0 9] 0 (board/amount 600))
                   :directions first :energy)))))

(deftest a-span-without-glyphs-adds-nothing
  (is (nil? (board/press :stress plain [] [0 3] 0 0.7)))
  (is (nil? (board/press :boom plain (glyphs-for "ab") [5 9] 0 0.7)))
  (is (nil? (board/press :stress plain (glyphs-for "a  b") [1 3] 0 0.7)) "only spaces"))

(def ^:private gen-unit
  (gen/double* {:min 0.0 :max 1.0 :NaN? false :infinite? false}))

(def ^:private gen-point
  (gen/hash-map :energy gen-unit :flourish gen-unit :softness gen-unit :warmth gen-unit))

(defspec every-press-is-valid-and-on-the-target 300
  (prop/for-all [chars (gen/vector (gen/elements ["a" "b" "W" "é" "😀" " " " " "." "\n" "!"]) 0 90)
                 pad (gen/elements (map :id board/pads))
                 point gen-point
                 a (gen/choose 0 100)
                 b (gen/choose 0 100)
                 t (gen/choose 0 100000)
                 held (gen/choose 0 2000)]
    (let [text (str/join chars)
          glyphs (glyphs-of chars)
          span (board/target text {:from a :to b})
          added (when span (board/press pad point glyphs span t (board/amount held)))]
      (cond
        (nil? span) (str/blank? text)
        ;; a selection may cut every glyph it touches in half
        (nil? added) true
        (= :boom pad)
        (let [[d & more] (:directions added)]
          (and (nil? more)
               (= [:directions] (keys added))
               (schema/valid? :ha/direction d)
               (= :card (:shot d))
               (= (board/amount held) (:energy d))
               (= t (:at-ms d))))
        :else
        (let [[c & more] (:cues added)
              [from to] span]
          (and (nil? more)
               (= [:cues] (keys added))
               (schema/valid? :ha/placed-cue c)
               (= :board (get-in c [:cue :source]))
               (= (board/amount held) (get-in c [:cue :intensity]))
               (= t (:at-ms c))
               ;; a selection may begin half way into a glyph of two units
               (str/includes? (subs text from to) (get-in c [:cue :word]))
               (every? (set (map :id glyphs)) (:glyph-ids c))))))))

;; --- amount -----------------------------------------------------------------

(deftest amount-from-the-hold
  (is (= 0.7 (board/amount 0)))
  (is (= 0.7 (board/amount 100)))
  (is (= 0.7 (board/amount 150)))
  (is (< 0.7 (board/amount 375) 1.0))
  (is (< (abs (- 0.85 (board/amount 375))) 1e-9) "half way between a tap and a full hold")
  (is (= 1.0 (board/amount 600)))
  (is (= 1.0 (board/amount 800)))
  (is (= 1.0 (board/amount 60000))))

(defspec amount-rises-with-the-hold 300
  (prop/for-all [a (gen/choose 0 1500)
                 b (gen/choose 0 1500)]
    (let [lo (min a b), hi (max a b)]
      (and (<= 0.7 (board/amount lo) (board/amount hi) 1.0)
           (schema/valid? :ha/unit (board/amount a))))))

;; --- hold -------------------------------------------------------------------

(defn- plays
  "What each event of events played, in order, from nothing held."
  [events]
  (second (reduce (fn [[holds out] event]
                    (let [{:keys [holds play]} (board/hold holds event)]
                      [holds (conj out play)]))
                  [nil []]
                  events)))

(deftest a-press-plays-on-its-release
  (is (= [nil {:pad :stress :ms 0}]
         (plays [[:pointer-down :stress 1000] [:pointer-up :stress 1000]])))
  (is (= [nil {:pad :stress :ms 650}]
         (plays [[:pointer-down :stress 1000] [:pointer-up :stress 1650]])))
  (is (= [nil {:pad :hold :ms 800}]
         (plays [[:key-down :hold 0] [:key-up :hold 800]]))))

(deftest a-key-repeat-keeps-the-first-time
  (is (= [nil nil nil {:pad :hold :ms 800}]
         (plays [[:key-down :hold 0] [:key-down :hold 500] [:key-down :hold 530] [:key-up :hold 800]]))))

(deftest a-release-plays-once
  (is (= [nil {:pad :stress :ms 10} nil]
         (plays [[:pointer-down :stress 0] [:pointer-up :stress 10] [:pointer-up :stress 20]])))
  (is (= [nil {:pad :stress :ms 10} nil]
         (plays [[:key-down :stress 0] [:key-up :stress 10] [:key-up :stress 20]]))))

;; The first build's fault: pointerdown put the pad down, only click cleared
;; it, and any later Digit keyup played it.
(deftest an-abandoned-press-never-plays
  (testing "dragged off the pad, then the pad's digit typed"
    (is (= [nil nil nil]
           (plays [[:pointer-down :stress 0] [:pointer-gone] [:key-up :stress 900]]))))
  (testing "a key release never plays a pad a pointer holds"
    (is (= [nil nil {:pad :stress :ms 1200}]
           (plays [[:pointer-down :stress 0] [:key-up :stress 900] [:pointer-up :stress 1200]]))))
  (testing "a pointer released on another pad plays neither"
    (is (= [nil nil nil]
           (plays [[:pointer-down :stress 0] [:pointer-up :hold 300] [:pointer-up :stress 400]]))))
  (testing "Alt+digit down, the window loses the focus, the key comes up later"
    (is (= [nil nil nil]
           (plays [[:key-down :stress 0] [:blur] [:key-up :stress 5000]]))))
  (testing "a blur drops a pointer press too"
    (is (= [nil nil nil]
           (plays [[:pointer-down :boom 0] [:blur] [:pointer-up :boom 100]]))))
  (testing "Enter down on a pad, the focus moved away, the key comes up elsewhere"
    (is (= [nil nil nil nil]
           (plays [[:key-down :stress 0] [:key-gone :stress] [:key-up :stress 300]
                   [:key-up :stress 900]]))))
  (testing "a key release with no key press before it"
    (is (= [nil] (plays [[:key-up :stress 100]])))))

(deftest pointer-and-key-holds-are-apart
  (is (= [nil nil {:pad :hold :ms 300} {:pad :stress :ms 700}]
         (plays [[:pointer-down :stress 0] [:key-down :hold 100]
                 [:key-up :hold 400] [:pointer-up :stress 700]])))
  (is (= [nil nil nil {:pad :hold :ms 400}]
         (plays [[:pointer-down :stress 0] [:key-down :hold 100]
                 [:pointer-gone] [:key-up :hold 500]]))))

(deftest a-bare-click-is-a-tap
  (testing "assistive technology clicks with no pointer or key"
    (is (= [{:pad :turn :ms 0}] (plays [[:click :turn 100]])))
    (is (= 0.7 (board/amount (:ms (first (plays [[:click :turn 100]])))))))
  (testing "the click a browser makes of a release that already played is not a second press"
    (is (= [nil {:pad :turn :ms 800} nil]
           (plays [[:key-down :turn 0] [:key-up :turn 800] [:click :turn 801]])))
    (is (= [nil {:pad :turn :ms 20} nil]
           (plays [[:pointer-down :turn 0] [:pointer-up :turn 20] [:click :turn 25]]))))
  (testing "a click later, or on another pad, is its own press"
    (is (= [nil {:pad :turn :ms 20} {:pad :turn :ms 0} {:pad :land :ms 0}]
           (plays [[:pointer-down :turn 0] [:pointer-up :turn 20]
                   [:click :turn 2000] [:click :land 2001]])))))

(def ^:private gen-event
  (gen/let [kind (gen/elements [:pointer-down :pointer-up :pointer-gone :key-down :key-up :key-gone :blur])
            pad (gen/elements (map :id board/pads))
            dt (gen/choose 0 900)]
    [kind pad dt]))

(defspec only-a-press-begun-on-the-pad-plays 300
  ;; whatever happens, a play is the release of a press of its own kind on
  ;; that pad, with nothing dropping it in between, and it lasts as long
  (prop/for-all [steps (gen/vector gen-event 0 40)]
    (let [events (map (fn [[kind pad _] t]
                        (if (#{:pointer-gone :blur} kind) [kind] [kind pad t]))
                      steps
                      (reductions + (map peek steps)))]
      (:ok?
       (reduce
        (fn [{:keys [holds pointer keys ok?]} [kind pad t :as event]]
          (let [{:keys [play] :as out} (board/hold holds event)
                expected (case kind
                           :pointer-up (when (= pad (:pad pointer)) {:pad pad :ms (- t (:t pointer))})
                           :key-up (when-let [t0 (keys pad)] {:pad pad :ms (- t t0)})
                           nil)]
            {:holds (:holds out)
             :pointer (case kind
                        :pointer-down {:pad pad :t t}
                        (:pointer-up :pointer-gone :blur) nil
                        pointer)
             :keys (case kind
                     :key-down (update keys pad #(or % t))
                     (:key-up :key-gone) (dissoc keys pad)
                     :blur {}
                     keys)
             :ok? (and ok? (= expected play))}))
        {:holds nil :pointer nil :keys {} :ok? true}
        events)))))

;; --- the XY pad -------------------------------------------------------------

(deftest xy-sets-energy-and-flourish
  (is (= {:energy 0.6 :flourish 0.4 :softness 0.5 :warmth 0.5}
         (board/xy->point plain 0.6 0.4)))
  (is (= [0.6 0.4] (board/point->xy (board/xy->point plain 0.6 0.4))))
  (is (= [0.5 0.35] (board/point->xy plain)))
  (testing "kept in [0 1]"
    (is (= {:energy 1.0 :flourish 0.0 :softness 0.9 :warmth 0.7}
           (board/xy->point (style/point :elegant) 1.7 -0.2)))))

(defspec xy-round-trips 300
  (prop/for-all [point gen-point
                 x gen-unit
                 y gen-unit
                 wild (gen/double* {:min -3.0 :max 3.0 :NaN? false :infinite? false})]
    (let [moved (board/xy->point point x y)]
      (and (= [x y] (board/point->xy moved))
           (= (select-keys point [:softness :warmth]) (select-keys moved [:softness :warmth]))
           (= point (apply board/xy->point point (board/point->xy point)))
           (schema/valid? :ha/style-point (board/xy->point point wild (- wild)))))))
