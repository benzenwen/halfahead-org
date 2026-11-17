(ns halfahead.lab.reading
  "What reading a draft back asks of the frame (docs/product/score.md, 'The
  clock'): the text's score as the style in force performs it, laid out in
  time for one reader by lab.pace at the tempo lab.pose will play it with, then turned into what lab.pose draws:
  a cue for each mark as the reading head reaches it, and how much of
  each glyph the head has inked so far. A read-back is asked for and can
  be stopped, so it may last up to 30 s where a performance that starts by
  itself gets 5. Offsets are in UTF-16 units, a glyph's being the lengths
  of the glyphs before it. Pure, the same on both hosts."
  (:require [halfahead.lab.gestures :as gestures]
            [halfahead.lab.marks :as marks]
            [halfahead.lab.pace :as pace]
            [halfahead.lab.score :as score]
            [halfahead.lab.synth :as synth]))

(def ^:private cap-ms 30000)

(def ^:private word-shown
  "The most units of a mark's text a cue carries (the schema's limit)."
  64)

(defn natural-ms
  "How long the gesture of effect lasts, unhurried, over n letters, played
  with the motion parameters params (lab.style's params of a named style,
  or from-point of a point: whichever lab.pose will play it with). In whole
  ms, rounded up, so a gesture granted this long is never cut short."
  [params n effect]
  (long (Math/ceil (* (synth/total-ms (gestures/patch effect) n)
                      (double (:tempo params))))))

(defn- span-length [{[from to] :span}] (- to from))

(defn plan
  "{:score s :reading r :performing [{:mark i :effect e :intensity k} ...]
  :params params} for reading text back to a reader at this :ha/pace, in
  the style at point (which chooses the gestures and the dress) played
  with the motion parameters params (which set how long a gesture lasts:
  a named style's own, not its point's, differ); nil for text that is not
  a message.

  s is the text's score with only the marks the style performs (a spare
  style passes over a faint mark, and a mark that is not performed does
  not hold the head either); r is its :ha/reading, each mark's gesture
  given its natural length over the mark's letters where the 30 s cap
  allows; :performing names the gesture of each of r's entries, in their
  order, i being the mark's index in s."
  [text point params pace]
  (when-let [s (score/from-text text point)]
    (let [gesture #(marks/gesture-for point %)
          s (update s :marks #(filterv gesture %))
          r (pace/reading s pace
                          {:cap-ms cap-ms
                           :ms-of #(natural-ms params (span-length %) (:effect (gesture %)))})]
      {:score s
       :reading r
       :params params
       :performing (mapv (fn [{:keys [mark]}]
                           (assoc (gesture ((:marks s) mark)) :mark mark))
                         (:entries r))})))

(defn- offsets
  "[[id from to] ...]: where each glyph's character lies in the text."
  [glyphs]
  (second (reduce (fn [[at out] {:keys [id char]}]
                    (let [to (+ at (count char))]
                      [to (conj out [id at to])]))
                  [0 []]
                  glyphs)))

(defn- placed
  "[{:entry e :gesture g :glyph-ids ids} ...] for the entries of the plan's
  reading with a glyph to move: the glyphs whose characters lie in the
  mark's span."
  [{:keys [score reading performing]} glyphs]
  (let [at (offsets glyphs)]
    (for [[entry gesture] (map vector (:entries reading) performing)
          :let [[from to] (:span ((:marks score) (:mark entry)))
                ids (into [] (keep (fn [[id a b]] (when (and (<= from a) (<= b to)) id))) at)]
          :when (seq ids)]
      {:entry entry :gesture gesture :glyph-ids ids})))

(defn cues
  "The :ha/placed-cue of each mark the plan's reading performs, for a
  reading begun at t0 over glyphs (the draft's, whose characters are the
  plan's text): on the glyphs in the mark's span, when the head reaches
  it, with a :tempo below 1 when the reading grants the gesture less than
  its natural length over those glyphs."
  [{:keys [score params] :as plan} glyphs t0]
  (let [{:keys [text]} score]
    (mapv (fn [{:keys [entry gesture glyph-ids]}]
            (let [{:keys [mark at-ms ms]} entry
                  {:keys [effect intensity]} gesture
                  [from to] (:span ((:marks score) mark))
                  natural (natural-ms params (count glyph-ids) effect)]
              (cond-> {:glyph-ids glyph-ids
                       :cue {:word (subs text from (min to (+ from word-shown)))
                             :effect effect
                             :intensity intensity
                             :source :score}
                       :at-ms (+ t0 at-ms)}
                (< ms natural) (assoc :tempo (/ ms (double natural))))))
          (placed plan glyphs))))

(defn- stops-ms
  "How long the head takes to cross the text; nil without stops."
  [{:keys [stops]}]
  (when-let [{:keys [at-ms ms]} (peek stops)]
    (+ at-ms ms)))

(defn fills
  "{glyph-id fill} at t for a paced reading begun at t0, while the head is
  crossing the text: 1 for the glyphs it has passed, 0 for those it has
  not reached, and the share of the one it is in. nil before the reading,
  once the head has crossed, and for a reading with no stops (a long
  message is not dimmed, it only shows its highlights)."
  [{:keys [reading]} glyphs t0 t]
  (when-let [total (stops-ms reading)]
    (when (and (<= t0 t) (< t (+ t0 total)))
      (let [h (pace/head reading (- t t0))]
        (into {}
              (map (fn [[id from to]]
                     [id (max 0.0 (min 1.0 (/ (- h from) (double (- to from)))))]))
              (offsets glyphs))))))

(defn over?
  "Whether a reading begun at t0 is over at t: the head has crossed the
  text and every cue has had the time it was granted."
  [{:keys [reading] :as plan} glyphs t0 t]
  (<= (reduce max (or (stops-ms reading) 0)
              (map (fn [{{:keys [at-ms ms]} :entry}] (+ at-ms ms)) (placed plan glyphs)))
      (- t t0)))
