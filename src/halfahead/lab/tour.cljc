(ns halfahead.lab.tour
  "The lab's tour: under a minute in which the lab types into its own text
  box, a caption for each scene. The tour is data (script) and a clock
  (plan, due, keys-due); the shell plays each step through the path a
  person's typing takes, so what the tour shows is what typing does. A
  read-back lasts as long as the reader's pace makes it, so the timeline is
  cut into segments at each :until-read and the shell starts the next
  segment's clock when the read-back is over. Pure, the same on both
  hosts.")

(def script
  "The scenes, in order: [{:caption string :steps [step ...]} ...]. A step
  is one of
    {:do :clear}                        empty the text box
    {:do :type :text s :ms-per-key n}   type s, a key every n ms
    {:do :wait :ms n}
    {:do :style :style kw}              pick a named style (:ha/style)
    {:do :reveal :level n}              raise the reveal level to n
    {:do :read-back :wpm n}             start a read-back at n wpm
    {:do :until-read}                   wait until the read-back is over
    {:do :light :over s :ms n}          sweep the pointer's light across
                                        the phrase s over n ms"
  [{:caption "It starts like any chat box."
    :steps [{:do :reveal :level 0}
            {:do :style :style :plain}
            {:do :type :text "Nothing to see here. Just typing." :ms-per-key 70}
            {:do :wait :ms 900}]}
   {:caption "What matters can stay gold."
    :steps [{:do :clear}
            {:do :reveal :level 1}
            {:do :style :style :elegant}
            {:do :type :text "This is *the one thing* that matters (trust me)." :ms-per-key 60}
            {:do :wait :ms 900}
            {:do :light :over "the one thing" :ms 2400}
            {:do :wait :ms 400}]}
   {:caption "Hold a letter, and it holds the note."
    :steps [{:do :clear}
            {:do :type :text "I loooove this " :ms-per-key 110}
            {:do :wait :ms 3200}]}
   {:caption "Then the lights go down. Some words know what they are."
    :steps [{:do :clear}
            {:do :reveal :level 3}
            {:do :style :style :plain}
            {:do :type :text "We shipped it. Finally! " :ms-per-key 90}
            {:do :wait :ms 1800}]}
   {:caption "Ctrl+Enter reads it back, at your pace."
    :steps [{:do :clear}
            {:do :style :style :elegant}
            {:do :type :text "I loooove this (honestly). But maybe not. Ship it!" :ms-per-key 45}
            ;; the typing's own cues settle before the reading performs them
            {:do :wait :ms 5200}
            {:do :read-back :wpm 200}
            {:do :until-read}
            {:do :wait :ms 600}]}
   {:caption "Same words. Another performer."
    :steps [{:do :style :style :caffeinated}
            {:do :wait :ms 400}
            {:do :read-back :wpm 200}
            {:do :until-read}
            {:do :wait :ms 600}]}
   {:caption "And when it deserves it: the whole stage."
    :steps [{:do :clear}
            {:do :style :style :plain}
            {:do :type :text "WHAT A DAY! " :ms-per-key 120}
            {:do :wait :ms 4200}]}
   {:caption "Your turn."
    :steps [{:do :clear}
            {:do :wait :ms 300}]}])

(defn- step-ms
  "How long a step lasts on the tour's clock; :until-read has no fixed
  length and ends its segment instead."
  [{:keys [text ms ms-per-key] :as step}]
  (case (:do step)
    :type (* (count text) ms-per-key)
    (:wait :light) ms
    0))

(defn plan
  "script flattened into a timeline: a vector of segments, each a vector of
  {:at-ms :scene i :step step} (scenes counted from 1), every step
  starting when the one before it ends. An :until-read ends its segment,
  and the next one's times start again at 0."
  [script]
  (->> (for [[i scene] (map-indexed vector script), step (:steps scene)]
         {:scene (inc i) :step step})
       (reduce (fn [{:keys [segments at]} {:keys [step] :as entry}]
                 (let [segments (update segments (dec (count segments)) conj (assoc entry :at-ms at))]
                   (if (= :until-read (:do step))
                     {:segments (conj segments []) :at 0}
                     {:segments segments :at (+ at (step-ms step))})))
               {:segments [[]] :at 0})
       :segments
       (filterv seq)))

(defn due
  "The steps of segment that have begun by t-ms (ms since the segment
  began) and are not in done (a set of indexes), in order: each the plan's
  entry with its :index in the segment, :elapsed-ms since it began, and
  :over? once its length has passed. Whoever plays adds a step to done
  when it is :over?, so a step that lasts is handed out until then and
  never again."
  [segment t-ms done]
  (into []
        (comp (map-indexed (fn [i entry] (assoc entry :index i)))
              (filter (fn [{:keys [index at-ms]}] (and (<= at-ms t-ms) (not (contains? done index)))))
              (map (fn [{:keys [at-ms step] :as entry}]
                     (let [elapsed (- t-ms at-ms)]
                       (assoc entry :elapsed-ms elapsed :over? (>= elapsed (step-ms step)))))))
        segment))

(defn keys-due
  "The prefix of a :type step's text to have been typed elapsed ms after
  the step began: the first key at 0, one more every :ms-per-key, never
  more than the text."
  [{:keys [text ms-per-key]} elapsed]
  (subs text 0 (min (count text) (inc (quot (max 0 elapsed) ms-per-key)))))

(defn caption
  "The caption of scene (counted from 1) of script."
  [script scene]
  (:caption (nth script (dec scene))))

(defn total-ms
  "The tour's length, not counting its read-backs."
  [script]
  (transduce (comp (mapcat :steps) (map step-ms)) + 0 script))
