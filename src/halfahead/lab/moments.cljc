(ns halfahead.lab.moments
  "What happened between two frames, as data a stage can react to: a cue
  placed on a word, a shot placed on a phrase, a reveal level reached.
  The shell asks once per animation frame, comparing the previous tick's
  draft and frame with this tick's, so each happening becomes exactly one
  moment. Pure; the stage behind the letters (lab.stage) gets these and
  nothing else about what the writer did.")

(defn- same-placement?
  "Whether new-p is old-p (a cue or a direction), perhaps shrunk because
  some of its letters were deleted since. A word typed again has fresh
  glyph ids and a fresh time, so it is not. The same rule as lab.debug's."
  [k old-p new-p]
  (and (= (:at-ms old-p) (:at-ms new-p))
       (= (k old-p) (k new-p))
       (every? (set (:glyph-ids old-p)) (:glyph-ids new-p))))

(defn- placed
  "The entries under k (a draft's :cues or :directions) in new-draft that
  were not in old-draft, in draft order; same? matches one to an old one."
  [k same? old-draft new-draft]
  (let [olds (get old-draft k)]
    (remove (fn [p] (some #(same? % p) olds)) (get new-draft k))))

(defn- cue-moment [{:keys [cue at-ms]}]
  {:kind :cue :effect (:effect cue) :intensity (:intensity cue) :word (:word cue) :at-ms at-ms})

(defn- shot-moment [{:keys [shot energy at-ms]}]
  {:kind :shot :shot shot :energy energy :at-ms at-ms})

(defn- level-moments
  "One moment for each level above the old frame's up to the new frame's,
  at the new frame's time; none unless both frames have a :reveal."
  [old-frame new-frame]
  (let [from (get-in old-frame [:reveal :level])
        to (get-in new-frame [:reveal :level])]
    (when (and from to)
      (for [n (range (inc from) (inc to))]
        {:kind :level :level n :at-ms (:t-ms new-frame)}))))

(def ^:private kind-rank {:cue 0 :shot 1 :level 2})

(defn moments
  "The moments between two ticks, each a {:draft :frame} (old-ctx nil
  before the first tick: an empty draft and no frame), in time order, a
  cue before a shot before a level at the same ms:
    {:kind :cue :effect e :intensity i :word w :at-ms t}  a cue placed
    {:kind :shot :shot s :energy e :at-ms t}              a direction placed
    {:kind :level :level n :at-ms t}                      a level reached
  A cue or direction that only lost letters since is not new, so nothing
  is reported twice; a word typed again is new."
  [old-ctx new-ctx]
  (let [{old-draft :draft old-frame :frame} old-ctx
        {new-draft :draft new-frame :frame} new-ctx]
    (->> (concat (map cue-moment (placed :cues (partial same-placement? :cue) old-draft new-draft))
                 (map shot-moment (placed :directions (partial same-placement? :shot) old-draft new-draft))
                 (level-moments old-frame new-frame))
         (sort-by (juxt :at-ms (comp kind-rank :kind)))
         vec)))
