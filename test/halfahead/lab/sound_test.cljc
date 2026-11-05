(ns halfahead.lab.sound-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [halfahead.lab.sound :as sound]
            [halfahead.lab.style :as style]
            [halfahead.schema :as schema]
            [malli.core :as m]))

(def ^:private effects (m/children (m/deref-all (schema/schema :ha/word-effect))))
(def ^:private shots (m/children (m/deref-all (schema/schema :ha/shot))))
(def ^:private waves #{:sine :triangle :square :noise})
(def ^:private eps 1e-9)

(defn- in-bounds?
  "Whether every voice of s, and s as a whole, is within sound/bounds."
  [s]
  (let [{:keys [max-ms max-voice-gain max-sum-gain] [lo-hz hi-hz] :hz [lo-pan hi-pan] :pan} sound/bounds
        vs (:voices s)]
    (and (seq vs)
         (every? (fn [{:keys [wave hz at-ms ms gain detune pan]}]
                   (and (waves wave) (number? detune)
                        (<= 0 at-ms) (pos? ms) (<= (+ at-ms ms) (+ max-ms eps))
                        (<= 0 gain (+ max-voice-gain eps))
                        (<= lo-hz hz hi-hz)
                        (<= lo-pan pan hi-pan)))
                 vs)
         (<= (reduce + (map :gain vs)) (+ max-sum-gain eps)))))

(defn- sum-gain [s] (reduce + (map :gain (:voices s))))

(defn- cue [effect intensity] {:word "w" :effect effect :intensity intensity :source :glossary})

;; --- coverage ----------------------------------------------------------------

(deftest the-vocabularies-are-the-schemas
  (is (= 11 (count effects)))
  (is (= [:line :card :push :pull] shots))
  (is (= (set style/styles) (set (m/children (m/deref-all (schema/schema :ha/style)))))))

(deftest a-sound-for-every-effect-and-style
  (doseq [e effects s style/styles i [0 0.5 1]]
    (is (in-bounds? (sound/cue-sound (cue e i) s)) [e s i])))

(deftest a-sound-for-every-shot-and-style
  (doseq [shot [:card :push :pull] s style/styles e [0 0.9 1]]
    (is (in-bounds? (sound/shot-sound {:shot shot :energy e} s)) [shot s e])))

(deftest line-is-silent
  (doseq [s style/styles e [0 0.3 1]]
    (is (nil? (sound/shot-sound {:shot :line :energy e} s)) [s e])))

;; --- character ---------------------------------------------------------------

(defn- by-onset [s] (sort-by :at-ms (:voices s)))

(deftest card-is-a-trailer-hit
  (doseq [s style/styles]
    (let [{:keys [voices] :as hit} (sound/shot-sound {:shot :card :energy 0.9} s)
          noises (filter #(= :noise (:wave %)) (by-onset hit))]
      (testing s
        (is (some #(and (not= :noise (:wave %)) (< (:hz %) 100)) voices) "a low thump")
        (is (<= 2 (count noises)) "a noise swell")
        (is (apply < (map :gain noises)) "that rises")
        (is (< (reduce max (map #(+ (:at-ms %) (:ms %)) voices)) 900))))))

(deftest push-rises-and-pull-falls
  (doseq [s style/styles]
    (is (apply < (map :hz (by-onset (sound/shot-sound {:shot :push :energy 0.6} s)))) s)
    (is (apply > (map :hz (by-onset (sound/shot-sound {:shot :pull :energy 0.6} s)))) s)))

(deftest pop-and-the-default-cue-are-the-quietest
  (doseq [s style/styles i [0 0.3 0.7 1]
          :let [pop (sum-gain (sound/cue-sound (cue :pop i) s))]
          e effects]
    (is (<= pop (sum-gain (sound/cue-sound (cue e i) s))) [s i e]))
  (testing "the default cue, :pop at 0.3, is quieter than any glossary cue"
    (let [default (sound/cue-sound {:word "we" :effect :pop :intensity 0.3 :source :default} :plain)]
      (is (<= (sum-gain default) (sum-gain (sound/cue-sound (cue :pop 0.4) :plain)))))))

(deftest quake-launch-and-shimmer-are-the-most-characterful
  (doseq [s style/styles i [0 1]
          :let [voices #(count (:voices (sound/cue-sound (cue % i) s)))]
          e [:quake :launch :shimmer]]
    (is (<= (voices :pop) (voices e)) [s e]))
  (testing "beyond plain's single blip, they have more voices than :pop"
    (doseq [s (remove #{:plain} style/styles)
            e [:quake :launch :shimmer]]
      (is (< (count (:voices (sound/cue-sound (cue :pop 1) s)))
             (count (:voices (sound/cue-sound (cue e 1) s))))
          [s e]))))

(deftest the-styles-voice-differently
  (let [voices #(:voices (sound/cue-sound (cue :launch 0.8) %))]
    (is (every? #(= :sine (:wave %)) (voices :elegant)))
    (is (every? #(= :triangle (:wave %)) (voices :caffeinated)))
    (is (every? #(<= (:ms %) 60) (voices :caffeinated)) "short ticks")
    (is (some #(not (zero? (:detune %))) (voices :drunken)) "detuned pairs")
    (is (< (reduce min (map :at-ms (voices :plain)))
           (reduce min (map :at-ms (voices :drunken))))
        "drunken comes in a little late")
    (is (= #{:noise :square} (set (map :wave (voices :coarse)))))
    (is (= 1 (count (voices :plain))) "a single blip")))

(deftest unknown-style-throws
  (doseq [f [#(sound/cue-sound (cue :pop 0.3) :loud)
             #(sound/shot-sound {:shot :line :energy 0} :loud)]]
    (is (= :ha/unknown-style
           (try (f) nil (catch #?(:clj Exception :cljs :default) e (:type (ex-data e)))))))
  (is (= :ha/unknown-effect
         (try (sound/cue-sound (cue :boom 0.3) :plain) nil
              (catch #?(:clj Exception :cljs :default) e (:type (ex-data e))))))
  (is (= :ha/unknown-shot
         (try (sound/shot-sound {:shot :zoom :energy 0.5} :plain) nil
              (catch #?(:clj Exception :cljs :default) e (:type (ex-data e)))))))

;; --- bounds as properties ------------------------------------------------------

(def ^:private gen-unit (gen/double* {:min 0 :max 1 :NaN? false :infinite? false}))

(def ^:private gen-cue
  (gen/let [effect (gen/elements effects)
            intensity gen-unit
            source (gen/elements [:glossary :punctuation :shape :default])
            word (gen/fmap #(apply str "w" %) (gen/vector gen/char-alpha 0 8))]
    {:word word :effect effect :intensity intensity :source source}))

(def ^:private gen-direction
  (gen/let [shot (gen/elements shots)
            energy gen-unit
            at-ms gen/nat
            ids (gen/not-empty (gen/vector gen/nat))]
    {:glyph-ids ids :shot shot :energy energy :at-ms at-ms :source :rules}))

(defspec cue-sounds-in-bounds 200
  (prop/for-all [c gen-cue
                 s (gen/elements style/styles)]
    (and (schema/valid? :ha/word-cue c)
         (in-bounds? (sound/cue-sound c s)))))

(defspec shot-sounds-in-bounds 200
  (prop/for-all [d gen-direction
                 s (gen/elements style/styles)]
    (and (schema/valid? :ha/direction d)
         (let [snd (sound/shot-sound d s)]
           (if (= :line (:shot d)) (nil? snd) (in-bounds? snd))))))

;; --- what the lab plays ------------------------------------------------------

(def ^:private gen-style (gen/elements style/styles))

(defspec sound-off-gives-nothing 100
  (prop/for-all [cs (gen/vector gen-cue 0 6)
                 ds (gen/vector gen-direction 0 6)
                 s gen-style]
    (and (= [] (sound/sounds {:sound? false :style s} :cue cs))
         (= [] (sound/sounds {:sound? false :style s} :shot ds)))))

(defspec sound-on-gives-one-bounded-sound-per-entry-but-lines 100
  (prop/for-all [cs (gen/vector gen-cue 0 6)
                 ds (gen/vector gen-direction 0 6)
                 s gen-style]
    (let [cue-sounds (sound/sounds {:sound? true :style s} :cue cs)
          shot-sounds (sound/sounds {:sound? true :style s} :shot ds)
          sounding (remove #(= :line (:shot %)) ds)]
      (and (= (map :effect cs) (map :effect cue-sounds))
           (= (map :shot sounding) (map :shot shot-sounds))
           (every? #(and (= :cue (:kind %)) (= s (:style %)) (in-bounds? (:sound %))) cue-sounds)
           (every? #(and (= :shot (:kind %)) (= s (:style %)) (in-bounds? (:sound %))) shot-sounds)))))

(deftest lines-alone-are-silent-with-sound-on
  (is (= [] (sound/sounds {:sound? true :style :coarse} :shot
                          [{:glyph-ids [1] :shot :line :energy 1 :at-ms 0 :source :rules}]))))

(deftest the-style-in-force
  (testing "a picked style is used as it is"
    (is (= :drunken (sound/style-in-force :drunken {:style :elegant}))))
  (testing "under auto, the last frame's style"
    (is (= :caffeinated (sound/style-in-force :auto {:style :caffeinated}))))
  (testing "under auto before any frame, plain"
    (is (= :plain (sound/style-in-force :auto nil)))))

(deftest the-log-keeps-the-last-twenty
  (is (= 20 sound/log-size))
  (is (= [1 2 3] (sound/log [1] [2 3])))
  (is (= [] (sound/log [] [])))
  (let [l (reduce sound/log [] (partition-all 7 (range 50)))]
    (is (= (vec (range 30 50)) l)))
  (is (= (vec (range 5 25)) (sound/log (vec (range 0 10)) (range 10 25)))))
