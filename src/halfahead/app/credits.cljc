(ns halfahead.app.credits
  "The closing credits of a conversation as plain text, for the :credits
  delivery to perform. Pure."
  (:require [clojure.string :as str]))

(defn- member-line [members {:keys [kind display-name owner-id]}]
  (if (= :agent kind)
    (str display-name " · agent of "
         (or (get-in members [owner-id :display-name]) owner-id))
    display-name))

(defn- cast-lines [{:keys [conversation members]}]
  (->> (or (seq (:member-ids conversation)) (keys members))
       (keep members)
       ;; sort-by is stable, so the member order holds within each kind
       (sort-by #(if (= :human (:kind %)) 0 1))
       (map #(member-line members %))))

(defn credits-text
  "The credits for state, dated date-string. Never blank, so it is always a
  valid :ha/message-text."
  [state date-string]
  (let [title (str/trim (str (get-in state [:conversation :title])))
        n     (count (:messages state))]
    (str/join "\n"
              (concat [(str/upper-case (if (str/blank? title) "Conversation" title))
                       ""]
                      (cast-lines state)
                      [""
                       (str n (if (= 1 n) " line exchanged" " lines exchanged"))
                       date-string
                       ""
                       "Half Ahead"]))))
