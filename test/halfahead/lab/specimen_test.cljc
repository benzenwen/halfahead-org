(ns halfahead.lab.specimen-test
  (:require [clojure.test :refer [deftest is testing]]
            [halfahead.lab.specimen :as specimen]
            [halfahead.schema :as schema]))

(defn- narrow-i
  "An advance that is not uniform, as a measured font's is not."
  [c]
  (case c ("l" "f" " ") 0.3 "H" 0.7 0.55))

(def ^:private all-opts
  (for [lights [0 1]
        advance [nil narrow-i]
        width-em [18 40 6]]
    (cond-> {:width-em width-em :aspect 1.4 :lights lights}
      advance (assoc :advance advance))))

(def ^:private channels [:sx :sy :skew :weight :blur :fill :ink :tint])

(def ^:private expected
  "Each row's values, in order, as the order lists them (:stretch with a
  sample at rest first, to compare with)."
  [[:weight [-1.0 -0.5 0.0 0.5 1.0]]
   [:blur [0.0 0.33 0.66 1.0]]
   [:skew [-0.3 0.0 0.3]]
   [:stretch [{:sx 1.0 :sy 1.0} {:sx 1.4 :sy 1.0} {:sx 1.0 :sy 1.4}]]
   [:fill [0.0 0.25 0.5 0.75 1.0]]
   [:ink [:text :signal :gold :cool :muted]]])

(defn- wanted
  "The channels a pose of this sample carries: the row's value where it is
  not the channel's rest."
  [row value]
  (case row
    :weight (if (zero? value) {} {:weight value})
    :blur (if (zero? value) {} {:blur value})
    :skew (if (zero? value) {} {:skew value})
    :fill (if (== 1 value) {} {:fill value})
    :stretch (into {} (remove #(== 1 (val %))) value)
    :ink (if (= :text value) {} {:ink value})))

(deftest the-frame-is-valid-on-paper-and-on-the-stage
  (doseq [opts all-opts]
    (testing (pr-str (dissoc opts :advance))
      (let [f (specimen/frame opts)]
        (is (schema/valid? :ha/glyph-frame f))
        (is (= {:level 3 :lights (:lights opts)} (:reveal f)))
        (is (= f (specimen/frame opts)))
        (is (= (range (count (:poses f))) (map :id (:poses f))))))))

(deftest the-rows-carry-the-values-listed
  (doseq [opts all-opts]
    (let [by-row (group-by :row (specimen/samples opts))]
      (is (= (map first expected) (distinct (map :row (specimen/samples opts)))))
      (doseq [[row values] expected]
        (is (= values (map :value (by-row row))) (str row)))))
  (is (= (set (map :value (filter #(= :ink (:row %)) (specimen/samples {}))))
         #{:text :signal :gold :cool :muted})))

(deftest every-sample-is-in-the-frame-and-only-its-channel-is-off-rest
  (doseq [opts all-opts]
    (let [f (specimen/frame opts)
          by-id (into {} (map (juxt :id identity)) (:poses f))
          samples (specimen/samples opts)
          sampled (mapcat :glyph-ids samples)]
      (is (apply distinct? sampled))
      (doseq [{:keys [row value glyph-ids]} samples]
        (testing (str row " " value)
          (is (seq glyph-ids))
          (is (every? by-id glyph-ids))
          (is (apply = (map (comp :y by-id) glyph-ids)) "a sample is on one row")
          (is (every? #(= (wanted row value) (select-keys (by-id %) channels)) glyph-ids))))
      (testing "everything else about every pose is at rest"
        (is (every? #(= {:ghost? false :z 0.0 :rx 0.0 :ry 0.0 :rz 0.0
                         :scale 1.0 :opacity 1.0 :glow 0.0 :wobble 0.0}
                        (select-keys % [:ghost? :z :rx :ry :rz :scale :opacity :glow :wobble]))
                    (:poses f))))
      (testing "glyphs between the samples are blank and carry no channel"
        (is (every? #(and (= " " (:char %)) (empty? (select-keys % channels)))
                    (remove (comp (set sampled) :id) (:poses f))))))))

(deftest the-samples-spell-the-word
  (let [f (specimen/frame {:width-em 40})
        by-id (into {} (map (juxt :id :char)) (:poses f))
        text (fn [s] (apply str (map by-id (:glyph-ids s))))]
    (doseq [{:keys [row] :as s} (specimen/samples {:width-em 40})]
      (is (= (if (= :fill row) "H" "Halfahead") (text s)) (str row))))
  (testing "a narrow width shows part of it, and every row fits"
    (let [opts {:width-em 18 :advance narrow-i}
          f (specimen/frame opts)
          by-id (into {} (map (juxt :id identity)) (:poses f))]
      (doseq [{:keys [glyph-ids]} (specimen/samples opts)]
        (is (= (apply str (map (comp :char by-id) glyph-ids))
               (subs "Halfahead" 0 (count glyph-ids)))))
      (is (every? #(<= 0.0 (+ (:x %) (narrow-i (:char %))) 18.0) (:poses f))))))

(deftest the-camera-is-frontal-and-fits-the-rows
  (doseq [opts all-opts
          aspect [0.6 1.0 1.4 2.0]]
    (let [{:keys [poses camera caret]} (specimen/frame (assoc opts :aspect aspect))
          {:keys [x y z tx ty tz fov]} camera
          half-h (* z (Math/tan (* 0.5 fov (/ Math/PI 180.0))))
          half-w (* half-h aspect)
          ;; a letter stands up to an em and a half above its baseline when
          ;; stretched, and its quad reaches an em to the right of its pen
          inside? (fn [px py] (and (<= (- tx half-w) (- px 0.5)) (<= (+ px 1.0) (+ tx half-w))
                                   (<= (- ty half-h) (- py 0.5)) (<= (+ py 1.5) (+ ty half-h))))]
      (testing (str (pr-str (dissoc opts :advance)) " aspect " aspect)
        (is (and (= x tx) (= y ty) (pos? z) (zero? tz)) "straight on")
        (is (every? #(inside? (:x %) (:y %)) poses))
        (is (< (:y caret) (apply min (map :y poses))) "the caret is below every row")))))
