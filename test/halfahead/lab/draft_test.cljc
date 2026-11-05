(ns halfahead.lab.draft-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [halfahead.lab.draft :as draft]
            [halfahead.schema :as schema]))

;; --- helpers ----------------------------------------------------------------

(defn- recorder
  "A cue-fn that records each [word following] it is called with."
  []
  (let [calls (atom [])]
    {:calls calls
     :cue-fn (fn [word following]
               (swap! calls conj [word following])
               {:word (subs word 0 (min 64 (count word)))
                :effect :pop :intensity 0.5 :source :default})}))

(defn- type-out
  "draft after typing text one UTF-16 unit at a time, the k-th at ms k."
  [d text cue-fn]
  (reduce (fn [d k] (draft/edit d (subs text 0 k) k cue-fn))
          d
          (range 1 (inc (count text)))))

(defn- ids [d] (mapv :id (:glyphs d)))

(defn- valid? [d] (nil? (schema/explain :ha/draft d)))

(defn- unit-code [s i]
  #?(:clj (int (.charAt ^String s i)) :cljs (.charCodeAt s i)))

(defn- high? [s i] (<= 0xD800 (unit-code s i) 0xDBFF))
(defn- low? [s i] (<= 0xDC00 (unit-code s i) 0xDFFF))

(defn- whole-glyph?
  "One unit that is not half of a pair, or a high and low surrogate."
  [c]
  (case (count c)
    1 true
    2 (and (high? c 0) (low? c 1))
    false))

(defn- no-pair-split?
  "No glyph ends in a high surrogate whose low half starts the next glyph."
  [glyphs]
  (and (every? (comp whole-glyph? :char) glyphs)
       (every? (fn [[a b]]
                 (let [a (:char a), b (:char b)]
                   (not (and (= 1 (count a)) (high? a 0) (low? b 0)))))
               (partition 2 1 glyphs))))

;; --- generators -------------------------------------------------------------

(def word-tokens ["a" "word" "Hello" "héllo" "don’t" "it's" "well-known" "42"
                  "日本語" "𝒜bc"])

(def gap-tokens [" " "  " "\n" ", " ". " "😀" "👨‍👩‍👧"])

(def gen-token (gen/elements (into word-tokens gap-tokens)))

(def gen-emoji-token
  (gen/elements ["😀" "👨‍👩‍👧" "🎉🎉" "𝒜" "🇯🇵" "a" "ok" " " "\n"]))

(defn- gen-op [gen-token]
  (gen/one-of
   [(gen/fmap (fn [t] [:type t]) gen-token)
    (gen/return [:backspace])
    (gen/fmap (fn [ts] [:paste ts]) (gen/vector gen-token 1 6))
    (gen/fmap (fn [[i j ts]] [:replace i j ts])
              (gen/tuple (gen/choose 0 1000) (gen/choose 0 1000)
                         (gen/vector gen-token 0 3)))]))

(defn- apply-op [text [kind a b ts]]
  (let [n (count text)]
    (case kind
      :type (str text a)
      :backspace (subs text 0 (max 0 (dec n)))
      :paste (str text (str/join a))
      :replace (let [i (quot (* n (min a b)) 1000)
                     j (quot (* n (max a b)) 1000)]
                 (str (subs text 0 i) (str/join ts) (subs text j))))))

(defn- gen-steps
  "A sequence of [text dt-ms] edits, texts built by applying random ops."
  [gen-token]
  (gen/fmap (fn [ops+dts]
              (let [texts (rest (reductions apply-op "" (map first ops+dts)))]
                (mapv vector texts (map second ops+dts))))
            (gen/vector (gen/tuple (gen-op gen-token) (gen/choose 0 2000)) 1 25)))

(defn- run-steps
  "Every draft along the way, starting with the empty one."
  [steps cue-fn]
  (reductions (fn [d [[text now]]] (draft/edit d text now cue-fn))
              (draft/empty-draft)
              (map vector steps (rest (reductions + 0 (map second steps))))))

;; --- properties -------------------------------------------------------------

(defspec edits-keep-text-ids-and-validity 300
  (prop/for-all [steps (gen-steps gen-token)]
    (let [{:keys [cue-fn]} (recorder)
          drafts (run-steps steps cue-fn)]
      (loop [[d & more] drafts, seen #{}]
        (if-not d
          true
          (let [live (ids d)
                dead (mapv :id (:ghosts d))
                seen' (into seen (concat live dead))]
            (and (valid? d)
                 (= (:text d) (apply str (map :char (:glyphs d))))
                 (apply distinct? -1 live)
                 (apply distinct? -1 dead)
                 (not-any? (set live) dead)
                 (every? #(< % (:next-id d)) seen')
                 (no-pair-split? (:glyphs d))
                 (recur more seen'))))))))

(defspec ids-are-never-reused 300
  (prop/for-all [steps (gen-steps gen-token)]
    (let [{:keys [cue-fn]} (recorder)
          drafts (run-steps steps cue-fn)]
      (every? (fn [[a b]]
                (let [old (into #{} (concat (ids a) (map :id (:ghosts a))))
                      new-ids (remove (set (ids a)) (ids b))]
                  (and (not-any? old new-ids)
                       (every? #(<= (:next-id a) %) new-ids))))
              (partition 2 1 drafts)))))

(defn- kept-prefix-count
  "How many of old's glyphs begin text unchanged, judged from the strings
  alone: chars that match, short of leaving a high surrogate without the
  low half that follows it in text."
  [glyphs text]
  (loop [k 0, at 0]
    (let [c (:char (get glyphs k))]
      (if (and c (str/starts-with? (subs text at) c))
        (recur (inc k) (+ at (count c)))
        (if (and (pos? k)
                 (< at (count text))
                 (let [prev (:char (get glyphs (dec k)))]
                   (and (= 1 (count prev)) (high? prev 0) (low? text at))))
          (dec k)
          k)))))

(defspec common-prefix-keeps-identity 300
  (prop/for-all [steps (gen-steps gen-token)
                 [op dt] (gen/tuple (gen-op gen-token) (gen/choose 0 2000))]
    (let [{:keys [cue-fn]} (recorder)
          before (last (run-steps steps cue-fn))
          text (apply-op (:text before) op)
          after (draft/edit before text 100000 cue-fn)
          k (kept-prefix-count (:glyphs before) text)]
      (= (subvec (:glyphs before) 0 k) (subvec (:glyphs after) 0 k)))))

(defspec no-surrogate-pair-is-split 300
  (prop/for-all [steps (gen-steps gen-emoji-token)]
    (let [{:keys [cue-fn]} (recorder)]
      (every? (fn [d] (and (valid? d)
                           (= (:text d) (apply str (map :char (:glyphs d))))
                           (no-pair-split? (:glyphs d))))
              (run-steps steps cue-fn)))))

(defn- glyph-strs
  "s split into whole surrogate pairs and single UTF-16 units."
  [s]
  (map str (re-seq #"[\uD800-\uDBFF][\uDC00-\uDFFF]|." s)))

#?(:cljs (def ^:private letter-or-digit (js/RegExp. "^[\\p{L}\\p{Nd}]$" "u")))

(defn- word-glyph?
  ;; Classified by code point rather than drawn from word-tokens: the edit
  ;; ops cut through surrogate pairs and can join halves of two different
  ;; characters into a new one, which may well be a letter. A lone surrogate
  ;; is never a letter or digit.
  [c]
  (or (= c "'") (= c "’") (= c "-")
      #?(:clj  (Character/isLetterOrDigit (.codePointAt ^String c 0))
         :cljs (.test letter-or-digit c))))

(defn- expected-finished-runs [glyphs]
  (->> (partition-by (comp boolean word-glyph? :char) glyphs)
       (partition-all 2 1)
       (keep (fn [[run nxt]]
               (when (and nxt (word-glyph? (:char (first run))))
                 (mapv :id run))))))

(defspec every-finished-word-is-cued 300
  (prop/for-all [steps (gen-steps gen-token)]
    (let [{:keys [cue-fn calls]} (recorder)
          drafts (doall (run-steps steps cue-fn))]
      (and (every? (fn [d]
                     (let [live (set (ids d))
                           cue-sets (map (comp set :glyph-ids) (:cues d))]
                       (and (every? #(every? live (:glyph-ids %)) (:cues d))
                            (every? (fn [run] (some #(every? % run) cue-sets))
                                    (expected-finished-runs (:glyphs d))))))
                   drafts)
           (every? (fn [[word following]]
                     (and (every? word-glyph? (glyph-strs word))
                          (not (word-glyph? following))))
                   @calls)))))

;; --- examples ---------------------------------------------------------------

(deftest cutting-pairs-makes-a-new-letter
  (testing "D835 from 𝒜 and DE00 from 😀 join into 𝘀 (U+1D600), a letter"
    (let [{:keys [cue-fn calls]} (recorder)
          d (reduce (fn [d text] (draft/edit d text 0 cue-fn))
                    (draft/empty-draft)
                    ["word" "𝒜bc😀word" "𝘀word" "𝘀word "])]
      (is (= [["𝒜bc" "😀"] ["𝘀word" " "]] @calls))
      (is (= ["𝘀" "w" "o" "r" "d" " "] (map :char (:glyphs d))))
      (is (= [[8 0 1 2 3]] (map :glyph-ids (:cues d))))
      (is (valid? d)))))

(deftest typing-hi
  (let [{:keys [cue-fn calls]} (recorder)
        d1 (draft/edit (draft/empty-draft) "h" 10 cue-fn)
        d2 (draft/edit d1 "hi" 20 cue-fn)]
    (is (= [{:id 0 :char "h" :born-ms 10} {:id 1 :char "i" :born-ms 20}]
           (:glyphs d2)))
    (is (= [] @calls (:cues d2)))
    (is (valid? d2))
    (let [d3 (draft/edit d2 "hi " 30 cue-fn)]
      (is (= [["hi" " "]] @calls))
      (is (= [{:glyph-ids [0 1] :at-ms 30
               :cue {:word "hi" :effect :pop :intensity 0.5 :source :default}}]
             (:cues d3)))
      (is (= 3 (:next-id d3)))
      (is (valid? d3)))))

(deftest inserting-a-comma
  (testing "typed: hello was cued at the space, so the comma cues nothing new"
    (let [{:keys [cue-fn calls]} (recorder)
          d (type-out (draft/empty-draft) "hello world" cue-fn)
          _ (is (= [["hello" " "]] @calls))
          d' (draft/edit d "hello, world" 100 cue-fn)]
      (is (= (ids d) (into (subvec (ids d') 0 5) (subvec (ids d') 6))))
      (is (= {:id 11 :char "," :born-ms 100} (nth (:glyphs d') 5)))
      (is (= [["hello" " "]] @calls))
      (is (valid? d'))))
  (testing "with no cue on hello yet, hello is cued with the comma"
    (let [{:keys [cue-fn calls]} (recorder)
          d (-> (draft/empty-draft)
                (draft/edit "hello world" 0 (:cue-fn (recorder)))
                (assoc :cues []))
          d' (draft/edit d "hello, world" 100 cue-fn)]
      (is (= (ids d) (into (subvec (ids d') 0 5) (subvec (ids d') 6))))
      (is (= [["hello" ","]] @calls))
      (is (= [0 1 2 3 4] (:glyph-ids (first (:cues d')))))
      (is (valid? d')))))

(deftest backspacing-cat
  (let [{:keys [cue-fn calls]} (recorder)
        d (type-out (draft/empty-draft) "cat " cue-fn)
        _ (is (= [["cat" " "]] @calls))
        d (draft/edit d "cat" 10 cue-fn)
        d (draft/edit d "ca" 20 cue-fn)]
    (is (= "ca" (apply str (map :char (:glyphs d)))))
    (is (some #{{:id 2 :char "t" :died-ms 20 :index 2}} (:ghosts d)))
    (is (= [[0 1]] (map :glyph-ids (:cues d))))
    (is (= [["cat" " "]] @calls))
    (is (valid? d))
    (testing "backspacing everything drops the cue"
      (let [d (-> d (draft/edit "c" 30 cue-fn) (draft/edit "" 40 cue-fn))]
        (is (= [] (:glyphs d) (:cues d)))
        (is (valid? d))))))

(deftest pasting-three-words
  (let [{:keys [cue-fn calls]} (recorder)
        d (draft/edit (draft/empty-draft) "one two three " 0 cue-fn)]
    (is (= [["one" " "] ["two" " "] ["three" " "]] @calls))
    (is (= [[0 1 2] [4 5 6] [8 9 10 11 12]] (map :glyph-ids (:cues d))))
    (is (valid? d))))

(deftest ghosts-linger-for-1500-ms
  (let [{:keys [cue-fn calls]} (recorder)
        d (-> (draft/empty-draft) (draft/edit "ab" 0 cue-fn) (draft/edit "a" 1000 cue-fn))]
    (testing "an unchanged text only prunes"
      (is (= d (draft/edit d "a" 2500 cue-fn)))
      (is (= (assoc d :ghosts []) (draft/edit d "a" 2501 cue-fn)))
      (is (= [] @calls)))
    (testing "prune between edits"
      (is (= 1 (count (:ghosts (draft/prune d 2500)))))
      (is (= [] (:ghosts (draft/prune d 2501)))))
    (testing "an edit prunes old ghosts and keeps new ones"
      (let [d' (draft/edit d "" 2501 cue-fn)]
        (is (= [{:id 0 :char "a" :died-ms 2501 :index 0}] (:ghosts d')))
        (is (valid? d'))))))

(deftest word-grammar
  (letfn [(calls-for [text]
            (let [{:keys [cue-fn calls]} (recorder)]
              (draft/edit (draft/empty-draft) text 0 cue-fn)
              @calls))]
    (is (= [["Hello" " "]] (calls-for "Hello ")) "case is preserved")
    (is (= [["don’t-stop" "!"] ["it's" " "]] (calls-for "don’t-stop! it's ")))
    (is (= [["hi" "\n"]] (calls-for "hi\nthere")) "an unfinished last word waits")
    (is (= [["route" " "] ["66" "."]] (calls-for "route 66.")))
    (is (= [["𝒜bc" "😀"]] (calls-for "𝒜bc😀")) "pairs are letters or not, whole")))

(deftest surrogate-pairs-stay-whole
  (let [{:keys [cue-fn]} (recorder)
        half (subs "😀" 0 1)
        d (draft/edit (draft/empty-draft) (str "a" half) 0 cue-fn)
        d' (draft/edit d "a😀" 10 cue-fn)]
    (is (= ["a" half] (map :char (:glyphs d))))
    (is (= ["a" "😀"] (map :char (:glyphs d'))))
    (is (= 0 (:id (first (:glyphs d')))))
    (is (= [{:id 1 :char half :died-ms 10 :index 1}] (:ghosts d')))
    (is (valid? d'))))

(deftest an-invalid-cue-throws
  (is (= :ha.lab/invalid-cue
         (try (draft/edit (draft/empty-draft) "hi " 0 (constantly {}))
              nil
              (catch #?(:clj Exception :cljs :default) e
                (:type (ex-data e)))))))
