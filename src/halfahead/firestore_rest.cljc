(ns halfahead.firestore-rest
  "Wire maps to and from the typed-value JSON of the Firestore REST API.
  Pure; the only namespace that knows that format."
  (:require [clojure.string :as str]))

(defn- unsupported! [v]
  (throw (ex-info "unsupported value" {:type :ha/unsupported-value :value v})))

(defn- inst->rfc3339 [d]
  #?(:clj  (str (.toInstant ^java.util.Date d))
     :cljs (str/replace (.toISOString d) ".000Z" "Z")))

(defn- rfc3339->inst [s]
  #?(:clj  (java.util.Date/from (java.time.Instant/parse s))
     :cljs (js/Date. s)))

(defn- ->value [v]
  (cond
    (nil? v)        {"nullValue" nil}
    (string? v)     {"stringValue" v}
    (boolean? v)    {"booleanValue" v}
    (integer? v)    {"integerValue" (str v)}
    (number? v)     {"doubleValue" v}
    (inst? v)       {"timestampValue" (inst->rfc3339 v)}
    (map? v)        {"mapValue" {"fields" (update-vals v ->value)}}
    (sequential? v) {"arrayValue" (if (empty? v)
                                    {}
                                    {"values" (mapv ->value v)})}
    :else           (unsupported! v)))

(defn- <-value [typed]
  (let [[tag v] (first typed)]
    (case tag
      "nullValue"      nil
      "stringValue"    v
      "booleanValue"   v
      "integerValue"   (parse-long v)
      "doubleValue"    v
      "timestampValue" (rfc3339->inst v)
      "arrayValue"     (mapv <-value (get v "values"))
      "mapValue"       (update-vals (get v "fields" {}) <-value)
      (unsupported! typed))))

(defn ->fields
  "Wire map -> {\"fields\" {name typed-value}}."
  [wire]
  {"fields" (update-vals wire ->value)})

(defn <-fields
  "Inverse of ->fields."
  [doc]
  (update-vals (get doc "fields" {}) <-value))
