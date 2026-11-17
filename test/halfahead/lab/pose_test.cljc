(ns halfahead.lab.pose-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [halfahead.lab.director :as director]
            [halfahead.lab.gestures :as gestures]
            [halfahead.lab.pose :as pose]
            [halfahead.lab.reveal :as reveal]
            [halfahead.lab.style :as style]
            [halfahead.schema :as schema]))

;; --- generators -------------------------------------------------------------

(def ^:private old-effects
  [:pop :launch :shimmer :skitter :quake :melt :rewind :fuse :jitter :glow :wobble])

(def ^:private effects
  "Every gesture: the eleven the lab began with, then the eight of the score."
  (into old-effects [:swell :bloom :slam :hush :lean :nod :shiver :draw]))

(def gen-char
  ;; newlines are frequent so drafts run past four lines and recede
  (gen/elements ["a" "b" "W" "é" "日" "😀" " " " " "\n" "\n" "\n" "\t" "-" "'" "."]))

(def gen-ms (gen/choose 0 3000))

(defn- gen-cue [ids]
  (gen/hash-map
   :glyph-ids (gen/fmap (fn [[start len]] (vec (take len (drop start ids))))
                        (gen/tuple (gen/choose 0 (dec (count ids)))
                                   (gen/choose 1 (count ids))))
   :cue (gen/hash-map :word (gen/elements ["boom" "wow" "hello" "!"])
                      :effect (gen/elements effects)
                      :intensity (gen/one-of [(gen/elements [0 0.5 1 1.0])
                                              (gen/double* {:min 0 :max 1 :NaN? false})])
                      :source (gen/elements [:glossary :punctuation :shape :default]))
   :at-ms gen-ms))

(def gen-draft
  (gen/bind
   (gen/tuple (gen/vector gen-char 0 40) (gen/choose 0 8))
   (fn [[chars n-ghosts]]
     (let [n (count chars)
           ids (vec (range n))]
       (gen/fmap
        (fn [[borns ghosts cues]]
          {:text (str/join chars)
           :glyphs (mapv (fn [id c b] {:id id :char c :born-ms b}) ids chars borns)
           :ghosts (vec (map-indexed (fn [i g] (assoc g :id (+ n i))) ghosts))
           :cues cues
           :next-id (+ n n-ghosts)})
        (gen/tuple (gen/vector gen-ms n)
                   (gen/vector (gen/hash-map :char gen-char :died-ms gen-ms
                                             :index (gen/choose 0 (+ n 5))
                                             :id (gen/return 0))
                               n-ghosts)
                   (if (pos? n) (gen/vector (gen-cue ids) 0 6) (gen/return []))))))))

(defn- event-times [{:keys [glyphs ghosts cues]}]
  (concat (map :born-ms glyphs) (map :died-ms ghosts) (map :at-ms cues)))

(defn- latest [draft] (reduce max 0 (event-times draft)))

(def ^:private style-choices (into [:auto] style/styles))

(def gen-reveal-opts
  (gen/hash-map :start-level (gen/choose 0 3)
                :wands (gen/vector (gen/choose 0 6000) 0 3)))

(def gen-opts
  "Frame opts, at every reveal level: started anywhere, with wands, or
  with no :reveal-opts at all (the full stage)."
  (gen/fmap
   (fn [[opts reveal-opts]] (cond-> opts reveal-opts (assoc :reveal-opts reveal-opts)))
   (gen/tuple (gen/hash-map :style-choice (gen/elements style-choices)
                            :width-em (gen/choose 3 40)
                            :aspect (gen/double* {:min 0.4 :max 3 :NaN? false :infinite? false})
                            :advance (gen/elements [pose/default-advance
                                                    (fn [c] (if (#{"W" "日" "😀"} c) 1.0 0.5))]))
              (gen/one-of [(gen/return nil) gen-reveal-opts]))))

(defn- reveal-opts [opts] (:reveal-opts opts {:start-level 3}))

(defn- level-latest
  "The last time the reveal moves anything: 900 ms after the last level
  was reached (the lights and the crawl; the peel takes 700)."
  [draft opts]
  (+ 900 (reduce max 0 (keep identity (reveal/levels draft 1e9 (reveal-opts opts))))))

(defn- level [draft opts t] (:level (reveal/state draft t (reveal-opts opts))))

(def gen-case
  "A draft, opts, and a time before, during or after its motion."
  (gen/bind
   (gen/tuple gen-draft gen-opts)
   (fn [[draft opts]]
     (let [events (vec (event-times draft))
           near (if (seq events)
                  (gen/fmap (fn [[e d]] (+ e d))
                            (gen/tuple (gen/elements events) (gen/choose -50 2600)))
                  (gen/return 0))]
       (gen/fmap (fn [t] [draft opts t])
                 (gen/one-of [near near near (gen/choose -500 6000)]))))))

;; --- helpers ----------------------------------------------------------------

(defn- line-number [y] (long (Math/round (/ (- (double y)) 1.35))))

(defn- rest-poses
  "Where every glyph rests: on the layout, and at level 3 (the default) for
  lines more than 3 above the last glyph's, receded by 0.9 em, 0.18
  opacity (floor 0.12) and -0.1 rad (floor -0.5) per line beyond."
  ([draft opts] (rest-poses draft opts 3))
  ([draft {:keys [advance width-em]} level]
  (let [ps (pose/layout (map :char (:glyphs draft)) advance width-em)
        cur (if (seq ps) (line-number (:y (peek ps))) 0)]
    (mapv (fn [{:keys [id char]} {:keys [x y]}]
            (let [k (if (= 3 level) (- cur (line-number y) 3) 0)]
              (merge {:id id :char char :ghost? false :x x :y y :z 0.0 :rx 0.0 :ry 0.0 :rz 0.0
                      :scale 1.0 :opacity 1.0 :glow 0.0 :wobble 0.0}
                     (when (pos? k)
                       {:z (* -0.9 k)
                        :opacity (max 0.12 (- 1.0 (* 0.18 k)))
                        :rx (max -0.5 (* -0.1 k))}))))
          (:glyphs draft)
          ps))))

(defn- current-line-y [draft opts]
  (let [ps (pose/layout (map :char (:glyphs draft))
                        (:advance opts pose/default-advance) (:width-em opts 40))]
    (if (seq ps) (:y (peek ps)) 0.0)))

(defn- live [frame] (filterv (complement :ghost?) (:poses frame)))
(defn- ghosts [frame] (filterv :ghost? (:poses frame)))

(defn- close? [a b] (< (abs (- a b)) 1e-9))

;; --- properties -------------------------------------------------------------

(defspec drafts-are-valid 100
  (prop/for-all [d gen-draft] (schema/valid? :ha/draft d)))

(defspec frame-is-valid 300
  (prop/for-all [[draft opts t] gen-case]
    (let [f (pose/frame draft t opts)]
      (and (schema/valid? :ha/glyph-frame f)
           (= (level draft opts t) (:level (:reveal f)))))))

(defspec live-poses-spell-the-draft 300
  (prop/for-all [[draft opts t] gen-case]
    (let [ls (live (pose/frame draft t opts))]
      (and (= (:text draft) (str/join (map :char ls)))
           (= (map :id (:glyphs draft)) (map :id ls))))))

(defspec everything-settles-onto-the-layout 300
  (prop/for-all [[draft opts _] gen-case
                 k (gen/choose 0 3000)]
    (let [t (+ (max (+ (latest draft) 5000) (level-latest draft opts)) k)
          f (pose/frame draft t opts)]
      (and (= (rest-poses draft opts (level draft opts t)) (live f))
           (empty? (ghosts f))
           (= (if (>= (level draft opts t) 2) 1.0 0.0) (:lights (:reveal f)))))))

(defspec reduced-motion-is-still 300
  ;; levels still advance, but nothing moves into them
  (prop/for-all [[draft opts t] gen-case]
    (let [f (pose/frame draft t (assoc opts :reduced-motion? true))
          lv (level draft opts t)]
      (and (= (rest-poses draft opts lv) (live f))
           (empty? (ghosts f))
           (= (current-line-y draft opts) (:ty (:camera f)))
           (= {:level lv :lights (if (>= lv 2) 1.0 0.0)} (:reveal f))))))

(defspec frame-has-a-camera 300
  (prop/for-all [[draft opts t] gen-case]
    (let [c (:camera (pose/frame draft t opts))]
      (and (schema/valid? :ha/camera c)
           (= c (:camera (pose/frame draft t opts)))))))

(defspec camera-frames-the-column 300
  (prop/for-all [[draft opts t] gen-case]
    (let [{:keys [x y z tx ty tz fov]} (:camera (pose/frame draft t opts))
          width (double (:width-em opts))
          dist (Math/sqrt (+ (* (- x tx) (- x tx)) (* (- y ty) (- y ty)) (* (- z tz) (- z tz))))
          pitch-deg (* (/ 180 Math/PI) (Math/atan2 (- y ty) (- z tz)))
          half (Math/tan (* 0.5 fov (/ Math/PI 180)))]
      (and (close? tx (/ width 2)) (= 0.0 tz) (close? x tx)
           (<= 0 pitch-deg 8)
           (< (abs (- 0.75 (/ width (* 2 dist (:aspect opts) half)))) 1e-9)))))

(defspec every-glyph-stays-within-half-an-em 300
  (prop/for-all [[draft opts t] gen-case]
    (let [rest-x (into {} (map (juxt :id :x)) (rest-poses draft opts))]
      (every? (fn [{:keys [id x]}] (<= (abs (- x (rest-x id))) (+ 0.5 1e-9)))
              (live (pose/frame draft t opts))))))

(defspec frame-names-the-style 300
  (prop/for-all [[draft {:keys [style-choice] :as opts} t] gen-case
                 reduced? gen/boolean]
    (let [s (:style (pose/frame draft t (assoc opts :reduced-motion? reduced?)))]
      (= s (if (= :auto style-choice)
             (style/infer (:glyphs draft) t)
             style-choice)))))

(defspec auto-motion-is-causal 300
  ;; A space after the last glyph never re-wraps earlier words or changes
  ;; the current line, unless the text ends with a newline (then the space
  ;; starts the next line, and the camera rightly moves); so any change to
  ;; an earlier glyph would come from the style.
  (prop/for-all [[draft opts _] gen-case
                 gaps (gen/vector (gen/elements [0 40 60 90 200 450 600 1500]) 40)
                 later (gen/choose 1 2000)
                 before (gen/choose 1 3000)]
    (let [;; births in a rhythm, so :auto infers every style
          draft (update draft :glyphs
                        #(mapv (fn [g born] (assoc g :born-ms born)) % (reductions + 0 gaps)))
          b (+ (latest draft) later)
          t (- b before)
          longer (-> draft
                     (update :text str " ")
                     (update :glyphs conj {:id (:next-id draft) :char " " :born-ms b})
                     (update :next-id inc))
          opts (assoc opts :style-choice :auto)]
      (or (str/ends-with? (:text draft) "\n")
          (= (live (pose/frame draft t opts))
             (pop (live (pose/frame longer t opts))))))))

(defspec frames-are-deterministic 100
  (prop/for-all [[draft opts t] gen-case]
    (= (pose/frame draft t opts) (pose/frame draft t opts))))

;; --- examples ---------------------------------------------------------------

(deftest layout-lines-and-wrapping
  (let [at #(pose/layout (map str %1) pose/default-advance %2)]
    (testing "a space advances; d of \"ab cd\" at width 10 sits at x 2.4"
      (is (= {:x 2.4 :y 0.0} (nth (at "ab cd" 10) 4))))
    (testing "a newline starts a new line at y -1.35"
      (let [ps (at "ab\ncd" 10)]
        (is (= {:x 0.0 :y -1.35} (nth ps 3)))
        (is (close? 0.6 (:x (nth ps 4))))))
    (testing "a word that would cross the width moves whole to the next line"
      (let [ps (at "ab cdef" 3)]
        (is (= {:x 0.0 :y -1.35} (nth ps 3)))
        (is (every? #(= -1.35 (:y %)) (subvec ps 3)))
        (is (= 0.0 (:y (nth ps 2))) "the space stays behind")))
    (testing "a word wider than the width breaks anywhere"
      (let [ps (at "abcdefghij" 3)]
        (is (= [0.0 0.0 0.0 0.0 0.0 -1.35 -1.35 -1.35 -1.35 -1.35]
               (map :y (take 10 ps))))
        (is (= 0.0 (:x (nth ps 5))))))))

(defn- draft-of [text born cues]
  {:text text
   :glyphs (vec (map-indexed (fn [i c] {:id i :char (str c) :born-ms born}) text))
   :ghosts []
   :cues cues
   :next-id (count text)})

(deftest quake-flashes-then-rests
  (let [t 10000
        cue {:glyph-ids [0 1 2 3]
             :cue {:word "boom" :effect :quake :intensity 1 :source :glossary}
             :at-ms t}
        draft (draft-of "boom " 0 [cue])
        opts {}
        base (rest-poses draft {:advance pose/default-advance :width-em 40})]
    (testing "at t+100 ms every quaking glyph glows and is off its base"
      (let [ps (live (pose/frame draft (+ t 100) opts))]
        (doseq [i (range 4)
                :let [p (nth ps i) b (nth base i)]]
          (is (pos? (:glow p)))
          (is (or (not= (:x p) (:x b)) (not= (:y p) (:y b)))))
        (is (= (nth base 4) (nth ps 4)) "the space was not in the cue")))
    (testing "at t+1500 ms they are back at base"
      (is (= base (live (pose/frame draft (+ t 1500) opts)))))))

(deftest every-effect-moves-then-settles
  (doseq [effect effects]
    (let [cue {:glyph-ids (vec (range 30))
               :cue {:word "w" :effect effect :intensity 1 :source :default}
               :at-ms 5000}
          draft (draft-of (apply str (repeat 30 "x")) 0 [cue])
          base (rest-poses draft {:advance pose/default-advance :width-em 40})]
      (testing (str effect)
        (is (some true? (for [dt [50 150 300 500 800]]
                          (not= base (live (pose/frame draft (+ 5000 dt) {}))))))
        (is (= base (live (pose/frame draft 10000 {}))))))))

(deftest stacked-cues-stay-within-half-an-em
  (let [cue {:glyph-ids [0]
             :cue {:word "boom" :effect :quake :intensity 1 :source :glossary}
             :at-ms 1000}
        draft (draft-of "b" 0 (vec (repeat 6 cue)))
        dxs (for [t (range 1000 2500 5)]
              (abs (- (:x (first (live (pose/frame draft t {})))) 0.0)))]
    (is (every? #(<= % (+ 0.5 1e-9)) dxs))
    (is (some #(> % 0.3) dxs) "six quakes would reach past 0.3 em unclamped")))

(deftest fuse-converges-in-depth-not-sideways
  (let [t 10000
        cue {:glyph-ids [0 1 2 3 4]
             :cue {:word "merge" :effect :fuse :intensity 0.8 :source :glossary}
             :at-ms t}
        draft (draft-of "merge " 0 [cue])
        base (rest-poses draft {:advance pose/default-advance :width-em 40})
        samples (for [dt (range 0 1000 10)] (live (pose/frame draft (+ t dt) {})))]
    (testing "no letter leaves its place sideways"
      (is (every? (fn [ps] (= (map :x base) (map :x ps))) samples)))
    (testing "the letters come in from in front of the line"
      (is (some (fn [ps] (some #(pos? (:z %)) ps)) samples)))
    (testing "neighbours come from opposite sides of the line"
      (is (some (fn [[a b]] (neg? (* (:y a) (:y b))))
                (map #(take 2 %) samples))))
    (testing "and all of them land on it"
      (is (= base (live (pose/frame draft (+ t 1500) {})))))))

(deftest births-spring-in
  (let [draft (draft-of "a" 1000 [])
        at #(first (:poses (pose/frame draft % {})))]
    (is (= 0.0 (:opacity (at 1000))))
    (is (< 0.3 (:opacity (at 1030)) 0.4))
    (is (< 1.1 (reduce max (map (comp :scale at) (range 1000 1400 5))) 1.2)
        "overshoots by about 15%")
    (is (= 1.0 (:scale (at 1400))))))

(deftest ghosts-drift-away-as-embers
  (let [draft (assoc (draft-of "ab" 0 [])
                     :ghosts [{:id 7 :char "c" :died-ms 1000 :index 1}
                              {:id 8 :char "d" :died-ms 1000 :index 9}])
        f (pose/frame draft 1100 {})
        [g1 g2] (ghosts f)]
    (testing "embers come after the live poses"
      (is (= [false false true true] (map :ghost? (:poses f)))))
    (testing "an ember rises and fades from where its index stands"
      (is (close? 0.6 (:x g1)))
      (is (< 0 (:y g1)))
      (is (< 0 (:opacity g1) 1)))
    (testing "an index past the end is clamped to the caret"
      (is (= (:x (:caret f)) (:x g2))))
    (testing "at 600 ms the ember is gone"
      (is (empty? (ghosts (pose/frame draft 1600 {})))))))

(deftest camera-eases-to-a-new-line
  (let [draft {:text "ab\ncd"
               :glyphs [{:id 0 :char "a" :born-ms 0} {:id 1 :char "b" :born-ms 0}
                        {:id 2 :char "\n" :born-ms 0}
                        {:id 3 :char "c" :born-ms 1000} {:id 4 :char "d" :born-ms 1000}]
               :ghosts [] :cues [] :next-id 5}
        ty #(:ty (:camera (pose/frame draft % {})))]
    (testing "on the new line's y from the first glyph's birth + 450 ms"
      (is (= -1.35 (ty 1450)))
      (is (= -1.35 (ty 5000))))
    (testing "between the two lines halfway through"
      (is (< -1.35 (ty 1225) 0.0)))
    (testing "on the old line before the new line's first glyph is born"
      (is (= 0.0 (ty 900))))
    (testing "the target stays on the column's centre and the text plane"
      (let [c (:camera (pose/frame draft 1225 {:width-em 18}))]
        (is (= 9.0 (:tx c)))
        (is (= 0.0 (:tz c)))))
    (testing "with reduced motion it is on the new line at once"
      (is (= -1.35 (:ty (:camera (pose/frame draft 1100 {:reduced-motion? true}))))))))

(deftest old-lines-recede
  (let [text (str/join "\n" (map #(str "line " %) (range 14)))
        draft (draft-of text 0 [])
        ps (live (pose/frame draft 10000 {}))
        line (fn [n] (filter #(close? (* -1.35 n) (:y %)) ps))]
    (testing "the current line and the three above it are untouched"
      (doseq [n (range 10 14), p (line n)]
        (is (= [0.0 1.0 0.0] [(:z p) (:opacity p) (:rx p)]))))
    (testing "the fourth line above is one step back"
      (doseq [p (line 9)]
        (is (close? -0.9 (:z p)))
        (is (close? 0.82 (:opacity p)))
        (is (close? -0.1 (:rx p)))))
    (testing "each further line a step more, down to the floors"
      (is (close? -1.8 (:z (first (line 8)))))
      (doseq [p (line 0)]
        (is (close? -9.0 (:z p)))
        (is (= 0.12 (:opacity p)))
        (is (= -0.5 (:rx p)))))
    (testing "and it holds in reduced motion"
      (is (= ps (live (pose/frame draft 10000 {:reduced-motion? true})))))))

(deftest caret
  (is (= {:x 0.0 :y 0.0} (:caret (pose/frame (draft-of "" 0 []) 0 {}))))
  (is (close? 1.2 (:x (:caret (pose/frame (draft-of "ab" 0 []) 0 {})))))
  (is (= {:x 0.0 :y -1.35} (:caret (pose/frame (draft-of "ab\n" 0 []) 0 {})))))

;; --- styles -----------------------------------------------------------------

(def ^:private quake
  {:glyph-ids [0 1 2 3]
   :cue {:word "boom" :effect :quake :intensity 1 :source :glossary}
   :at-ms 10000})

(deftest every-effect-settles-in-every-style
  (doseq [s style/styles, effect effects]
    (let [cue {:glyph-ids (vec (range 30))
               :cue {:word "w" :effect effect :intensity 1 :source :default}
               :at-ms 5000}
          draft (draft-of (apply str (repeat 30 "x")) 0 [cue])
          base (rest-poses draft {:advance pose/default-advance :width-em 40})
          opts {:style-choice s}]
      (testing (str s " " effect)
        (is (some true? (for [dt [50 150 300 500 800]]
                          (not= base (live (pose/frame draft (+ 5000 dt) opts))))))
        (is (= base (live (pose/frame draft 10000 opts))))))))

(deftest styles-differ
  (let [draft (draft-of "boom " 10000 [quake])
        at (fn [s] (live (pose/frame draft 10100 {:style-choice s})))
        poses (map at style/styles)]
    (is (= (count style/styles) (count (set poses)))
        "a birth and a quake 100 ms in look different in every style")
    (is (= (live (pose/frame draft 10100 {})) (at :plain))
        ":plain is the default")))

(deftest coarse-moves-in-steps
  (testing "a quake holds each of 3 poses, then rests"
    (let [draft (draft-of "boom " 0 [quake])
          at #(first (live (pose/frame draft % {:style-choice :coarse})))
          moving (map at (range 10000 10500 5))]
      (is (= 3 (count (set moving))) "the quake lasts 500 ms in coarse")
      (is (= (first (rest-poses draft {:advance pose/default-advance :width-em 40}))
             (at 10500)))))
  (testing "a birth takes 3 steps"
    (let [draft (draft-of "a" 1000 [])
          scales (for [t (range 1000 1200 2)]
                   (:scale (first (live (pose/frame draft t {:style-choice :coarse})))))]
      (is (= 3 (count (set scales))))
      (is (= 1.0 (:scale (first (live (pose/frame draft 1200 {:style-choice :coarse})))))))))

(deftest elegant-glides
  (let [draft (draft-of "a" 1000 [])
        scales (for [t (range 1000 1600 5)]
                 (:scale (first (live (pose/frame draft t {:style-choice :elegant})))))]
    (testing "a birth never overshoots"
      (is (every? #(<= % (+ 1.0 1e-9)) scales)))
    (testing "and grows steadily, more slowly than plain"
      (is (apply <= scales))
      (is (< (:scale (first (live (pose/frame draft 1200 {:style-choice :elegant}))))
             (:scale (first (live (pose/frame draft 1200 {}))))))
      (is (= 1.0 (:scale (first (live (pose/frame draft 1600 {:style-choice :elegant})))))))))

(deftest caffeinated-overshoots-more-than-plain
  (let [draft (draft-of "a" 1000 [])
        peak (fn [s] (reduce max (for [t (range 1000 1400 2)]
                                   (:scale (first (live (pose/frame draft t {:style-choice s})))))))]
    (is (> (peak :caffeinated) (+ 0.05 (peak :plain))))))

(deftest a-letter-keeps-its-birth-style
  (let [slow (mapv (fn [i] {:id i :char "a" :born-ms (* 450 i)}) (range 12))
        letter (peek slow)
        b (:born-ms letter)
        burst (mapv (fn [k] {:id (+ 12 k) :char "b" :born-ms (+ b 40 (* 40 k))}) (range 6))
        draft (fn [glyphs] {:text (apply str (map :char glyphs)) :glyphs glyphs
                            :ghosts [] :cues [] :next-id (count glyphs)})
        pose-at (fn [glyphs t] (nth (live (pose/frame (draft glyphs) t {:style-choice :auto})) 11))
        t (+ b 300)]
    (testing "typed slowly, the last letter is born elegant"
      (is (= (pose-at slow t)
             (nth (live (pose/frame (draft slow) t {:style-choice :elegant})) 11))))
    (testing "a fast burst after it leaves it as it was"
      (is (= (pose-at slow t) (pose-at (into slow burst) t))))
    (testing "but the burst itself is styled from the rhythm"
      (is (= :coarse (:style (pose/frame (draft (into slow burst)) (+ b 280) {:style-choice :auto})))))))

;; --- the score's channels ------------------------------------------------------

(def ^:private score-keys [:sx :sy :skew :weight :blur :fill :ink :tint])

(defn- cue-on [text effect intensity at]
  (draft-of text 0 [{:glyph-ids (vec (range (count text)))
                     :cue {:word text :effect effect :intensity intensity :source :default}
                     :at-ms at}]))

(deftest effects-cover-every-gesture
  (is (= (set effects) (set (keys gestures/patches)))))

(deftest new-channels-reach-the-frame-and-rest
  (testing "mid-cue the channel is in the pose, off its rest and in range"
    (doseq [[effect channel dt in-range?] [[:bloom :weight 800 #(< 0 % 1)]
                                           [:hush :weight 700 #(< -1 % 0)]
                                           [:hush :blur 700 #(< 0 % 1)]
                                           [:lean :skew 390 pos?]
                                           [:draw :fill 250 #(< 0 % 1)]]]
      (let [f (pose/frame (cue-on "a" effect 1 5000) (+ 5000 dt) {})
            v (get (first (live f)) channel)]
        (is (schema/valid? :ha/glyph-frame f) (str effect))
        (is (and (some? v) (in-range? v)) (str effect " " channel " is " v)))))
  (testing "ink starts unfilled: a draw's first frame has :fill 0"
    (is (= 0.0 (:fill (first (live (pose/frame (cue-on "a" :draw 1 5000) 5000 {})))))))
  (testing "a pose at rest carries none of the new keys"
    (doseq [effect effects
            p (live (pose/frame (cue-on "abc" effect 1 5000) 10000 {}))]
      (is (not-any? #(contains? p %) score-keys) (str effect))))
  (testing "nor does any pose moved by one of the first eleven effects"
    (doseq [effect old-effects
            dt (range 0 1500 50)
            p (live (pose/frame (cue-on "abcdef" effect 1 5000) (+ 5000 dt) {}))]
      (is (not-any? #(contains? p %) score-keys) (str effect " at " dt))))
  (testing "stacked cues stay in range"
    (let [slam {:glyph-ids [0]
                :cue {:word "b" :effect :slam :intensity 1 :source :default}
                :at-ms 5000}
          hush (assoc-in slam [:cue :effect] :hush)
          draw (assoc-in slam [:cue :effect] :draw)
          at (fn [cue dt]
               (let [f (pose/frame (draft-of "b" 0 (vec (repeat 4 cue))) (+ 5000 dt) {})]
                 (is (schema/valid? :ha/glyph-frame f))
                 (first (live f))))]
      (is (= 1.0 (:weight (at slam 30))) "four slams would be 3 unclamped")
      (is (= -1.0 (:weight (at hush 700))))
      (is (= 1.0 (:blur (at hush 700))))
      (is (= 0.0 (:fill (at draw 100)))))))

(deftest amplitude-scales-skew
  (doseq [s [:plain :elegant :caffeinated]]
    (let [{:keys [amplitude tempo]} (style/params s)
          draft (cue-on "a" :lean 1 5000)
          peak (reduce max (for [t (range 5000 (+ 5000 (* 1300 tempo)))]
                             (:skew (first (live (pose/frame draft t {:style-choice s}))) 0.0)))]
      (is (< (abs (- peak (* 0.28 amplitude))) 1e-3) (str s " peaks at " peak)))))

(deftest swell-lifts-each-letter-in-turn
  (let [k 0.7
        draft (cue-on "loooove" :swell k 5000)
        times (range 5000 8300 5)
        lifts (mapv (fn [t] (mapv :y (live (pose/frame draft t {})))) times)
        peak-at (fn [i] (apply max-key #(nth (nth lifts (quot (- % 5000) 5)) i) (reverse times)))
        peaks (map peak-at (range 7))]
    (testing "each letter's lift peaks 120 ms after the one before"
      (is (= (map #(+ 5900 (* 120 %)) (range 7)) peaks)))
    (testing "a sixth of an em at 0.7, and never more"
      (is (close? (* 0.25 k) (reduce max (map #(reduce max %) lifts))))
      (is (every? #(<= 0.0 % (+ (* 0.25 k) 1e-9)) (flatten lifts))))
    (testing "it glows as it lifts"
      (is (pos? (:glow (first (live (pose/frame draft 5900 {})))))))
    (testing "and the word is at rest when the last letter's 2400 ms are up"
      (is (not= (rest-poses draft {:advance pose/default-advance :width-em 40})
                (live (pose/frame draft 8115 {}))))
      (is (= (rest-poses draft {:advance pose/default-advance :width-em 40})
             (live (pose/frame draft 8120 {})))))))

;; --- shots ------------------------------------------------------------------

(def ^:private gen-directed-case
  "A draft, opts and a time, as gen-case, with directions: those the
  director places on it at some time, and arbitrary ones (absent ids,
  overlaps, any shot, any time)."
  (gen/bind
   (gen/tuple gen-draft gen-opts gen-ms)
   (fn [[draft opts at]]
     (let [n (:next-id draft)]
       (gen/fmap
        (fn [[arbitrary t]]
          (let [placed (:directions (director/direct draft at))
                draft (assoc draft :directions (into placed arbitrary))]
            [draft opts t]))
        (gen/tuple (gen/vector (gen/hash-map :glyph-ids (gen/vector (gen/choose 0 (+ n 3)) 1 8)
                                             :shot (gen/elements [:line :card :push :pull])
                                             :energy (gen/elements [0.2 0.5 0.6 0.9])
                                             :at-ms gen-ms
                                             :source (gen/elements [:rules :model]))
                               0 3)
                   (gen/one-of [(gen/fmap #(+ at %) (gen/choose -100 5000))
                                (gen/choose -500 9000)])))))))

(defn- latest-direction [draft] (reduce max 0 (map :at-ms (:directions draft))))

(defspec directed-drafts-are-valid 100
  (prop/for-all [[d _ _] gen-directed-case] (schema/valid? :ha/draft d)))

(defspec frame-is-valid-with-directions 300
  (prop/for-all [[draft opts t] gen-directed-case]
    (schema/valid? :ha/glyph-frame (pose/frame draft t opts))))

(defspec live-poses-spell-the-draft-with-directions 300
  (prop/for-all [[draft opts t] gen-directed-case]
    (let [ls (live (pose/frame draft t opts))]
      (and (= (:text draft) (str/join (map :char ls)))
           (= (map :id (:glyphs draft)) (map :id ls))))))

(defspec everything-settles-after-directions 300
  (prop/for-all [[draft opts _] gen-directed-case
                 k (gen/choose 0 3000)]
    (let [t (+ (max (+ (latest draft) 5000) (+ (latest-direction draft) 5000)
                    (level-latest draft opts))
               k)
          f (pose/frame draft t opts)]
      (and (= (rest-poses draft opts (level draft opts t)) (live f))
           (empty? (ghosts f))
           (= (:camera (pose/frame (dissoc draft :directions) t opts)) (:camera f))))))

(defspec reduced-motion-performs-no-shots 300
  ;; a direction may open level 3, but it is not performed
  (prop/for-all [[draft opts t] gen-directed-case]
    (let [opts (assoc opts :reduced-motion? true)
          f (pose/frame draft t opts)]
      (and (= (rest-poses draft opts (level draft opts t)) (live f))
           (= (:camera (pose/frame (dissoc draft :directions) t opts)) (:camera f))))))

(defn- directed [text shot at]
  (let [d (draft-of text 0 [])]
    (assoc d :directions [{:glyph-ids (into [] (comp (remove (comp str/blank? :char)) (map :id))
                                            (:glyphs d))
                           :shot shot :energy 0.5 :at-ms at :source :rules}])))

(defn- distance [{:keys [x y z tx ty tz]}]
  (Math/sqrt (+ (* (- x tx) (- x tx)) (* (- y ty) (- y ty)) (* (- z tz) (- z tz)))))

(deftest a-card-takes-the-stage
  (let [text "so it goes and goes! "             ; 5 words: reveal 1100, hold to 2300
        draft (directed text :card 10000)
        opts {:width-em 18}
        in (set (:glyph-ids (first (:directions draft))))
        base (rest-poses draft {:advance pose/default-advance :width-em 18})
        at #(live (pose/frame draft (+ 10000 %) opts))
        held (at 1700)
        card (filter (comp in :id) held)
        s (:scale (first card))
        left (+ (:x (first card)) (* 0.3 (- 1 s)))
        right (+ (:x (last card)) (* 0.3 (+ 1 s)))]
    (testing "held, the phrase spans 70% of the view's width, centred on the target"
      (is (< 1 s))
      (is (every? #(close? s (:scale %)) card))
      (is (close? 0.7 (/ (- right left) (/ 18 0.75))))
      (is (close? 9.0 (/ (+ left right) 2)))
      (is (every? #(= 1.0 (:opacity %)) card)))
    (testing "the rest steps back"
      (is (every? #(<= (:opacity %) 0.08) (remove (comp in :id) held))))
    (testing "a word a beat: word k is still in the line before k x 220 ms"
      (doseq [[k start] [[2 6] [3 11] [4 15]]]
        (is (= (subvec base start (+ start 2))
               (subvec (vec (at (dec (* k 220)))) start (+ start 2))))))
    (testing "at rest by 4500 ms"
      (is (= base (at 2700)))
      (is (= base (at 4500))))))

(deftest the-rest-of-the-line-steps-back-for-a-card
  (let [draft (assoc (draft-of "hello there WHAT A DAY! " 0 [])
                     :directions [{:glyph-ids [12 13 14 15 17 19 20 21 22]
                                   :shot :card :energy 0.5 :at-ms 10000 :source :rules}])
        undirected (dissoc draft :directions)
        opts {:width-em 18}
        in (set (:glyph-ids (first (:directions draft))))
        ;; 3 words: reveal 660, hold to 1860, folded by 2260
        at (fn [u & [o]] (live (pose/frame draft (+ 10000 u) (merge opts o))))
        without (fn [u & [o]] (live (pose/frame undirected (+ 10000 u) (merge opts o))))
        mid 1260]
    (testing "held, every glyph off the card is faint and at least 1.9 em further back"
      (let [held (at mid)
            unshot (into {} (map (juxt :id identity)) (without mid))
            rest (remove (comp in :id) held)]
        (is (and (seq rest)
                 (every? #(<= (:opacity %) 0.08) rest)
                 (every? #(<= (:z %) (- (:z (unshot (:id %))) 1.9)) rest)
                 (every? #(= 1.0 (:opacity %)) (filter (comp in :id) held))))))
    (testing "the line returns exactly once the card has folded"
      (is (and (= (without 2260) (at 2260))
               (= (without 5000) (at 5000)))))
    (testing "reduced motion performs no card"
      (is (= (without mid {:reduced-motion? true}) (at mid {:reduced-motion? true}))))))

(deftest a-push-moves-the-camera-in-and-back
  (let [draft (directed "well is it? " :push 10000)
        rest (:camera (pose/frame (dissoc draft :directions) 10800 {}))
        at #(:camera (pose/frame draft (+ 10000 %) {}))
        held (at 800)]
    (is (close? (* 0.65 (distance rest)) (distance held)))
    (testing "looking at the phrase's centre"
      (is (close? (/ (* 0.6 (+ 0 1 2 3 5 6 8 9 10)) 9) (:tx held))))
    (is (= rest (at 1700)))))

(deftest a-pull-moves-the-camera-back-and-in
  (let [draft (directed "one two three four five six seven eight nine ten eleven twelve. "
                        :pull 10000)
        rest (:camera (pose/frame (dissoc draft :directions) 11000 {}))
        at #(:camera (pose/frame draft (+ 10000 %) {}))]
    (is (close? (* 1.4 (distance rest)) (distance (at 1000))))
    (is (= [(:tx rest) (:ty rest)] [(:tx (at 1000)) (:ty (at 1000))]))
    (is (= rest (at 2100)))))

(deftest a-line-does-nothing-extra
  (let [draft (directed "so it goes. " :line 10000)]
    (doseq [dt [0 100 500 1000]]
      (is (= (pose/frame (dissoc draft :directions) (+ 10000 dt) {})
             (pose/frame draft (+ 10000 dt) {}))))))

;; --- reveal levels ------------------------------------------------------------

(def ^:private gen-flat-case
  "As gen-case, at level 0 throughout: started there, no wands, and at
  most two cues, too few to reach level 1."
  (gen/fmap (fn [[draft opts t]]
              [(update draft :cues #(vec (take 2 %))) (assoc opts :reveal-opts {:start-level 0}) t])
            gen-case))

(defspec level-0-is-a-flat-page 300
  (prop/for-all [[draft opts t] gen-flat-case]
    (let [f (pose/frame draft t opts)
          stage (pose/frame (assoc draft :cues []) t (dissoc opts :reveal-opts))
          {:keys [y ty]} (:camera f)]
      (and (= {:level 0 :lights 0.0} (:reveal f))
           (every? #(= [0.0 0.0 0.0 0.0] [(:z %) (:rx %) (:ry %) (:rz %)]) (live f))
           (every? #(zero? (:z %)) (ghosts f))
           (close? y ty)
           ;; cues perform nothing
           (= f (pose/frame (assoc draft :cues []) t opts))
           ;; a third of the birth the full stage gives, where that is not
           ;; clamped at scale 0
           (every? (fn [[p s]] (or (zero? (:scale s))
                                   (< (abs (- (- (:scale p) 1.0) (/ (- (:scale s) 1.0) 3.0))) 1e-9)))
                   (map vector (live f) (live stage)))))))

(def ^:private gen-inline-case
  "As gen-case, at level 1: started there, no wands, and only quiet
  default cues, so use never reaches level 2."
  (gen/fmap (fn [[draft opts t]]
              [(update draft :cues (fn [cs] (mapv #(assoc-in % [:cue :source] :default) cs)))
               (assoc opts :reveal-opts {:start-level 1}) t])
            gen-case))

(defspec level-1-performs-in-the-plane 300
  (prop/for-all [[draft opts t] gen-inline-case]
    (let [f (pose/frame draft t opts)
          rest-x (into {} (map (juxt :id :x)) (rest-poses draft opts 1))]
      (and (= 1 (:level (:reveal f)))
           (= 0.0 (:lights (:reveal f)))
           (every? #(= [0.0 0.0 0.0] [(:z %) (:rx %) (:ry %)]) (live f))
           (every? (fn [{:keys [id x]}] (<= (abs (- x (rest-x id))) (+ 0.5 1e-9))) (live f))))))

(deftest level-1-still-performs-cues
  (let [cue {:glyph-ids [0 1 2 3]
             :cue {:word "back" :effect :rewind :intensity 1 :source :default}
             :at-ms 10000}
        draft (draft-of "back " 0 [cue])
        f (pose/frame draft 10200 {:reveal-opts {:start-level 1}})]
    (is (some #(not= 0.0 (:rz %)) (live f)) "a rewind turns in the plane")
    (is (= (live (pose/frame draft 12000 {:reveal-opts {:start-level 1}}))
           (rest-poses draft {:advance pose/default-advance :width-em 40} 1)))))

(deftest level-2-has-depth-but-no-shots
  (let [text (str/join "\n" (map #(str "line " %) (range 8)))
        opts {:reveal-opts {:start-level 1 :wands [5000]}}  ; level 2 at 5000
        plain (draft-of text 0 [])
        early-card (assoc plain :directions [{:glyph-ids [0 1 2 3] :shot :card :energy 0.9
                                              :at-ms 4000 :source :rules}])
        old-card (assoc plain :directions [{:glyph-ids [0 1 2 3] :shot :card :energy 0.9
                                            :at-ms 5500 :source :rules}])]
    (is (= 2 (:level (:reveal (pose/frame plain 6000 opts)))))
    (testing "a card placed before level 2, or on letters older than it, opens nothing and is not performed"
      (doseq [d [early-card old-card], t [5600 6000 6500]]
        (is (= (pose/frame plain t opts) (pose/frame d t opts)))))
    (testing "old lines do not recede"
      (is (every? #(= 1.0 (:opacity %)) (live (pose/frame plain 9000 opts)))))
    (testing "births come forward in depth"
      (let [born (assoc plain :glyphs (conj (:glyphs plain) {:id 99 :char "x" :born-ms 8000}))]
        (is (pos? (:z (peek (live (pose/frame born 8100 opts))))))))))

(deftest level-3-is-the-full-stage
  (let [text (str/join "\n" (map #(str "line " %) (range 8)))
        draft (directed text :card 10000)]
    (doseq [t [0 10500 11000 20000]]
      (is (= (pose/frame draft t {}) (pose/frame draft t {:reveal-opts {:start-level 3}}))))
    (is (= {:level 3 :lights 1.0} (:reveal (pose/frame draft 0 {}))))
    (testing "reached by a wand, the crawl eases in over 900 ms"
      (let [opts {:reveal-opts {:start-level 2 :wands [5000]}}
            z0 #(:z (first (live (pose/frame draft % opts))))]
        (is (= 0.0 (z0 4999)))
        (is (< -3.6 (z0 5450) 0.0))
        (is (close? -3.6 (z0 5900)) "line 0 is four lines back from line 7")))))

(deftest letters-peel-off-the-page
  (let [draft (assoc (draft-of "abc" 0 [])
                     :glyphs [{:id 0 :char "a" :born-ms 0} {:id 1 :char "b" :born-ms 0}
                              {:id 2 :char "c" :born-ms 5100}])
        opts {:reveal-opts {:start-level 1 :wands [5000]}}
        z (fn [i t & [o]] (:z (nth (live (pose/frame draft t (or o opts))) i)))]
    (testing "every letter present when level 2 is reached lifts 0.6 em"
      (is (close? 0.6 (z 0 5350)))
      (is (< 0.0 (z 1 5100) 0.6)))
    (testing "and is back on the page by 700 ms"
      (is (= 0.0 (z 0 5700)))
      (is (= 0.0 (z 0 9000))))
    (testing "a letter written after it does not peel"
      (is (= 0.0 (z 2 5600))))
    (testing "not with reduced motion"
      (is (= 0.0 (z 0 5350 (assoc opts :reduced-motion? true)))))
    (testing "not when the lab started at level 2"
      (is (= 0.0 (z 0 5350 {:reveal-opts {:start-level 2}}))))))

(deftest the-camera-pitches-with-the-lights
  (let [draft (draft-of "ab" 0 [])
        pitch (fn [t o] (let [{:keys [y ty z tz]} (:camera (pose/frame draft t o))]
                          (Math/atan2 (- y ty) (- z tz))))
        opts {:reveal-opts {:start-level 1 :wands [5000]}}]
    (is (close? 0.0 (pitch 4000 opts)) "frontal with the house lights up")
    (is (< 0.0 (pitch 5450 opts) (pitch 5900 opts)))
    (is (close? (pitch 5900 opts) (pitch 0 {})) "the stage's 2 degrees once they are down")))

;; --- a reading: hurried cues and the head's ink --------------------------------

(defn- poses-close? [as bs]
  (and (= (count as) (count bs))
       (every? (fn [[a b]]
                 (and (= (set (keys a)) (set (keys b)))
                      (every? (fn [k]
                                (let [x (a k), y (b k)]
                                  (if (number? x) (close? x y) (= x y))))
                              (keys a))))
               (map vector as bs))))

(defspec a-hurried-cue-is-the-same-motion-faster 300
  (prop/for-all [effect (gen/elements effects)
                 ;; a sway runs on the wall clock, whatever the cue's tempo
                 style (gen/elements [:plain :elegant :coarse])
                 intensity (gen/elements [0.3 0.7 1.0])
                 n (gen/choose 1 9)
                 u (gen/choose 0 3000)]
    (let [text (apply str (repeat n "a"))
          unhurried (cue-on text effect intensity 20000)
          hurried (assoc-in unhurried [:cues 0 :tempo] 0.5)
          opts {:style-choice style}
          slow (pose/frame unhurried (+ 20000 (* 2 u)) opts)
          fast (pose/frame hurried (+ 20000 u) opts)]
      (and (schema/valid? :ha/draft hurried)
           (schema/valid? :ha/glyph-frame fast)
           (poses-close? (:poses slow) (:poses fast))))))

(deftest a-hurried-cue-ends-sooner
  (let [unhurried (cue-on "loooove" :swell 0.7 20000)
        hurried (assoc-in unhurried [:cues 0 :tempo] 0.5)
        lifted? (fn [draft t] (some #(not= 0.0 (:y %)) (live (pose/frame draft t {}))))]
    (is (lifted? unhurried 21700))
    (is (lifted? hurried 21500))
    (is (not (lifted? hurried 21700)) "over in half of 3120 ms")
    (is (= (pose/frame unhurried 20500 {})
           (pose/frame (assoc-in unhurried [:cues 0 :tempo] 1) 20500 {}))
        "a tempo of 1 is no tempo")))

(deftest fills-reach-the-poses
  (let [draft (draft-of "abcd" 0 [])
        f (pose/frame draft 9000 {:fills {0 1.0, 1 0.4, 2 0.0}})]
    (is (schema/valid? :ha/glyph-frame f))
    (is (= [nil 0.4 0.0 nil] (mapv :fill (live f)))
        "a glyph that is lit, or that the map leaves out, has no :fill")
    (is (= (pose/frame draft 9000 {})
           (update f :poses (fn [ps] (mapv #(dissoc % :fill) ps))))
        "and nothing else changes")
    (is (= (pose/frame draft 9000 {}) (pose/frame draft 9000 {:fills nil})))
    (is (= (pose/frame draft 9000 {}) (pose/frame draft 9000 {:fills {}}))))
  (testing "a cue's own fill is multiplied by the head's"
    (let [draft (cue-on "a" :draw 1 5000)
          drawn (:fill (first (live (pose/frame draft 5250 {}))))
          f (pose/frame draft 5250 {:fills {0 0.5}})]
      (is (< 0 drawn 1))
      (is (schema/valid? :ha/glyph-frame f))
      (is (close? (* 0.5 drawn) (:fill (first (live f)))))
      (is (= drawn (:fill (first (live (pose/frame draft 5250 {:fills {0 1.0}}))))))))
  (testing "embers are not inked"
    (let [draft (assoc (draft-of "ab" 0 [])
                       :ghosts [{:id 0 :char "x" :died-ms 8900 :index 0}]
                       :glyphs [{:id 1 :char "a" :born-ms 0} {:id 2 :char "b" :born-ms 0}])
          f (pose/frame draft 9000 {:fills {0 0.0 1 0.0 2 0.0}})]
      (is (= [0.0 0.0] (mapv :fill (live f))))
      (is (= [nil] (mapv :fill (ghosts f)))))))

(defspec fills-are-ignored-with-reduced-motion 200
  (prop/for-all [[draft opts dt] gen-case
                 v (gen/elements [0.0 0.3 1.0])]
    (let [fills (into {} (map (fn [g] [(:id g) v])) (:glyphs draft))
          still (assoc opts :reduced-motion? true)
          f (pose/frame draft dt (assoc still :fills fills))
          moving (pose/frame draft dt (assoc opts :fills fills))]
      (and (= f (pose/frame draft dt still))
           (not-any? #(contains? % :fill) (:poses f))
           (schema/valid? :ha/glyph-frame moving)
           (if (< v 1)
             (every? #(<= 0.0 (:fill % 1.0) v) (live moving))
             (= moving (pose/frame draft dt opts)))))))
