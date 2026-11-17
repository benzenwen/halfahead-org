(ns halfahead.lab.ink
  "The closed palette (:ha/ink) as colours: one for paper (house lights
  up, :lights 0) and one for the dark stage (:lights 1), each chosen to
  keep at least 4.5:1 contrast against its background and to be told
  apart from the other four. Nobody authors a colour; a renderer asks
  here. Colours are [r g b], sRGB components from 0 to 1.")

(defn- rgb [hex]
  (mapv #(/ (bit-and (bit-shift-right hex %) 0xff) 255.0) [16 8 0]))

(def ^:private palette
  "ink -> [on paper #f6f1e7, on the stage #141210]. :text, :signal and
  :muted are the lab page's own --text, --signal and --muted."
  {:text   [(rgb 0x1d1a16) (rgb 0xefe6d6)]
   :signal [(rgb 0xc2401c) (rgb 0xff7a55)]
   :gold   [(rgb 0x7d5a00) (rgb 0xe8b84a)]
   :cool   [(rgb 0x1f5f8b) (rgb 0x7fb8e6)]
   :muted  [(rgb 0x5a5044) (rgb 0xb9ad9a)]})

(defn colour
  "The colour of ink with the house lights at lights: the paper colour at
  0, the stage colour at 1, a straight mix of the two between."
  [ink lights]
  (if-let [[paper stage] (palette ink)]
    (let [l (double lights)]
      (mapv (fn [p s] (+ p (* l (- s p)))) paper stage))
    (throw (ex-info "No such ink" {:type :lab/unknown-ink :ink ink}))))

(defn- linear [c]
  (if (<= c 0.03928)
    (/ c 12.92)
    (Math/pow (/ (+ c 0.055) 1.055) 2.4)))

(defn- luminance [[r g b]]
  (+ (* 0.2126 (linear r)) (* 0.7152 (linear g)) (* 0.0722 (linear b))))

(defn contrast
  "The WCAG 2 contrast ratio of colours a and b, from 1 (the same
  luminance) to 21 (black on white), whichever is the lighter."
  [a b]
  (let [la (luminance a), lb (luminance b)]
    (/ (+ (max la lb) 0.05) (+ (min la lb) 0.05))))
