(ns halfahead.lab.instance-test
  (:require [clojure.test :refer [deftest is]]
            [halfahead.lab.instance :as instance]))

(deftest the-limits-are-webgpus-guaranteed-minimums
  ;; pinned, so nobody passes the tests below by loosening the limit
  (is (= {:max-vertex-buffers 8 :max-vertex-attributes 16} instance/limits)))

(deftest the-glyph-pipeline-fits-any-webgpu-device-with-a-buffer-to-spare
  (is (= 7 (instance/vertex-buffers)))
  (is (= (+ 3 (count instance/attributes)) (instance/vertex-buffers))
      "position, uv, the instance matrix, and one per attribute")
  (is (<= (instance/vertex-buffers) (dec (:max-vertex-buffers instance/limits)))
      "a new channel goes into a packed attribute, not a new one"))

(deftest the-glyph-pipeline-fits-the-attribute-slots
  (is (= 10 (instance/vertex-attribute-slots)))
  (is (= (+ 6 (count instance/attributes)) (instance/vertex-attribute-slots))
      "position, uv, four for the matrix, and one per attribute")
  (is (<= (instance/vertex-attribute-slots) (:max-vertex-attributes instance/limits))))

(deftest every-attribute-is-a-vector-the-shader-can-read
  (is (seq instance/attributes))
  (doseq [{:keys [name width holds] :as a} instance/attributes]
    (is (and (string? name) (seq name)) (pr-str a))
    (is (and (integer? width) (<= 1 width 4)) (pr-str a))
    (is (and (vector? holds) (seq holds) (every? keyword? holds)
             (<= (count holds) width))
        (pr-str a))))

(deftest names-and-channels-are-distinct
  (is (apply distinct? (map :name instance/attributes)))
  (is (apply distinct? (mapcat :holds instance/attributes))
      "no channel is in two attributes"))

(deftest the-attributes-are-the-ones-the-shader-reads
  (is (= [["glyphRect" 4] ["glyphLook" 4] ["glyphShape" 3] ["glyphInk" 3]]
         (mapv (juxt :name :width) instance/attributes)))
  (is (= [:rect :opacity :glow :wobble :ghost :weight :blur :fill :ink]
         (vec (mapcat :holds instance/attributes)))))
