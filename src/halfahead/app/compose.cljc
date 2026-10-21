(ns halfahead.app.compose
  "Turning what a person typed into a message's text and emphasis. Pure."
  (:require [clojure.string :as str]))

(def ^:private max-spans 8)

(defn- space? [c] (str/blank? (str c)))

(defn- run-end
  "Index of the asterisk closing a run opened at i, or nil when the
  asterisks at i are not a single-asterisk pair around a tight run."
  [s i]
  (let [n (count s)
        j (str/index-of s "*" (inc i))]
    (when (and j
               (< (inc i) j)
               (or (zero? i) (not= \* (nth s (dec i))))
               (not (space? (nth s (inc i))))
               (not (space? (nth s (dec j))))
               (or (= (inc j) n) (not= \* (nth s (inc j)))))
      j)))

(defn parse-emphasis
  "(parse-emphasis s) -> {:text t :emphasis [[start end] ...]}. Each
  *run* between single asterisks becomes a span over the run in t, minus
  its asterisks. Only the first 8 runs count; anything else stays as typed.
  A result that would be blank returns s unchanged."
  [s]
  (let [s (str s)
        n (count s)
        [text spans]
        (loop [i 0, out "", spans []]
          (let [j (when (and (< i n)
                             (< (count spans) max-spans)
                             (= \* (nth s i)))
                    (run-end s i))]
            (cond
              (>= i n) [out spans]
              j        (let [start (count out)
                             run   (subs s (inc i) j)]
                         (recur (inc j) (str out run)
                                (conj spans [start (+ start (count run))])))
              :else    (recur (inc i) (str out (nth s i)) spans))))]
    (if (str/blank? text)
      {:text s :emphasis []}
      {:text text :emphasis spans})))
