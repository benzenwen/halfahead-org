(ns halfahead.lab.sound
  "Experiment 3: sound to calm, to excite, to intrigue. Every word cue and
  every shot can sound, and a sound is data, a few synthesized voices:

    {:voices [{:wave (:sine :triangle :square :noise) :hz n :at-ms n :ms n
               :gain g :detune cents :pan p} ...]}

  An effect gives a figure (a little contour of notes); the style in force
  voices it: Elegant calms, Caffeinated excites, Drunken intrigues, Coarse
  thumps. Every sound is quiet and short, inside `bounds`, which are
  enforced here rather than trusted to the tables. Pure, the same on both
  hosts; halfahead.lab.audio is what plays it.")

(def bounds
  "What every sound stays within: total length, each voice's gain, the
  summed gain of its voices, frequency (for :noise, the filter's cutoff)
  and stereo pan."
  {:max-ms 900 :max-voice-gain 0.12 :max-sum-gain 0.25 :hz [40 4000] :pan [-1 1]})

;; --- figures -----------------------------------------------------------------

(def ^:private figures
  ;; :notes [[semitones-from-the-style's-root at-ms] ...], each :ms long at
  ;; :gain. :pop is the quietest, with one note; :quake, :launch and
  ;; :shimmer have the most notes and the widest contours.
  {:pop     {:notes [[0 0]] :ms 60 :gain 0.03}
   :launch  {:notes [[0 0] [5 50] [12 100] [19 150]] :ms 90 :gain 0.06}
   :shimmer {:notes [[12 0] [19 40] [24 80] [19 120] [24 160]] :ms 70 :gain 0.05}
   :skitter {:notes [[7 0] [5 30] [9 60]] :ms 30 :gain 0.04}
   :quake   {:notes [[-12 0] [-11 40] [-12 80] [-13 120]] :ms 110 :gain 0.07}
   :melt    {:notes [[0 0] [-3 80] [-7 160]] :ms 140 :gain 0.045}
   :rewind  {:notes [[7 0] [4 60] [0 120]] :ms 70 :gain 0.045}
   :fuse    {:notes [[0 0] [7 70]] :ms 100 :gain 0.045}
   :jitter  {:notes [[0 0] [1 25] [0 50] [1 75]] :ms 25 :gain 0.04}
   :glow    {:notes [[0 0] [7 0] [12 0]] :ms 300 :gain 0.04}
   :wobble  {:notes [[0 0] [2 90] [0 180]] :ms 120 :gain 0.045}})

;; --- palettes ----------------------------------------------------------------

(def ^:private palettes
  ;; :root   Hz of semitone 0
  ;; :wave   the oscillator
  ;; :time   multiplies onsets; :length multiplies each note's length
  ;; :level  multiplies gain
  {:plain       {:root 660 :wave :sine :time 1.0 :length 1.0 :level 1.0}
   :elegant     {:root 440 :wave :sine :time 1.5 :length 2.5 :level 0.8}
   :caffeinated {:root 880 :wave :triangle :time 0.6 :length 0.5 :level 0.9}
   :drunken     {:root 330 :wave :sine :time 1.3 :length 1.3 :level 1.0}
   :coarse      {:root 70 :wave :square :time 0.8 :length 1.0 :level 1.0}})

(def ^:private consonant
  "Unison, fourths, fifths and octaves: what Elegant snaps a contour to."
  [-12 -7 -5 0 5 7 12 17 19 24])

(defn- palette [style]
  (or (palettes style)
      (throw (ex-info (str "Unknown style " (pr-str style))
                      {:type :ha/unknown-style :style style}))))

(defn- hz-at [root semitones]
  (* root (Math/pow 2 (/ semitones 12.0))))

(defn- nearest [xs x]
  (apply min-key #(Math/abs (double (- % x))) xs))

(defn- note-voices
  "The voices that play one note of a figure in a style. A note is
  {:semis :at-ms :ms :gain :i} with onset and length already timed, and i
  its place in the figure."
  [style {:keys [root wave]} {:keys [semis at-ms ms gain i]}]
  (let [voice {:wave wave :hz (hz-at root semis) :at-ms at-ms :ms ms
               :gain gain :detune 0 :pan 0}]
    (case style
      :plain [voice]
      :elegant [(assoc voice :hz (hz-at root (nearest consonant semis)))]
      ;; short bright ticks, a little left and right in turn
      :caffeinated [(assoc voice :ms (min ms 60) :pan (if (even? i) -0.2 0.2))]
      ;; a detuned pair beats slowly, a wobble; it comes in a little late
      :drunken (let [late (+ at-ms 40)
                     half (/ gain 2)]
                 [(assoc voice :at-ms late :gain half :detune -18 :pan -0.4)
                  (assoc voice :at-ms late :gain half :detune 18 :pan 0.4)])
      ;; a burst of noise over a low thud
      :coarse [(assoc voice :wave :noise :hz (hz-at 1200 semis) :ms (min ms 50)
                      :gain (* 0.5 gain))
               (assoc voice :hz (hz-at root (/ semis 2)) :gain (* 0.6 gain))])))

(defn- clamp [lo hi x] (max lo (min hi x)))

(defn- clamp-sound
  "voices as a sound within bounds: each cut to end by :max-ms (dropped if
  nothing is left), its gain, hz and pan clamped, then all gains scaled
  down together when their sum is over :max-sum-gain."
  [voices]
  (let [{:keys [max-ms max-voice-gain max-sum-gain] [lo-hz hi-hz] :hz [lo-pan hi-pan] :pan} bounds
        vs (into []
                 (comp (map (fn [{:keys [at-ms ms] :as v}]
                              (let [at (clamp 0 max-ms at-ms)]
                                (assoc v :at-ms at :ms (min ms (- max-ms at))))))
                       (filter #(pos? (:ms %)))
                       (map #(-> %
                                 (update :gain (partial clamp 0 max-voice-gain))
                                 (update :hz (partial clamp lo-hz hi-hz))
                                 (update :pan (partial clamp lo-pan hi-pan)))))
                 voices)
        sum (reduce + (map :gain vs))
        scale (if (> sum max-sum-gain) (/ max-sum-gain sum) 1)]
    {:voices (mapv #(update % :gain * scale) vs)}))

(defn cue-sound
  "The sound for a word cue (anything carrying a :ha/word-effect :effect
  and an :intensity in 0..1, as a :ha/word-cue does) in a :ha/style.
  Intensity raises the level and draws the figure out a little. Plain
  sounds only the figure's first note, as a single soft blip."
  [{:keys [effect intensity]} style]
  (let [{:keys [time length level] :as p} (palette style)
        {:keys [notes ms gain]} (or (figures effect)
                                    (throw (ex-info (str "Unknown effect " (pr-str effect))
                                                    {:type :ha/unknown-effect :effect effect})))
        i (clamp 0 1 (or intensity 0))
        notes (if (= :plain style) (take 1 notes) notes)
        stretch (+ 0.8 (* 0.2 i))]
    (clamp-sound
     (into []
           (comp (map-indexed
                  (fn [n [semis at]]
                    {:semis semis :i n
                     :at-ms (* at time stretch)
                     :ms (* ms length stretch)
                     :gain (* gain level (+ 0.5 (* 0.5 i)))}))
                 (mapcat #(note-voices style p %)))
           notes))))

;; --- shots -------------------------------------------------------------------

(def ^:private thud-wave
  {:plain :sine :elegant :sine :caffeinated :triangle :drunken :sine :coarse :square})

(defn- card-hit
  "A trailer hit: a short noise swell rising in level and brightness, then
  a low thump, all over within 800 ms."
  [style level]
  (let [detune (if (= :drunken style) 20 0)]
    (conj (mapv (fn [k]
                  {:wave :noise :hz (* 800 (inc k)) :at-ms (* 80 k) :ms 90
                   :gain (* level (+ 0.02 (* 0.01 k))) :detune 0 :pan 0})
                (range 4))
          {:wave (thud-wave style) :hz 55 :at-ms 320 :ms 480
           :gain (* level 0.1) :detune detune :pan 0})))

(defn- camera-move
  "Three steps of a tone from root, rising for a push and falling for a
  pull."
  [style root shot level]
  (let [ratios (cond-> [1 1.25 1.5] (= :pull shot) rseq)
        detune (if (= :drunken style) 25 0)]
    (vec (map-indexed (fn [k r]
                        {:wave (thud-wave style) :hz (* (min root 440) r)
                         :at-ms (* 120 k) :ms 140 :gain (* level 0.05)
                         :detune detune :pan 0})
                      ratios))))

(defn shot-sound
  "The sound for a direction (anything carrying a :ha/shot :shot and an
  :energy in 0..1) in a :ha/style, or nil for :line, which stays silent.
  :card is a trailer hit, :push a rising tone, :pull a falling one; energy
  raises the level."
  [{:keys [shot energy]} style]
  (let [{:keys [root]} (palette style)
        level (+ 0.6 (* 0.4 (clamp 0 1 (or energy 0))))]
    (case shot
      :line nil
      :card (clamp-sound (card-hit style level))
      (:push :pull) (clamp-sound (camera-move style root shot level))
      (throw (ex-info (str "Unknown shot " (pr-str shot))
                      {:type :ha/unknown-shot :shot shot})))))

;; --- what the lab plays ------------------------------------------------------

(def log-size
  "How many of the last sounds played the lab keeps for inspection."
  20)

(defn style-in-force
  "The style a sound placed now is voiced in: the one picked, or under
  :auto the one the last frame was performed in (:plain before any frame)."
  [style-choice last-frame]
  (if (= :auto style-choice) (:style last-frame :plain) style-choice))

(defn sounds
  "What to play for newly placed entries of a kind, :cue (word cues) or
  :shot (directions): one entry {:kind :style :sound ...}, tagged with the
  cue's word, effect and intensity or the shot and its energy, for each
  entry that sounds. Nothing when Sound is off, and nothing for a :line."
  [{:keys [sound? style]} kind entries]
  (if-not sound?
    []
    (into []
          (keep (fn [e]
                  (when-let [snd (case kind
                                   :cue (cue-sound e style)
                                   :shot (shot-sound e style))]
                    (merge {:kind kind :style style :sound snd}
                           (select-keys e (case kind
                                            :cue [:word :effect :intensity]
                                            :shot [:shot :energy]))))))
          entries)))

(defn log
  "The log of sounds played, newest last, with played added and only the
  last log-size kept."
  [played-so-far played]
  (vec (take-last log-size (into (vec played-so-far) played))))
