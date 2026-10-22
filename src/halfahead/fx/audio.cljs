(ns halfahead.fx.audio
  "The only Web Audio code: plays a sound cue (see halfahead.sound). The
  AudioContext is made on the first cue, which can only follow the reader
  ticking Sound, a user gesture. Never throws to the caller.")

(defonce ^:private context (atom nil))

(defn- audio-context []
  (or @context
      (reset! context (js/AudioContext.))))

(defn- play-note! [ctx {:keys [hz ms at-ms gain]}]
  (let [osc   (.createOscillator ctx)
        amp   (.createGain ctx)
        start (+ (.-currentTime ctx) (/ at-ms 1000))
        end   (+ start (/ ms 1000))
        peak  (+ start (min 0.01 (/ ms 2000)))]
    (set! (.-value (.-frequency osc)) hz)
    (.setValueAtTime (.-gain amp) 0.0001 start)
    (.linearRampToValueAtTime (.-gain amp) gain peak)
    (.linearRampToValueAtTime (.-gain amp) 0.0001 end)
    (.connect osc amp)
    (.connect amp (.-destination ctx))
    (.start osc start)
    (.stop osc (+ end 0.02))))

(defn play-cue! [{:keys [notes]}]
  (try
    (let [ctx (audio-context)]
      (when (= "suspended" (.-state ctx))
        (.resume ctx))
      (run! #(play-note! ctx %) notes))
    (catch :default _ nil)))
