(ns halfahead.lab.cues
  "A finished word becomes a cue, decided instantly and on the device."
  (:require [clojure.string :as str]
            [halfahead.lab.glossary :as glossary]))

(def ^:private max-word-length 64)

(defn- glossary-hit [word]
  (let [w (str/lower-case word)]
    (or (get glossary/entries w)
        (when (str/ends-with? w "s")
          (get glossary/entries (subs w 0 (dec (count w))))))))

(defn- punctuated [following]
  (case following
    "!" {:effect :quake :intensity 0.7}
    "?" {:effect :wobble :intensity 0.5}
    nil))

(defn- cased-letter? [c]
  (let [s (str c)]
    (not= (str/upper-case s) (str/lower-case s))))

(defn- all-capitals? [word]
  (and (= word (str/upper-case word))
       (<= 2 (count (filter cased-letter? word)))))

(defn- shaped [word]
  (cond
    (all-capitals? word)        {:effect :launch :intensity 0.8}
    (<= 12 (count word))        {:effect :shimmer :intensity 0.5}
    (re-matches #"[0-9]+" word) {:effect :pop :intensity 0.4}))

(defn- high-surrogate?
  "String comparison is by UTF-16 unit on both hosts, so no interop is needed."
  [c]
  (let [s (str c)]
    (and (<= 0 (compare s "\uD800")) (<= (compare s "\uDBFF") 0))))

(defn- cue-word
  "Leading whitespace goes first, so a long run of it cannot truncate a
  non-blank word down to a blank one; the cut never splits a surrogate pair."
  [word]
  (let [w (str/triml word)]
    (if (<= (count w) max-word-length)
      w
      (let [end (if (high-surrogate? (nth w (dec max-word-length)))
                  (dec max-word-length)
                  max-word-length)]
        (subs w 0 end)))))

(defn word-cue
  "The cue for a just-finished word, as typed, and the one-character string
  that followed it. Rules, first match wins: Half Ahead's glossary (a
  following \"!\" adds 0.2, capped at 1), then the following punctuation,
  then the word's shape, then a quiet default. Pure and local; a model, if
  ever added, belongs behind this function as a fallback before the default.
  Never throws for a non-blank word."
  [word following]
  (let [[cue source]
        (if-let [{:keys [effect intensity]} (glossary-hit word)]
          [{:effect effect
            :intensity (if (= "!" following) (min 1.0 (+ intensity 0.2)) intensity)}
           :glossary]
          (if-let [cue (punctuated following)]
            [cue :punctuation]
            (if-let [cue (shaped word)]
              [cue :shape]
              [{:effect :pop :intensity 0.3} :default])))]
    (assoc cue :word (cue-word word) :source source)))
