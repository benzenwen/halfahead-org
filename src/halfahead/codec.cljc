(ns halfahead.codec
  "The boundary between domain maps (kebab-case keyword keys, keyword
  enums, span tuples) and Firestore's wire shape (camelCase string keys,
  enum strings, span maps). Both directions validate against
  halfahead.schema. The legal wire keys are exactly the schema's keys."
  (:require [clojure.string :as str]
            [halfahead.schema :as schema]
            [malli.core :as m]))

(defn- camel [k]
  (let [[head & more] (str/split (name k) #"-")]
    (apply str head (map str/capitalize more))))

(defn- field-kind
  "How a field's value is spelled on the wire: :enum, :span, :spans, or nil
  for values that pass through unchanged."
  [s]
  (let [s (m/deref-all s)]
    (case (m/type s)
      :enum   :enum
      :and    (when (= :tuple (m/type (m/deref-all (first (m/children s)))))
                :span)
      :vector (when (= :span (field-kind (first (m/children s))))
                :spans)
      nil)))

(def ^:private fields
  "k -> {kebab-key {:wire name :kind kind :values enum-values}}"
  (memoize
   (fn [k]
     (let [top (m/deref-all (schema/schema k))
           body (if (= :and (m/type top))
                  (first (filter #(= :map (m/type %)) (m/children top)))
                  top)]
       (into {}
             (for [[key s] (m/entries body)
                   :let [s (m/deref-all s)]]
               [key {:wire   (camel key)
                     :kind   (field-kind s)
                     :values (when (= :enum (m/type s)) (set (m/children s)))}]))))))

(defn- invalid! [k value]
  (throw (ex-info "invalid data" {:type   :ha/invalid-data
                                  :entity k
                                  :errors (schema/explain k value)
                                  :value  value})))

(defn- span->wire [[from to]] {"from" from "to" to})

(defn- encode-value [kind v]
  (case kind
    :enum  (name v)
    :span  (span->wire v)
    :spans (mapv span->wire v)
    v))

(defn- wire->span
  "A malformed span is left as it is, so validation rejects it."
  [v]
  (if (and (map? v) (contains? v "from") (contains? v "to"))
    [(get v "from") (get v "to")]
    v))

(defn- decode-value [key {:keys [kind values]} v]
  (case kind
    :enum  (cond
             (not (string? v)) v
             (and (= key :delivery) (not (contains? values (keyword v)))) :plain
             :else (keyword v))
    :span  (wire->span v)
    :spans (if (sequential? v) (mapv wire->span v) v)
    v))

(defn encode
  "The Firestore wire map for domain map x of entity k. Throws
  :ha/invalid-data when x is not a valid k."
  [k x]
  (when-not (schema/valid? k x) (invalid! k x))
  (let [table (fields k)]
    (into {}
          (for [[key v] x
                :let [{:keys [wire kind]} (table key)]]
            [wire (encode-value kind v)]))))

(defn decode
  "The domain map for Firestore wire map w of entity k. Wire keys the schema
  does not know are dropped (a newer writer may add fields) and an unknown
  delivery becomes :plain. Throws :ha/invalid-data when the result is not a
  valid k."
  [k w]
  (let [table (fields k)
        x (into {}
                (for [[key {:keys [wire] :as field}] table
                      :when (contains? w wire)]
                  [key (decode-value key field (get w wire))]))]
    (when-not (schema/valid? k x) (invalid! k x))
    x))
