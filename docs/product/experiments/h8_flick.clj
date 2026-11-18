;; H8 in docs/product/lab-notebook.md, as measured on 2026-11-18 against
;; order 0053's lab.flick: on ordinary lines, can the flick find pairs a
;; person can tell apart, and does a careful chooser's point then move
;; toward their taste? A dated record, not a test: it reads the learner's
;; state map directly and prototypes the two variants that order 0059
;; then built. Run from the repository root:
;;   bb docs/product/experiments/h8_flick.clj
(require '[halfahead.lab.flick :as flick]
         '[halfahead.lab.learn :as learn]
         '[halfahead.lab.reading :as reading]
         '[halfahead.lab.style :as style])

(def lines
  ["ok" "Sounds good." "I loooove this (honestly). But maybe not. Ship it!"
   "Fine. But maybe not." "We shipped it." "WHAT A DAY!"
   "Can you look at the deploy logs when you get a minute?"
   "The build is green again, finally."
   "That is *the one thing* that matters (trust me)."
   "Hmm... I don't know, maybe?" "No. Absolutely not. Never again."
   "sooo tired" "Thanks!"
   "I think the cache is stale, but I could be wrong."
   "It works on my machine (of course it does)."
   "First we plan, then we build, then we ship." "Wait. What?"
   "This is the best release we have ever done." "lol"
   "Could we move the meeting to three?"
   "The tests pass, the linter is happy, and the demo is at noon."
   "Nooo, not the database again!" "Honestly? I'd rewrite it." "Merged."
   "It's fine. Really. It's fine." "We did it, we actually did it!"
   "Let me think about that and get back to you tomorrow."
   "DO NOT DEPLOY ON FRIDAY."
   "A small thing: the button is two pixels off."
   "I was wrong about the queue. You were right."])

(def pace {:wpm 250})
(def axes [:energy :flourish :softness :warmth])

;; the variant: before skipping an axis, try it the other way
(defn- plan-at [text point] (reading/plan text point (style/from-point point) pace))

(defn offer-both [state text]
  (when-let [plan-a (plan-at text (:point state))]
    (some (fn [s]
            (some (fn [s]
                    (let [{:keys [axis a b]} (learn/pair s)
                          plan-b (plan-at text b)]
                      (when (flick/distinct? plan-a plan-b)
                        {:state s :axis axis :a plan-a :b plan-b :point-a a :point-b b})))
                  [s (update-in s [:dir (:axis s)] -)]))
          (take 4 (iterate learn/skip state)))))

(defn offer-built [state text] (flick/offer state text pace))

;; variant 2: the other way before skipping, and an axis whose step has
;; fallen below 1/16 is finished
(defn offer-floor [state text]
  (when-let [plan-a (plan-at text (:point state))]
    (some (fn [s]
            (when (>= (get-in s [:step (:axis s)]) 0.0625)
              (some (fn [s]
                      (let [{:keys [axis a b]} (learn/pair s)
                            plan-b (plan-at text b)]
                        (when (flick/distinct? plan-a plan-b)
                          {:state s :axis axis :a plan-a :b plan-b :point-a a :point-b b})))
                    [s (update-in s [:dir (:axis s)] -)])))
          (take 4 (iterate learn/skip state)))))

(defn always-a [offer text]
  (loop [state (learn/start) n 0]
    (let [o (when (< n 60) (offer state text))]
      (if-not o n (recur (flick/decide o :a) (inc n))))))

;; a tiny generator, so the run repeats
(defn- lcg [g] (mod (* 16807 g) 2147483647))
(defn- draws [seed n]
  (->> (iterate lcg (+ 1 seed)) (drop 5) (take n) (map #(/ (double %) 2147483647))))

(defn exam
  "One simulated writer on one line: flicks until nothing is offered (or 40)."
  [offer text hidden]
  (loop [state (learn/start) offered #{} n 0]
    (let [o (when (< n 40) (offer state text))]
      (if-not o
        {:flicks n :axes offered :point (:point state)}
        (let [nearer (if (< (learn/distance (:point-b o) hidden)
                            (learn/distance (:point-a o) hidden)) :b :a)]
          (recur (flick/decide o nearer) (conj offered (:axis o)) (inc n)))))))

(defn- median [xs] (let [v (vec (sort xs))] (nth v (quot (count v) 2))))

(defn report [label offer]
  (let [readers (for [i (range 20)] (zipmap axes (draws (* 7919 (inc i)) 4)))
        first-offers (map #(offer (learn/start) %) lines)
        runs (for [text lines hidden readers] (assoc (exam offer text hidden) :text text :hidden hidden))
        per-axis-err (fn [axis] (median (map #(Math/abs (- (axis (:point %)) (axis (:hidden %)))) runs)))
        start-err (fn [axis] (median (map #(Math/abs (- 0.5 (axis %))) readers)))]
    (println "==" label)
    (println "lines with a first exam:" (count (remove nil? first-offers)) "of" (count lines))
    (println "first axis asked:" (frequencies (map :axis first-offers)))
    (println "lines with nothing to ask:" (pr-str (keep (fn [[t o]] (when-not o t)) (map vector lines first-offers))))
    (println "flicks until nothing more to ask: median" (median (map :flicks runs))
             "min" (apply min (map :flicks runs)) "max" (apply max (map :flicks runs)))
    (println "axes ever asked, share of runs:"
             (into {} (for [a axes] [a (/ (Math/round (* 100.0 (/ (count (filter #(contains? (:axes %) a) runs)) (double (count runs))))) 100.0)])))
    (println "distance to taste: start" (format "%.3f" (median (map #(learn/distance {:energy 0.5 :flourish 0.5 :softness 0.5 :warmth 0.5} (:hidden %)) runs)))
             "end" (format "%.3f" (median (map #(learn/distance (:point %) (:hidden %)) runs))))
    (println "median error per axis, start -> end:"
             (into {} (for [a axes] [a [(format "%.2f" (start-err a)) (format "%.2f" (per-axis-err a))]])))))

(report "as built (0053)" offer-built)
(report "variant: try the axis the other way before skipping it" offer-both)

(report "variant 2: the other way, and an axis finishes below a step of 1/16" offer-floor)
(println "always-A chooser, flicks until nothing more to ask (max over the 30 lines):"
         "as built" (apply max (map #(always-a offer-built %) lines))
         "| other way" (apply max (map #(always-a offer-both %) lines))
         "| other way + floor" (apply max (map #(always-a offer-floor %) lines)))

(defn chooser-run [offer text pick]
  (loop [state (learn/start) n 0]
    (let [o (when (< n 80) (offer state text))]
      (if-not o n (recur (flick/decide o (pick o)) (inc n))))))
(def lower-energy #(if (< (:energy (:point-b %)) (:energy (:point-a %))) :b :a))
(def higher-energy #(if (> (:energy (:point-b %)) (:energy (:point-a %))) :b :a))
(def always-b (constantly :b))
(println "other way + floor, max flicks over the 30 lines:"
         "lower energy" (apply max (map #(chooser-run offer-floor % lower-energy) lines))
         "| higher energy" (apply max (map #(chooser-run offer-floor % higher-energy) lines))
         "| always B" (apply max (map #(chooser-run offer-floor % always-b) lines)))
