(ns halfahead.lab.specimen
  "A type specimen as a glyph frame: rows of the word 'Halfahead', each row
  one channel of :ha/glyph-pose held at fixed values and everything else at
  rest, so how a renderer draws weight, focus, slant, stretch, fill and ink
  can be judged by eye and measured in a still picture instead of in
  passing frames. Pure, and the same frame for the same opts."
  (:require [halfahead.lab.pose :as pose]))

(def ^:private word "Halfahead")

(def ^:private rows
  "Top to bottom. :fill wipes across each quad, so only a single letter
  shows it as one wipe; the other rows show as much of the word as fits."
  [{:row :weight :values [-1.0 -0.5 0.0 0.5 1.0]}
   {:row :blur :values [0.0 0.33 0.66 1.0]}
   {:row :skew :values [-0.3 0.0 0.3]}
   ;; the first is at rest, to compare the other two with
   {:row :stretch :values [{:sx 1.0 :sy 1.0} {:sx 1.4 :sy 1.0} {:sx 1.0 :sy 1.4}]}
   {:row :fill :values [0.0 0.25 0.5 0.75 1.0] :letters 1}
   {:row :ink :values [:text :signal :gold :cool :muted]}])

(def ^:private rest-values
  {:sx 1.0 :sy 1.0 :skew 0.0 :weight 0.0 :blur 0.0 :fill 1.0 :ink :text})

(def ^:private gap
  "Between two samples of a row: wide enough that a stretched or slanted
  sample's quads stay clear of its neighbour's."
  "   ")

(def ^:private row-em
  "From one row's baseline to the next: room for a sample 1.4 times as tall."
  1.9)

(def ^:private fov-deg 40.0)

(def ^:private fill-share
  "The most of the view's width or height the rows span, which keeps them
  clear of the page's bar above and its controls below."
  0.7)

(defn- off-rest
  "The pose keys of a row's value, without those at their rest value."
  [row value]
  (into {}
        (remove (fn [[k v]] (let [r (rest-values k)] (if (number? v) (== v r) (= v r)))))
        (case row
          :stretch value
          :ink {:ink value}
          {row value})))

(defn- width [advance s]
  (reduce + 0.0 (map #(double (advance (str %))) s)))

(defn- sample-text
  "The most of the word, at most letters of it, that lets n samples and
  their gaps fit in width-em; one letter at least."
  [advance width-em n letters]
  (let [fits? (fn [k] (<= (+ (* n (width advance (subs word 0 k)))
                             (* (dec n) (width advance gap)))
                          width-em))]
    (subs word 0 (or (last (filter fits? (range 1 (inc (min letters (count word)))))) 1))))

(defn- laid-out
  "Every row with its glyphs placed: [{:row :samples [{:value :glyph-ids}]
  :glyphs [{:id :char :x :y :channels}]}], ids counting up through the
  rows. A gap's spaces are glyphs at rest in no sample."
  [{:keys [advance width-em] :or {advance pose/default-advance width-em 40}}]
  (first
   (reduce
    (fn [[out id0] [n {:keys [row values letters]}]]
      (let [text (sample-text advance width-em (count values) (or letters (count word)))
            step (+ (count text) (count gap))
            chars (mapv str (apply str (interpose gap (repeat (count values) text))))
            ;; one line, however long: sample-text already made it fit
            places (pose/layout chars advance 1.0e9)
            sample-of (fn [i] (when (< (rem i step) (count text)) (quot i step)))
            glyphs (mapv (fn [i c {:keys [x]}]
                           {:id (+ id0 i) :char c :x x :y (* -1.0 row-em n)
                            :channels (if-let [s (sample-of i)] (off-rest row (nth values s)) {})})
                         (range) chars places)
            samples (mapv (fn [s value]
                            {:value value
                             :glyph-ids (mapv #(+ id0 (* s step) %) (range (count text)))})
                          (range) values)]
        [(conj out {:row row :samples samples :glyphs glyphs
                    :width (width advance (apply str chars))})
         (+ id0 (count chars))]))
    [[] 0]
    (map-indexed vector rows))))

(defn- camera
  "Straight on, centred on the rows, as near as keeps them within
  fill-share of the view both ways for aspect (width / height)."
  [laid aspect]
  (let [;; a letter's quad reaches about 0.3 em past its row on each side, an
        ;; em and a bit above the first baseline and half an em below the last
        w (+ (apply max (map :width laid)) 0.6)
        top 1.3
        bottom (- (* -1.0 row-em (dec (count laid))) 0.5)
        tan-half (Math/tan (* 0.5 fov-deg (/ Math/PI 180.0)))
        dist (/ (max (/ w (double aspect)) (- top bottom)) (* fill-share 2.0 tan-half))
        tx (- (/ w 2.0) 0.3)
        ty (/ (+ top bottom) 2.0)]
    {:x tx :y ty :z dist :tx tx :ty ty :tz 0.0 :fov fov-deg}))

(defn samples
  "Where each sample of (frame opts) is: [{:row kw :value v :glyph-ids
  [..]}], rows top to bottom and values left to right. A value is the
  channel's number, the ink's keyword, or for :stretch a map of :sx and
  :sy."
  [opts]
  (into []
        (mapcat (fn [{:keys [row samples]}] (map #(assoc % :row row) samples)))
        (laid-out opts)))

(defn frame
  "The specimen as a valid :ha/glyph-frame. opts is {:advance fn :width-em
  n :aspect n :lights 0|1}: advance a fn char -> em width (lab.pose's
  default when absent), width-em the width no row exceeds, aspect the
  viewport's width / height, lights 0 for paper or 1 for the dark stage.
  Every pose is at rest but for its sample's channel; the camera looks
  straight on and fits the rows; the caret stands below the last row."
  [{:keys [aspect lights] :or {aspect 1.6 lights 1} :as opts}]
  (let [laid (laid-out opts)]
    {:t-ms 0
     :poses (into []
                  (mapcat (fn [{:keys [glyphs]}]
                            (map (fn [{:keys [id char x y channels]}]
                                   (merge {:id id :char char :ghost? false
                                           :x x :y y :z 0.0 :rx 0.0 :ry 0.0 :rz 0.0
                                           :scale 1.0 :opacity 1.0 :glow 0.0 :wobble 0.0}
                                          channels))
                                 glyphs)))
                  laid)
     :caret {:x 0.0 :y (* -1.0 row-em (count laid))}
     :camera (camera laid aspect)
     :style :plain
     :reveal {:level 3 :lights lights}}))
