(ns halfahead.lab.moments-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [halfahead.lab.cues :as cues]
            [halfahead.lab.director :as director]
            [halfahead.lab.draft :as draft]
            [halfahead.lab.moments :as moments]))

;; --- helpers ----------------------------------------------------------------

(defn- edit
  "What the lab's shell does on an edit at level 2 or more: the draft's
  edit, its directions carried over, then the director at the same time."
  [d text t]
  (-> (draft/edit d text t cues/word-cue)
      (assoc :directions (:directions d []))
      (director/direct t)))

(defn- ctx [d t] {:draft d :frame {:t-ms t}})

(defn- gathered
  "Every moment along ctxs, from no tick before the first, ticked in order
  as the shell does once per animation frame."
  [ctxs]
  (into [] (mapcat (fn [[a b]] (moments/moments a b))) (partition 2 1 (cons nil ctxs))))

(defn- typing
  "One ctx per unit of text typed onto d from t0, step ms apart."
  [d text t0 step]
  (rest (reductions (fn [{d :draft} k]
                      (let [t (+ t0 (* step (dec k)))]
                        (ctx (edit d (str (:text d) (subs text (dec k) k)) t) t)))
                    {:draft d}
                    (range 1 (inc (count text))))))

(defn- of-kind [k ms] (filterv #(= k (:kind %)) ms))

;; --- cues -------------------------------------------------------------------

(deftest a-cue-is-one-moment
  (let [ctxs (typing (draft/empty-draft) "crash " 1000 70)
        ms (gathered ctxs)]
    (is (= [{:kind :cue :effect :quake :intensity 1.0 :word "crash" :at-ms 1350}] ms))
    (testing "and asking again about the same tick finds nothing"
      (is (= [] (moments/moments (last ctxs) (last ctxs)))))))

(deftest a-shrunk-or-pruned-cue-is-no-moment
  (let [typed (last (typing (draft/empty-draft) "crash " 0 10))
        shrunk (ctx (edit (:draft typed) "cras " 1000) 1000)
        gone (ctx (edit (:draft shrunk) "" 2000) 2000)
        pruned (ctx (draft/prune (:draft gone) 5000) 5000)]
    (is (= [] (moments/moments typed shrunk)))
    (is (= [] (moments/moments shrunk gone)))
    (is (= [] (moments/moments gone pruned)))
    (testing "a word typed again is a new moment"
      (let [again (typing (:draft pruned) "crash " 6000 10)]
        (is (= [[:cue :quake 6050]]
               (mapv (juxt :kind :effect :at-ms)
                     (mapcat moments/moments (cons pruned again) again))))))))

;; --- shots ------------------------------------------------------------------

(deftest a-card-is-one-shot-moment
  (let [ctxs (typing (draft/empty-draft) "WHAT A DAY! " 0 10)
        shots (of-kind :shot (gathered ctxs))]
    (is (= [{:kind :shot :shot :card :energy 0.9 :at-ms 100}] shots))
    (is (= [] (moments/moments (last ctxs) (last ctxs))))
    (testing "a shot shrunk by a deletion is no moment"
      (let [d (:draft (last ctxs))
            shrunk (ctx (edit d "WHAT A DA! " 1000) 1000)]
        (is (= [] (of-kind :shot (moments/moments (last ctxs) shrunk))))))))

;; --- levels -----------------------------------------------------------------

(defn- at-level [n t] {:draft (draft/empty-draft) :frame {:t-ms t :reveal {:level n :lights 0.0}}})

(deftest each-level-reached-is-one-moment
  (is (= [{:kind :level :level 1 :at-ms 500} {:kind :level :level 2 :at-ms 500}]
         (moments/moments (at-level 0 400) (at-level 2 500))))
  (is (= [] (moments/moments (at-level 2 400) (at-level 2 500))))
  (is (= [] (moments/moments (at-level 3 400) (at-level 1 500))))
  (is (= [] (moments/moments {:draft (draft/empty-draft) :frame {:t-ms 400}} (at-level 3 500))))
  (is (= [] (moments/moments nil (at-level 3 500)))))

;; --- order ------------------------------------------------------------------

(deftest same-ms-is-cue-then-shot-then-level
  (let [d (edit (draft/empty-draft) "WOW! " 700)
        ms (moments/moments (at-level 2 600)
                            {:draft d :frame {:t-ms 700 :reveal {:level 3 :lights 1.0}}})]
    (is (= [:cue :shot :level] (mapv :kind ms)))
    (is (every? #(= 700 (:at-ms %)) ms))))

(deftest moments-are-in-time-order
  (let [old {:draft (draft/empty-draft) :frame {:t-ms 0 :reveal {:level 2 :lights 1.0}}}
        d (-> (draft/empty-draft) (edit "so. " 100) (edit "so. WOW! " 300))
        ms (moments/moments old {:draft d :frame {:t-ms 400 :reveal {:level 3 :lights 1.0}}})]
    (is (= (sort (map :at-ms ms)) (map :at-ms ms)))
    (is (= [100 100 300 300 400] (mapv :at-ms ms)))
    (is (= [:cue :shot :cue :shot :level] (mapv :kind ms)))))

(deftest absent-keys-are-empty
  (is (= [] (moments/moments nil nil)))
  (is (= [] (moments/moments {} {:draft {} :frame {}}))))

;; --- properties -------------------------------------------------------------

(def ^:private gen-step
  "One edit: insert a chunk at a place, or delete a span; and a gap in ms
  (at least 2, so the frame-time tick between edits has a time of its own)."
  (gen/tuple (gen/one-of [(gen/tuple (gen/return :insert)
                                     (gen/fmap str/join
                                               (gen/vector (gen/elements ["a" "B" " " " " "!" "?" "."
                                                                          "crash" "WOW" "why" "deploy"])
                                                           1 6)))
                          (gen/tuple (gen/return :delete) (gen/choose 1 6))])
             gen/nat
             (gen/elements [2 10 90 400 1300 3000 10000])))

(defn- apply-step [{:keys [text] :as d} t [[kind arg] at _]]
  (let [n (count text)
        at (mod at (inc n))]
    (edit d (case kind
              :insert (str (subs text 0 at) arg (subs text at))
              :delete (str (subs text 0 at) (subs text (min n (+ at arg)))))
          t)))

(defn- ticks
  "A ctx after every edit and one between edits (the director run at frame
  time, the ghosts pruned), as the shell's animation frames see them."
  [steps]
  (:out (reduce (fn [{:keys [d t out]} [_ _ gap :as step]]
                  (let [t (+ t gap)
                        d (apply-step d t step)
                        t' (+ t (quot gap 2))
                        later (-> d (draft/prune t') (director/direct t'))]
                    {:d later :t t' :out (conj out (ctx d t) (ctx later t'))}))
                {:d (draft/empty-draft) :t 0 :out []}
                steps)))

(defn- placed-at-own-tick
  "Every cue and direction, as it was when placed: those in each tick's
  draft at that tick's time, since nothing is ever placed in the past."
  [k ctxs]
  (mapcat (fn [{:keys [draft frame]}]
            (filter #(= (:t-ms frame) (:at-ms %)) (get draft k)))
          ctxs))

(defspec every-cue-and-shot-is-one-moment-in-order 200
  (prop/for-all [steps (gen/vector gen-step 1 30)]
    (let [ctxs (ticks steps)
          ms (gathered ctxs)]
      (and (= (mapv (fn [{:keys [cue at-ms]}] [(:effect cue) (:intensity cue) at-ms])
                    (placed-at-own-tick :cues ctxs))
              (mapv (juxt :effect :intensity :at-ms) (of-kind :cue ms)))
           (= (mapv (juxt :shot :energy :at-ms) (placed-at-own-tick :directions ctxs))
              (mapv (juxt :shot :energy :at-ms) (of-kind :shot ms)))
           (apply <= 0 (map :at-ms ms))))))
