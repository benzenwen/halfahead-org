(ns halfahead.lab.stage.grid
  "The first stage: a receding grid floor under the text, shown from reveal
  level 2 as far as the house lights are down. It reacts to two moments,
  and only by light: a :quake cue brightens its lines for a moment in
  proportion to the cue's intensity (a hint of lightning), and a :card
  shot pulses its far lines once. With no moments it is the plain floor."
  (:require ["three/tsl" :as tsl]
            ["three/webgpu" :as three]))

(def ^:private floor-drop
  "Gap between the line being written and the floor, in em."
  1.2)

(def ^:private flash-ms 900)
(def ^:private flash-gain
  "How much brighter the lines are at a flash of intensity 1."
  6.0)
(def ^:private pulse-ms 700)
(def ^:private pulse-gain 3.0)

(defn- floor-mesh
  "A wide plane under the text, receding into the distance: fine grid lines
  in ink at low alpha (blending is in linear light, so 0.02 shows as about
  8% of the way from the background to ink), anti-aliased by their
  screen-space width, fixed in world space so they never slide as the
  plane follows the block. Fog fades it into the background. visibility
  is a uniform from 0 (no floor) to 1, flash and pulse are 0 at rest, so
  the floor at rest is exactly the floor before stages existed; the pulse
  reaches only lines a few em or more from the block."
  [^js ink ^js visibility ^js flash ^js pulse]
  (let [^js p (.div (.-xz tsl/positionWorld) 2.0)
        ^js cell (.div (tsl/abs (.sub (tsl/fract (.sub p 0.5)) 0.5)) (tsl/fwidth p))
        line (.oneMinus (tsl/min (tsl/min (.-x cell) (.-y cell)) 1.0))
        far (tsl/smoothstep 3.0 9.0 (.length (.-xy tsl/positionLocal)))
        lift (.mul (.add (.mul flash flash-gain) 1.0)
                   (.add (.mul (.mul pulse far) pulse-gain) 1.0))
        ^js m (three/MeshBasicNodeMaterial.)]
    (set! (.-transparent m) true)
    (set! (.-depthWrite m) false)
    (set! (.-colorNode m) (tsl/vec4 ink 1.0))
    (set! (.-opacityNode m) (.mul (.mul (.mul line 0.02) visibility) lift))
    (doto (three/Mesh. (three/PlaneGeometry. 400 400) m)
      (-> .-rotation (.set (- (/ js/Math.PI 2)) 0 0))
      (aset "renderOrder" -1))))

(defn- since
  "How far through a span of ms that began at t0 time t is, from 0 to 1;
  nil when there is no t0 or the span is over."
  [t0 ms t]
  (when t0
    (let [u (/ (- t t0) ms)]
      (when (<= 0 u 1) u))))

(defn- init [^js scene {:keys [ink]}]
  (let [nodes {:visibility (tsl/uniform 0.0) :flash (tsl/uniform 0.0) :pulse (tsl/uniform 0.0)}
        ^js mesh (floor-mesh ink (:visibility nodes) (:flash nodes) (:pulse nodes))]
    (.add scene mesh)
    {:scene scene :mesh mesh :nodes nodes
     :visibility 0 :brightness 0 :pulse 0}))

(defn- moment [state {:keys [kind effect intensity shot at-ms]}]
  (cond
    (and (= :cue kind) (= :quake effect)) (assoc state :flash-at at-ms :flash-intensity intensity)
    (and (= :shot kind) (= :card shot)) (assoc state :pulse-at at-ms)
    :else state))

(defn- update-stage
  "Rests the floor under the camera's target, shows it from level 2 as far
  as the lights are down, and lights its lines for any flash or pulse
  still under way at t; all from times, so nothing accumulates."
  [{:keys [^js mesh nodes flash-at flash-intensity pulse-at] :as state}
   {:keys [camera reveal]} t]
  (let [{:keys [level lights] :or {level 3 lights 1}} reveal
        visibility (if (>= level 2) lights 0)
        brightness (if-let [u (since flash-at flash-ms t)]
                     (* flash-intensity (* (- 1 u) (- 1 u)))
                     0)
        pulse (if-let [u (since pulse-at pulse-ms t)] (js/Math.sin (* js/Math.PI u)) 0)]
    (when-let [{:keys [tx ty]} camera]
      (.set (.-position mesh) tx (- ty floor-drop) 0))
    (set! (.-value ^js (:visibility nodes)) visibility)
    (set! (.-value ^js (:flash nodes)) brightness)
    (set! (.-value ^js (:pulse nodes)) pulse)
    (assoc state :visibility visibility :brightness brightness :pulse pulse)))

(defn- dispose [{:keys [^js scene ^js mesh]}]
  (.remove scene mesh)
  (.dispose (.-geometry mesh))
  (.dispose (.-material mesh))
  nil)

(def stage
  "The grid floor, as a lab.stage."
  {:init init :update update-stage :moment moment :dispose dispose})
