(ns halfahead.lab.board
  "The lab's board (docs/product/score.md, 'How people and agents drive
  it'): an instrument instead of parameters. Six pads say what the phrase
  just written is doing, one is the big moment, and an XY pad is the
  writer's style, energy across and flourish up. Here is everything the
  board decides: which span a pad acts on, what a press adds to the draft,
  how hard a press was from how long it was held, and which press a
  release belongs to, so an abandoned one never plays. Offsets are UTF-16
  units, a glyph's being the lengths of the glyphs before it, as in
  lab.reading. Pure, the same on both hosts: times arrive in the calls."
  (:require [halfahead.lab.marks :as marks]
            [halfahead.lab.style :as style]))

(def pads
  "The board's pads in order: the six marks a hand can place, then BOOM."
  [{:id :stress :label "STRESS" :key "1"}
   {:id :hold   :label "HOLD"   :key "2"}
   {:id :aside  :label "ASIDE"  :key "3"}
   {:id :turn   :label "TURN"   :key "4"}
   {:id :doubt  :label "DOUBT"  :key "5"}
   {:id :land   :label "LAND"   :key "6"}
   {:id :boom   :label "BOOM"   :key "7"}])

;; --- the target ----------------------------------------------------------------

(def ^:private phrase-words
  "The most words of the last phrase a pad acts on."
  12)

(defn- unit [text i] (subs text i (inc i)))

(defn- space? [text i]
  (contains? #{" " "\n" "\t" "\r" "\f" " "} (unit text i)))

(defn- trimmed
  "[from to) without the whitespace at either end; nil when nothing is left."
  [text from to]
  (let [to (loop [to to] (if (and (< from to) (space? text (dec to))) (recur (dec to)) to))
        from (loop [from from] (if (and (< from to) (space? text from)) (recur (inc from)) from))]
    (when (< from to) [from to])))

(defn- sentence-start
  "Where the sentence that ends at end began: after the last newline, or
  the last . ! ? … with whitespace after it, before end. The whitespace is
  what makes it a full stop: '3.5' and 'halfahead.org' stay whole."
  [text end]
  (loop [i (dec end)]
    (cond
      (neg? i) 0
      (= "\n" (unit text i)) (inc i)
      (and (contains? #{"." "!" "?" "…"} (unit text i))
           (< (inc i) end)
           (space? text (inc i))) (inc i)
      :else (recur (dec i)))))

(defn- word-starts
  "Where each whitespace-separated word of [from to) begins."
  [text from to]
  (filterv #(and (not (space? text %))
                 (or (= % from) (space? text (dec %))))
           (range from to)))

(defn- last-phrase [text]
  (when-let [[_ end] (trimmed text 0 (count text))]
    (when-let [[from _] (trimmed text (sentence-start text end) end)]
      (let [starts (word-starts text from end)]
        [(if (> (count starts) phrase-words)
           (nth starts (- (count starts) phrase-words))
           from)
         end]))))

(defn target
  "The [from to) span of text a pad acts on, or nil when there is nothing
  to mark. A selection {:from :to} that holds more than whitespace is the
  target, trimmed of whitespace at both ends. Otherwise it is the last
  phrase: from the end of the previous sentence (after . ! ? … followed
  by whitespace, or a newline) to the end of the text without its trailing
  whitespace, and no more than its last 12 words."
  [text selection]
  (let [n (count text)
        from (max 0 (min n (:from selection 0)))
        to (max 0 (min n (:to selection 0)))]
    (or (trimmed text (min from to) (max from to))
        (last-phrase text))))

;; --- a press -------------------------------------------------------------------

(def ^:private word-shown
  "The most units of the span's text a cue carries (the schema's limit)."
  64)

(defn- in-span
  "The glyphs whose characters lie wholly in [from to), as lab.reading
  maps a mark's span to glyphs."
  [glyphs [from to]]
  (second (reduce (fn [[at out] {:keys [char] :as g}]
                    (let [end (+ at (count char))]
                      [end (cond-> out (and (<= from at) (<= end to)) (conj g))]))
                  [0 []]
                  glyphs)))

(defn- shown
  "The glyphs' text, cut between glyphs to at most 64 units."
  [glyphs]
  (reduce (fn [word {:keys [char]}]
            (if (> (+ (count word) (count char)) word-shown)
              (reduced word)
              (str word char)))
          ""
          glyphs))

(defn- forced-gesture
  "The gesture the style at point performs mark with. A pad is a
  deliberate act, so where the style is too spare to perform the mark it
  gets the gesture the same style would choose with every flourish."
  [point mark]
  (let [point (style/as-point point)]
    (or (marks/gesture-for point mark)
        (marks/gesture-for (assoc point :flourish 1.0) mark))))

(defn press
  "What pressing pad (a pad's :id) adds to the draft at time t, in the
  style at point, on the glyphs of the draft that lie in span: {:cues
  [c]} for a mark pad, one :ha/placed-cue with :source :board whose
  gesture is the one the style performs a mark of that kind and amount
  with (always performed, even by a spare style) and whose :word is the
  span's text, at most 64 units; {:directions [d]} for :boom, a :card of
  energy amount. nil when the span holds no glyph or only whitespace."
  [pad point glyphs span t amount]
  (let [inside (in-span glyphs span)
        ids (mapv :id inside)
        word (shown inside)]
    (when (re-find #"\S" word)
      (if (= :boom pad)
        {:directions [{:glyph-ids ids :shot :card :energy amount :at-ms t :source :rules}]}
        (let [{:keys [effect intensity]} (forced-gesture point {:span span :kind pad :amount amount})]
          {:cues [{:glyph-ids ids
                   :cue {:word word :effect effect :intensity intensity :source :board}
                   :at-ms t}]})))))

;; --- how hard ------------------------------------------------------------------

(def ^:private tap-amount 0.7)
(def ^:private tap-ms
  "A press this short is a tap, however the finger lingered."
  150)
(def ^:private held-ms
  "A press this long is held all the way."
  600)

(defn amount
  "How much a press held for ms means, 0.7 to 1.0: 0.7 for a tap (up to
  150 ms), rising evenly to 1.0 for a pad held 600 ms or longer."
  [ms]
  (let [p (max 0.0 (min 1.0 (/ (- ms tap-ms) (double (- held-ms tap-ms)))))]
    (if (>= p 1.0) 1.0 (+ tap-amount (* p (- 1.0 tap-amount))))))

;; --- which press a release belongs to ------------------------------------------

(def ^:private echo-ms
  "A click this soon after a pad played is that same press again (the
  click a browser makes of a key or pointer release), not a new one."
  400)

(defn- played [holds pad t ms]
  {:holds (assoc holds :played {:pad pad :t t})
   :play {:pad pad :ms ms}})

(defn hold
  "{:holds h :play {:pad id :ms held} or nil}: the pads held down after
  event, and the press it completes, if any. holds is nil at first.
  Events:
    [:pointer-down pad t]  a pointer went down on pad
    [:pointer-up pad t]    and came up on pad: plays it if it went down there
    [:pointer-gone]        it left the pad, was cancelled or came up elsewhere
    [:key-down pad t]      the pad's key went down (a repeat keeps the first time)
    [:key-up pad t]        and came up: plays the pad if this key put it down
    [:key-gone pad]        the pad lost the focus with its key still down
    [:blur]                the window lost the focus: nothing is held
    [:click pad t]         a click with no pointer or key behind it (assistive
                           technology): a tap, unless pad played within 400 ms
  Pointer holds and key holds are kept apart, so a key release never plays
  a pad a pointer put down, and a press abandoned in any way never plays."
  [holds [kind pad t]]
  (let [holds (or holds {})]
    (case kind
      :pointer-down {:holds (assoc holds :pointer {:pad pad :t t}) :play nil}
      :pointer-up (let [{down :pad t0 :t} (:pointer holds)
                        holds (dissoc holds :pointer)]
                    (if (= pad down)
                      (played holds pad t (- t t0))
                      {:holds holds :play nil}))
      :pointer-gone {:holds (dissoc holds :pointer) :play nil}
      :key-down {:holds (update-in holds [:keys pad] #(or % t)) :play nil}
      :key-up (let [t0 (get-in holds [:keys pad])
                    holds (update holds :keys dissoc pad)]
                (if t0
                  (played holds pad t (- t t0))
                  {:holds holds :play nil}))
      :key-gone {:holds (update holds :keys dissoc pad) :play nil}
      :blur {:holds (dissoc holds :pointer :keys) :play nil}
      :click (let [{last-pad :pad last-t :t} (:played holds)]
               (if (and (= pad last-pad) (< (- t last-t) echo-ms))
                 {:holds holds :play nil}
                 (played holds pad t 0))))))

;; --- the XY pad ----------------------------------------------------------------

(defn- unit-clamped [v] (max 0.0 (min 1.0 (double v))))

(defn xy->point
  "point with :energy x and :flourish y, both kept in [0 1]."
  [point x y]
  (assoc point :energy (unit-clamped x) :flourish (unit-clamped y)))

(defn point->xy
  "[x y], where a point sits on the XY pad: energy across, flourish up."
  [{:keys [energy flourish]}]
  [energy flourish])
