(ns halfahead.lab.audio
  "The lab's only Web Audio code: plays a sound from halfahead.lab.sound.
  There is one AudioContext, made (or resumed) by unlock!, which the shell
  calls only from a user gesture; until then play! does nothing. Nothing
  is fetched or recorded: oscillators and one buffer of white noise. Never
  throws to the caller.")

(defonce ^:private audio (atom nil))

(defn unlock!
  "Make the AudioContext, or resume it. Call from a user gesture."
  []
  (try
    (let [ctx (or (:ctx @audio)
                  (let [ctx (js/AudioContext.)
                        master (.createGain ctx)]
                    (.connect master (.-destination ctx))
                    (reset! audio {:ctx ctx :master master})
                    ctx))]
      (when (= "suspended" (.-state ctx))
        (.catch (.resume ctx) (fn [_] nil))))
    (catch :default _ nil)))

(defn- noise-buffer
  "One second of white noise, made once and shared by every :noise voice."
  [^js ctx]
  (or (:noise @audio)
      (let [rate (.-sampleRate ctx)
            buf (.createBuffer ctx 1 rate rate)
            data (.getChannelData buf 0)]
        (dotimes [i rate] (aset data i (dec (* 2 (js/Math.random)))))
        (swap! audio assoc :noise buf)
        buf)))

(defn- source
  "The node a voice starts and stops: an oscillator, or noise through a
  lowpass at the voice's hz."
  [^js ctx {:keys [wave hz detune]}]
  (if (= :noise wave)
    (let [src (.createBufferSource ctx)
          lowpass (.createBiquadFilter ctx)]
      (set! (.-buffer src) (noise-buffer ctx))
      (set! (.-loop src) true)
      (set! (.-type lowpass) "lowpass")
      (set! (.. lowpass -frequency -value) hz)
      (.connect src lowpass)
      [src lowpass])
    (let [osc (.createOscillator ctx)]
      (set! (.-type osc) (name wave))
      (set! (.. osc -frequency -value) hz)
      (set! (.. osc -detune -value) detune)
      [osc osc])))

(defn- play-voice! [^js ctx ^js master {:keys [at-ms ms gain pan] :as voice}]
  (let [[^js src ^js out] (source ctx voice)
        amp (.createGain ctx)
        start (+ (.-currentTime ctx) (/ at-ms 1000))
        end (+ start (/ ms 1000))
        ;; long notes swell in (Elegant's slow attack), ticks are struck
        attack (max 0.003 (min 0.08 (/ ms 5000)))]
    (.setValueAtTime (.-gain amp) 0.0001 start)
    (.linearRampToValueAtTime (.-gain amp) (max 0.0001 gain) (+ start attack))
    (.exponentialRampToValueAtTime (.-gain amp) 0.0001 end)
    (.connect out amp)
    (if (.-createStereoPanner ctx)
      (let [panner (.createStereoPanner ctx)]
        (set! (.. panner -pan -value) pan)
        (.connect amp panner)
        (.connect panner master))
      (.connect amp master))
    (.start src start)
    (.stop src (+ end 0.02))))

(defn play!
  "Play sound now, if unlock! has made the AudioContext; otherwise, or for
  nil, nothing."
  [sound]
  (try
    (when-let [{:keys [ctx master]} (and sound @audio)]
      (run! #(play-voice! ctx master %) (:voices sound)))
    (catch :default _ nil)))
