(ns halfahead.lab.pose
  "The seam between the typing lab's draft and whatever draws it: given a
  draft and a time, where every letter stands, how it is turned, how big,
  bright and wobbly it is. Every frame is computed from scratch, never
  simulated, so a renderer may ask for any moment in any order, and every
  motion ends exactly on the layout (births within 400 ms, cues within
  1500 ms, ghosts within 600 ms). Units are em; angles are radians."
  (:require [clojure.string :as str]))

;; --- arithmetic --------------------------------------------------------------
;; Math/* reads the same in Clojure and ClojureScript, so no reader
;; conditionals are needed; args are doubles so the JVM never reflects.

(def ^:private tau (* 2.0 Math/PI))

(defn- sin [x] (Math/sin (double x)))
(defn- cos [x] (Math/cos (double x)))
(defn- exp [x] (Math/exp (double x)))

(defn- clamp [lo hi x] (max lo (min hi x)))

(defn- smoothstep [p] (* p p (- 3.0 (* 2.0 p))))

(defn- phase
  "A fixed angle per glyph id and salt, so jitter is deterministic and the
  same on both hosts. Small integers only: JavaScript has no 64-bit ints."
  [id salt]
  (let [h (mod (+ (* (mod id 65521) 7919) (* salt 104729)) 65521)]
    (* tau (/ h 65521.0))))

;; --- layout ------------------------------------------------------------------

(def ^:private line-height 1.35)

(defn default-advance
  "Every character 0.6 em wide: the fallback before a font is measured."
  [_c]
  0.6)

(defn- line-y [n] (if (zero? n) 0.0 (* -1.0 line-height n)))

(defn- tokens
  "Indexes of chars grouped into words (runs of non-whitespace) and single
  whitespace chars, in order."
  [chars]
  (->> (map-indexed vector chars)
       (partition-by (fn [[i c]] (if (str/blank? c) [:space i] :word)))
       (map #(mapv first %))))

(defn layout
  "One {:x :y} per char, left-aligned from x 0. A newline starts the next
  line; a word that would cross width-em moves whole to the next line, and
  a word wider than width-em breaks wherever it must. Line n has
  y = -1.35 n. advance is a fn char -> em width."
  [chars advance width-em]
  (let [chars (vec chars)
        width (double width-em)
        adv #(double (advance (nth chars %)))]
    (loop [toks (tokens chars), x 0.0, n 0, out (transient (vec (repeat (count chars) nil)))]
      (if-let [[i0 :as tok] (first toks)]
        (let [c (nth chars i0)]
          (cond
            (= "\n" c)
            (recur (rest toks) 0.0 (inc n) (assoc! out i0 {:x x :y (line-y n)}))

            (str/blank? c)
            (let [x' (+ x (adv i0))]
              (recur (rest toks) x' n (assoc! out i0 {:x x :y (line-y n)})))

            :else
            (let [w (reduce + (map adv tok))
                  [x n] (if (and (pos? x) (<= w width) (> (+ x w) width))
                          [0.0 (inc n)]
                          [x n])
                  [x n out] (reduce (fn [[x n out] i]
                                      (let [a (adv i)
                                            [x n] (if (and (pos? x) (> (+ x a) width))
                                                    [0.0 (inc n)]
                                                    [x n])]
                                        [(+ x a) n (assoc! out i {:x x :y (line-y n)})]))
                                    [x n out]
                                    tok)]
              (recur (rest toks) x n out))))
        (persistent! out)))))

(defn- end-position
  "Where the next character would go: after the last char, or at the start
  of the next line when the text ends with a newline."
  [chars positions advance]
  (if-let [{:keys [x y]} (peek positions)]
    (let [c (peek chars)]
      (if (= "\n" c)
        {:x 0.0 :y (- y line-height)}
        {:x (+ x (double (advance c))) :y y}))
    {:x 0.0 :y 0.0}))

;; --- motion ------------------------------------------------------------------
;; Each source of motion returns a delta map to add onto the base pose, or
;; nil when it is not moving at this instant.

(def ^:private birth-ms 400)

(def ^:private spring
  ;; damping 0.517 overshoots by ~15%; omega puts the envelope at ~1% of
  ;; rest by 400 ms, where the cutoff makes it exact.
  (let [zeta 0.517
        omega (/ 4.6 (* zeta birth-ms))]
    {:zeta zeta
     :decay (* zeta omega)
     :omega-d (* omega (Math/sqrt (- 1.0 (* zeta zeta))))
     :ratio (/ zeta (Math/sqrt (- 1.0 (* zeta zeta))))}))

(defn- birth [age]
  (when (< age birth-ms)
    (let [{:keys [decay omega-d ratio]} spring
          s (if (neg? age)
              0.0
              (- 1.0 (* (exp (- (* decay age)))
                        (+ (cos (* omega-d age)) (* ratio (sin (* omega-d age)))))))]
      {:scale (- s 1.0)
       :z (* 0.6 (- 1.0 s))
       :opacity (- (clamp 0.0 1.0 (/ age 90.0)) 1.0)})))

(def ^:private effect-ms
  "How long each effect moves a glyph; none exceeds 1400 ms."
  {:pop 400 :launch 1400 :shimmer 1400 :skitter 900 :quake 1000 :melt 1400
   :rewind 700 :fuse 900 :jitter 1400 :glow 1000 :wobble 1400})

(defn- effect-delta
  "The delta for one effect at local time u (0 <= u < its duration), at
  intensity k, for the glyph at pos of n in the cue."
  [effect u k id pos n]
  (let [d (double (effect-ms effect))
        p (/ u d)
        fade (- 1.0 p)]
    (case effect
      :pop {:scale (* 0.35 k (sin (* Math/PI p)))}

      :launch (if (< u 700)
                (let [q (/ u 700.0)
                      arc (* 4.0 q (- 1.0 q))]
                  {:y (* 1.2 k arc) :z (* 1.5 k arc)})
                (let [e (- 1.0 (/ (- u 700.0) 700.0))]
                  {:wobble (* k e e)}))

      :shimmer {:glow (* 0.8 k fade fade (Math/abs (sin (/ (* tau u) 350.0))))
                :y (* 0.05 k fade (sin (/ (* tau u) 700.0)))}

      :skitter {:x (* 0.12 k fade fade (sin (+ (/ (* tau u) 90.0) (phase id 1))))}

      :quake (let [a (+ (/ (* tau u) 70.0) (phase id 2))]
               {:x (* 0.15 k fade (sin a))
                :y (* 0.15 k fade (cos a))
                :glow (* k (max 0.0 (- 1.0 (/ u 600.0))))})

      :melt (let [droop (if (< u 600)
                          (sin (* 0.5 Math/PI (/ u 600.0)))
                          (let [q (/ (- u 600.0) 800.0)]
                            (* (- 1.0 q) (- 1.0 q) (cos (* 3.0 Math/PI q)))))]
              {:y (* -0.4 k droop) :rx (* 0.6 k droop)})

      :rewind {:rz (* (- tau) k (- 1.0 (smoothstep p)))}

      :fuse {:x (* 0.5 k (- pos (/ (dec n) 2.0)) (- 1.0 (smoothstep p)))}

      :jitter {:x (* 0.04 k fade (sin (+ (/ (* tau u) 60.0) (phase id 3))))
               :y (* 0.04 k fade (sin (+ (/ (* tau u) 75.0) (phase id 4))))}

      :glow {:glow (* 0.9 k (sin (* Math/PI p)))}

      :wobble {:wobble (* k fade fade)})))

(defn- stagger-step
  "25 ms between neighbours, compressed so the last glyph of any cue starts
  within 100 ms: 100 + the longest effect (1400) is the 1500 ms settle."
  [n]
  (if (> n 5) (/ 100.0 (dec n)) 25.0))

(defn- cue-deltas
  "id -> [delta ...] for every glyph a cue is moving at time t."
  [cues t]
  (reduce
   (fn [acc {:keys [glyph-ids cue at-ms]}]
     (let [{:keys [effect intensity]} cue
           n (count glyph-ids)
           step (stagger-step n)
           d (effect-ms effect)]
       (reduce
        (fn [acc [i id]]
          (let [pos (if (= :rewind effect) (- n 1 i) i)
                u (- t (double at-ms) (* step pos))]
            (if (and (>= u 0) (< u d))
              (update acc id (fnil conj []) (effect-delta effect u (double intensity) id i n))
              acc)))
        acc
        ;; a glyph listed twice moves once, at its first position
        (vals (reduce (fn [m [i id]] (if (m id) m (assoc m id [i id])))
                      {} (map-indexed vector glyph-ids))))))
   {}
   cues))

;; --- poses -------------------------------------------------------------------

(defn- base-pose [{:keys [id char]} {:keys [x y]}]
  {:id id :char char :ghost? false
   :x x :y y :z 0.0 :rx 0.0 :ry 0.0 :rz 0.0
   :scale 1.0 :opacity 1.0 :glow 0.0 :wobble 0.0})

(defn- apply-deltas [pose deltas]
  (if (empty? deltas)
    pose
    (-> (apply merge-with + pose deltas)
        (update :scale #(max 0.0 %))
        (update :opacity #(clamp 0.0 1.0 %))
        (update :glow #(clamp 0.0 1.0 %))
        (update :wobble #(clamp 0.0 1.0 %)))))

(def ^:private ember-ms 600)

(defn- ember [{:keys [id char died-ms index]} t positions end]
  (let [age (- t (double died-ms))]
    (when (and (>= age 0) (< age ember-ms))
      (let [p (/ age ember-ms)
            {:keys [x y]} (get positions index end)]
        {:id id :char char :ghost? true
         :x x :y (+ y (* 1.5 p)) :z (* 0.3 p)
         :rx 0.0 :ry 0.0 :rz (* (if (even? id) 1.0 -1.0) tau p)
         :scale (- 1.0 (* 0.4 p)) :opacity (- 1.0 p) :glow (* 0.5 (- 1.0 p)) :wobble 0.0}))))

(defn frame
  "The :ha/glyph-frame for draft at t-ms: one live pose per glyph, in
  order, then one ember per ghost younger than 600 ms, and the caret.
  opts: {:advance fn :width-em n :reduced-motion? b}; with reduced motion
  every live pose is its resting place and there are no embers."
  [draft t-ms {:keys [advance width-em reduced-motion?]
               :or {advance default-advance width-em 40}}]
  (let [{:keys [glyphs ghosts cues]} draft
        chars (mapv :char glyphs)
        positions (layout chars advance width-em)
        end (end-position chars positions advance)
        bases (mapv base-pose glyphs positions)
        t (double t-ms)
        live (if reduced-motion?
               bases
               (let [moving (cue-deltas cues t)]
                 (mapv (fn [{:keys [id born-ms]} base]
                         (apply-deltas base (into (get moving id [])
                                                  (keep identity)
                                                  [(birth (- t (double born-ms)))])))
                       glyphs bases)))
        embers (when-not reduced-motion?
                 (keep #(ember % t positions end) ghosts))]
    {:t-ms t-ms
     :poses (into live embers)
     :caret end}))
