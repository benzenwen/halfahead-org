(ns halfahead.app.enrich
  "Completes view actions with what only the shell knows: times, ids and
  input values. Pure; the shell builds the context.")

(defn- send-key? [{:keys [key shift?]}]
  (and (= "Enter" key) (not shift?)))

(defn enrich
  "A vector of actions to handle for a view action. ctx is
  {:now-ms :now :date :new-id :value :key :shift?}. An action it does not know
  passes through."
  [action {:keys [now-ms now date new-id value] :as ctx}]
  (case (first action)
    :composer/edited   [[:composer/edited {:text value}]]
    :composer/key      (if (send-key? ctx) [[:composer/sent {:id new-id :now now}]] [])
    :composer/sent     [[:composer/sent {:id new-id :now now}]]
    :replay/requested  [(update action 1 assoc :now-ms now-ms)]
    :scene/ended       [(update action 1 assoc :now-ms now-ms :date date)]
    :scene/key         (if (= "Escape" (:key ctx)) [[:scene/dismissed]] [])
    [action]))

(defn prevent-default?
  "True where the browser's own behaviour would fight the action: Enter
  would add a newline, and submit would reload the page."
  [action ctx]
  (case (first action)
    :composer/key  (send-key? ctx)
    :composer/sent true
    false))
