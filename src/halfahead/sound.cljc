(ns halfahead.sound
  "Sound cues as data: a short synthesized figure per delivery. Pure; the
  browser's halfahead.fx.audio turns the notes into oscillators. Every cue
  is quiet (gain <= 0.15) and over within 600 ms.")

(def ^:private cues
  {:headline {:notes [{:hz 523 :ms 140 :at-ms 0   :gain 0.12}
                      {:hz 784 :ms 260 :at-ms 150 :gain 0.12}]}
   :typed    {:notes [{:hz 1200 :ms 30 :at-ms 0 :gain 0.06}]}
   :aside    {:notes [{:hz 196 :ms 400 :at-ms 0 :gain 0.05}]}
   :credits  {:notes [{:hz 659 :ms 160 :at-ms 0   :gain 0.1}
                      {:hz 523 :ms 160 :at-ms 170 :gain 0.1}
                      {:hz 392 :ms 240 :at-ms 340 :gain 0.1}]}})

(defn cue
  "The cue for a message's delivery, or nil when it has none (:plain, or a
  delivery we do not know)."
  [{:keys [delivery]}]
  (get cues delivery))
