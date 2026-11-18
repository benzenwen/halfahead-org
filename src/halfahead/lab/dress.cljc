(ns halfahead.lab.dress
  "Dress (docs/product/score.md, 'Dress'): what a mark leaves behind on its
  letters once the motion ends, the way bold does. A :ha/dress names a
  span of the text; here it is placed on the draft's glyphs and turned
  into what a letter at rest looks like: its size, its ink, its weight,
  and for gold a glint that brightens near a light (the pointer), as gold
  leaf caught a candle. Dress is presentation, never text: it adds, drops
  and changes no character. The two asterisks that enclose a dressed
  stress have done their work once it is dressed: they are that dress's
  markers, quietened by whoever draws and never dropped. Offsets are in UTF-16 units, a glyph's being
  the lengths of the glyphs before it. Pure, the same on both hosts.")

(def looks
  "What each dress does to a letter at rest: :size multiplies it, :ink
  names its colour, :weight is added to it, :glint makes it catch the
  light."
  {:versal  {:size 1.8}
   :rubric  {:ink :signal}
   :gilt    {:ink :gold :glint true}
   :strong  {:weight 0.55}
   :whisper {:size 0.85 :ink :muted :weight -0.2}})

(defn- offsets
  "[[id from to] ...]: where each glyph's character lies in the text."
  [glyphs]
  (second (reduce (fn [[at out] {:keys [id char]}]
                    (let [to (+ at (count char))]
                      [to (conj out [id at to])]))
                  [0 []]
                  glyphs)))

(def ^:private stress
  "The dress a stress wears: what asterisks ask for."
  #{:gilt :rubric :strong})

(defn- markers
  "[id-before id-after] when the glyph ending at from and the glyph
  starting at to (at being offsets) are both an asterisk; else nil."
  [chars at from to]
  (let [before (some (fn [[id _ b]] (when (= b from) id)) at)
        after (some (fn [[id a _]] (when (= a to) id)) at)]
    (when (and before after (= "*" (chars before) (chars after)))
      [before after])))

(defn placed
  "[{:as kw :glyph-ids [id ...]} ...]: each :ha/dress of dress on the
  glyphs whose characters lie in its span, in the order given. A dress
  whose span holds no glyph is left out. A :gilt, :rubric or :strong
  dress whose span is enclosed by asterisks (the glyph just before it and
  the glyph just after it are each '*') also has :markers [id-before
  id-after]: the two glyphs that asked for it, which rest whispered while
  it is worn (lab.pose)."
  [dress glyphs]
  (let [at (offsets glyphs)
        chars (into {} (map (juxt :id :char)) glyphs)]
    (into []
          (keep (fn [{:keys [as] [from to] :span}]
                  (let [ids (into [] (keep (fn [[id a b]] (when (and (<= from a) (<= b to)) id))) at)
                        around (when (stress as) (markers chars at from to))]
                    (when (seq ids)
                      (cond-> {:as as :glyph-ids ids}
                        around (assoc :markers around))))))
          dress)))

(defn seen
  "{[as first-glyph-id] since-ms}: when each placed dress was first seen.
  A dress already in prev keeps its time, a new one is seen at t, and
  dress that is gone is forgotten. The only memory dress needs: whoever
  draws keeps it from one frame to the next."
  [prev placed t]
  (into {}
        (map (fn [{:keys [as glyph-ids]}]
               (let [k [as (first glyph-ids)]]
                 [k (get prev k t)])))
        placed))

(defn- further-from-1 [a b]
  (if (> (abs (- b 1)) (abs (- a 1))) b a))

(defn sizes
  "{glyph-id size} for the glyphs (of glyphs) whose placed dress changes
  their size. A glyph in two such dress takes the larger change from 1."
  [placed glyphs]
  (let [present (into #{} (map :id) glyphs)]
    (reduce (fn [m {:keys [as glyph-ids]}]
              (if-let [size (:size (looks as))]
                (reduce (fn [m id]
                          (if (present id)
                            (assoc m id (further-from-1 (get m id 1) size))
                            m))
                        m
                        glyph-ids)
                m))
            {}
            placed)))

(def ^:private glint
  "Gold at rest glows a little by itself (:ambient), and up to :lit more
  beside a light, falling off as a bell :reach em wide."
  {:ambient 0.12 :lit 0.5 :reach 1.2})

(defn- glint-at
  "How much a gilt letter at (x, y) glows with the light where it is (nil:
  no light)."
  [{:keys [x y]} light]
  (let [{:keys [ambient lit reach]} glint]
    (if light
      (let [dx (- x (:x light)), dy (- y (:y light))]
        (+ ambient (* lit (Math/exp (- (/ (+ (* dx dx) (* dy dy))
                                            (* 2.0 reach reach)))))))
      ambient)))

(defn at-rest
  "pose with look (one of looks' values) applied at weight w, 0 (not at
  all) to 1: :ink the look's, with :tint w; :weight w times the look's,
  added to the pose's and absent when that is none; and for :glint, :glow
  raised by w times what the light gives: 0.12 with no light (nil), else
  0.12 + 0.5 e^(-d^2 / (2 x 1.2^2)), d the distance in em from light
  ({:x :y}) to the letter's (x, y). Size is the layout's business, not
  the look of one letter, so it is not applied here."
  [pose {:keys [ink weight] :as look} w light]
  (let [heavier (when weight
                  (max -1.0 (min 1.0 (+ (:weight pose 0.0) (* w weight)))))]
    (cond-> pose
      ink (assoc :ink ink :tint w)
      (and heavier (zero? heavier)) (dissoc :weight)
      (and heavier (not (zero? heavier))) (assoc :weight heavier)
      (:glint look) (update :glow #(min 1.0 (+ % (* w (glint-at pose light))))))))
