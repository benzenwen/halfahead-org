(ns halfahead.lab.stage.light
  "The grid's two light curves, as functions of time alone. They live
  apart from the three.js stage so the curves can be proven without a GPU,
  and so a check of the light can ask what it should be at any t rather
  than hope a frame lands near its peak.")

(def ^:private flash-ms 900)
(def ^:private pulse-ms 700)

(defn- since
  "How far through a span of ms that began at t0 time t is, from 0 to 1;
  nil when there is no t0 or t is outside the span."
  [t0 ms t]
  (when t0
    (let [u (/ (- t t0) ms)]
      (when (<= 0 u 1) u))))

(defn flash
  "A quake's flash at t: intensity at at-ms, falling as (1 - u)^2 to 0 over
  900 ms; 0 with no at-ms, before it, or once the span is over."
  [at-ms intensity t]
  (if-let [u (since at-ms flash-ms t)]
    (* intensity (* (- 1 u) (- 1 u)))
    0))

(defn pulse
  "A card's pulse at t: one arch of sin(pi u) over 700 ms from at-ms; 0
  outside it or with no at-ms."
  [at-ms t]
  (if-let [u (since at-ms pulse-ms t)]
    (Math/sin (* Math/PI u))
    0))
