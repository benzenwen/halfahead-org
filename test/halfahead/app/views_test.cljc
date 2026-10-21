(ns halfahead.app.views-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [halfahead.app.core :as core]
            [halfahead.app.views :as views]))

;; --- fixtures ---------------------------------------------------------------

(def ben {:id "ben" :kind :human :display-name "Ben" :joined-at #inst "2026-01-01"})
(def bot {:id "bot" :kind :agent :display-name "Bot" :owner-id "ben"
          :joined-at #inst "2026-01-01"})
(def convo {:id "c1" :title "Hello" :member-ids ["ben" "bot"]
            :created-by "ben" :created-at #inst "2026-01-01"})

(defn msg
  ([id text] (msg id text :plain "ben" #inst "2026-01-02"))
  ([id text delivery] (msg id text delivery "ben" #inst "2026-01-02"))
  ([id text delivery author sent-at]
   {:id id :conversation-id "c1" :author-id author :text text :delivery delivery
    :emphasis [] :sent-at sent-at}))

(defn step [state action] (:state (core/handle state action)))

(def started
  (step core/initial-state
        [:session/started {:me "ben" :conversation convo :members [ben bot]
                           :now-ms 1000 :reduced-motion? false}]))

(defn live [state & ms]
  (step state [:messages/received {:messages (vec ms) :initial? false :now-ms 1000}]))

(defn initial [state & ms]
  (step state [:messages/received {:messages (vec ms) :initial? true :now-ms 1000}]))

(defn thinking [state]
  (step state [:presence/received
               {:presence [{:member-id "bot" :conversation-id "c1" :state :thinking
                            :since #inst "2026-01-02"}]}]))

;; --- hiccup walking ---------------------------------------------------------

(defn- tag-of [node] (let [t (name (first node))] (first (str/split t #"[.#]"))))

(defn- attrs-of [node] (let [a (second node)] (if (map? a) a {})))

(defn- classes-of [node]
  (let [tag-classes (rest (str/split (name (first node)) #"\."))
        c (:class (attrs-of node))]
    (set (concat tag-classes (if (coll? c) c [c])))))

(defn- children-of [node]
  (drop (if (map? (second node)) 2 1) node))

(defn- child-nodes [node]
  (filter vector? (mapcat (fn [c] (if (seq? c) c [c])) (children-of node))))

(defn- nodes [hiccup] (tree-seq vector? child-nodes hiccup))

(defn find-all [pred hiccup] (filter pred (filter #(keyword? (first %)) (nodes hiccup))))
(defn find-one [pred hiccup] (first (find-all pred hiccup)))
(defn tag? [t] #(= t (tag-of %)))
(defn class? [c] #(contains? (classes-of %) c))

(defn text-of [node]
  (cond (string? node) node
        (vector? node) (apply str (map text-of (children-of node)))
        (seq? node) (apply str (map text-of node))
        :else ""))

(defn seg-nodes [article] (find-all (class? "seg") article))

(defn articles [page] (find-all (tag? "article") page))

(defn sr-text [article] (text-of (find-one (class? "sr-only") article)))

(defn seg-text [article] (apply str (map text-of (seg-nodes article))))

(defn seg-p [seg] (get-in (attrs-of seg) [:style "--p"]))

;; --- criteria ---------------------------------------------------------------

(deftest agent-chip-says-agent-of-owner
  (let [page (views/page started)
        chips (find-all (class? "chip") (find-one (tag? "header") page))
        by-text (fn [s] (first (filter #(str/includes? (text-of %) s) chips)))]
    (is (= 2 (count chips)))
    (is (str/includes? (text-of (by-text "Bot")) "agent of Ben"))
    (is (not (str/includes? (text-of (by-text "Ben")) "agent of")) "human chip: name only")
    (is (str/includes? (text-of (find-one (tag? "h1") page)) "Hello"))))

(deftest messages-oldest-first-in-ol
  (let [s (live started
                (msg "c" "third" :plain "ben" #inst "2026-01-04")
                (msg "a" "first" :plain "ben" #inst "2026-01-02")
                (msg "b" "second" :plain "bot" #inst "2026-01-03"))
        ol (find-one (tag? "ol") (views/page s))
        items (find-all (tag? "li") ol)]
    (is (= ["first" "second" "third"] (map #(sr-text (find-one (tag? "article") %)) items)))))

(def gen-text
  (gen/such-that seq (gen/fmap str/join
                               (gen/vector (gen/elements ["a" "word" "héllo" "😀" "\n" " " ". " "日本語"])
                                           1 20))))

(defspec both-copies-equal-full-text-at-every-point 200
  (prop/for-all [text gen-text
                 delivery (gen/elements [:plain :typed :headline :aside :credits])
                 elapsed (gen/choose -50 8000)]
    (let [s (-> started
                (live (msg "m" text delivery))
                (step [:clock/ticked {:now-ms (+ 1000 elapsed)}]))
          a (first (articles (views/page s)))]
      (and (= text (sr-text a))
           (= text (seg-text a))
           (= "true" (:aria-hidden (attrs-of (find-one #(= "true" (:aria-hidden (attrs-of %))) a))))))))

(deftest performing-segments-carry-frame-progress
  (let [s (-> started
              (live (msg "m" "type this out please" :typed))
              (step [:clock/ticked {:now-ms 1300}]))
        frame (get (core/frames s) "m")
        a (first (articles (views/page s)))
        ps (map seg-p (seg-nodes a))]
    (is (seq ps))
    (is (= (map (comp str :progress) frame) ps))
    (is (some #(< 0 (:progress %) 1) frame) "the fixture is mid-performance")
    (is (= (map #(str "fx-" (name (:effect %))) frame)
           (map #(first (filter (fn [c] (str/starts-with? c "fx-")) (classes-of %))) (seg-nodes a))))))

(deftest settled-segments-are-all-one
  (let [s (initial started (msg "m" "hello there" :typed))
        a (first (articles (views/page s)))]
    (is (seq (seg-nodes a)))
    (is (every? #(= (str 1.0) (seg-p %)) (seg-nodes a)))))

(deftest presence-line-matches-awaiting-agent
  (let [status #(find-one (fn [n] (= "status" (:role (attrs-of n)))) (views/page %))
        text #(text-of (status %))
        human-last (thinking (initial started (msg "a" "hi")))
        agent-last (thinking (initial started (msg "a" "hi" :plain "bot" #inst "2026-01-02")))
        idle (initial started (msg "a" "hi"))]
    (is (= "Bot is thinking…" (text human-last)))
    (is (= "polite" (:aria-live (attrs-of (status human-last)))))
    (doseq [s [human-last agent-last idle started]]
      (is (= (core/awaiting-agent? s) (boolean (seq (text s))))))
    (is (= "" (text agent-last)))
    (doseq [s [idle started agent-last]]
      (is (nil? (find-one (class? "glyph") (status s)))))
    (let [glyphs (find-all (class? "glyph") (status human-last))]
      (is (= 1 (count glyphs)))
      (is (= "thinking" (:data-state (attrs-of (first glyphs))))))))

(defn- form-of [s] (find-one (tag? "form") (views/page s)))

(deftest send-disabled-for-blank-text
  (doseq [[t disabled?] [["" true] ["   " true] ["\n" true] ["hi" false]]]
    (let [s (step started [:composer/edited {:text t}])
          button (find-one (tag? "button") (form-of s))]
      (is (= disabled? (:disabled (attrs-of button))) (pr-str t)))))

(deftest composer-events-are-data
  (let [form (form-of started)
        ta (find-one (tag? "textarea") form)
        label (find-one (tag? "label") form)]
    (is (= [[:composer/sent]] (get-in (attrs-of form) [:on :submit])))
    (is (= [[:composer/edited]] (get-in (attrs-of ta) [:on :input])))
    (let [[[k args] :as h] (get-in (attrs-of ta) [:on :keydown])]
      (is (= 1 (count h)))
      (is (= :composer/key k))
      (is (= #{:key :shift?} (set (keys args)))))
    (is (= (:id (attrs-of ta)) (:for (attrs-of label))))))

(defn- radios [s]
  (find-all #(and (= "input" (tag-of %)) (= "radio" (:type (attrs-of %)))) (form-of s)))

(defn- checked-values [s]
  (mapv (comp :value attrs-of) (filter (comp :checked attrs-of) (radios s))))

(deftest delivery-radios-offer-five-named-deliveries
  (let [rs (radios started)
        labels (find-all (fn [n] (and (= "label" (tag-of n))
                                      (find-one #(= "input" (tag-of %)) n)))
                         (form-of started))]
    (is (= ["plain" "typed" "headline" "aside" "credits"] (mapv (comp :value attrs-of) rs)))
    (is (every? #(= "delivery" (:name (attrs-of %))) rs))
    (is (= 5 (count labels)))
    (is (every? #(not (str/blank? (:title (attrs-of %)))) labels))))

(deftest delivery-radio-checked-follows-state
  (is (= ["plain"] (checked-values started)))
  (let [s (step started [:composer/delivery-chosen {:delivery :aside}])]
    (is (= ["aside"] (checked-values s)))
    (let [sent (-> s
                   (step [:composer/edited {:text "hm"}])
                   (step [:composer/sent {:id "m1" :now #inst "2026-02-02"}]))]
      (is (= ["plain"] (checked-values sent))))))

(deftest delivery-radios-emit-delivery-chosen
  (doseq [r (radios started)]
    (is (= [[:composer/delivery-chosen {:delivery (keyword (:value (attrs-of r)))}]]
           (get-in (attrs-of r) [:on :change])))))

(deftest delivery-group-has-accessible-name
  (let [g (find-one #(= "radiogroup" (:role (attrs-of %))) (form-of started))]
    (is g)
    (is (= "Delivery" (:aria-label (attrs-of g))))))

(deftest empty-conversation-renders-empty-state
  (let [empty-page (views/page started)
        full (views/page (initial started (msg "a" "hi")))]
    (is (find-one (class? "empty") empty-page))
    (is (nil? (find-one (tag? "ol") empty-page)))
    (is (nil? (find-one (class? "empty") full)))
    (is (find-one (tag? "ol") full))))

(defn- button-named [label node]
  (find-one #(and (= "button" (tag-of %)) (= label (text-of %))) node))

(deftest skip-button-only-while-performing
  (let [s (-> started
              (initial (msg "old" "settled" :plain "ben" #inst "2026-01-02"))
              (live (msg "new" "performing" :typed "ben" #inst "2026-01-03")))
        [old new] (articles (views/page s))
        done (step s [:clock/ticked {:now-ms 60000}])]
    (is (nil? (button-named "Skip" old)))
    (is (= "button" (:type (attrs-of (button-named "Skip" new)))))
    (is (= [[:message/skipped {:id "new"}]]
           (get-in (attrs-of (button-named "Skip" new)) [:on :click])))
    (is (nil? (:on (attrs-of new))) "no article click handler")
    (is (nil? (button-named "Skip" (second (articles (views/page done))))))))

(deftest replay-button-events
  (let [s (initial started (msg "old" "settled"))
        b (find-one (class? "replay") (first (articles (views/page s))))]
    (is (= [[:replay/requested {:id "old"}]] (get-in (attrs-of b) [:on :click])))
    (is (seq (:aria-label (attrs-of b))))))

(deftest author-line-reads-name-dot-owner
  (let [s (initial started (msg "a" "hi" :plain "bot" #inst "2026-01-02")
                   (msg "b" "yo" :plain "ben" #inst "2026-01-03"))
        [agent human] (map #(text-of (find-one (class? "author") %)) (articles (views/page s)))]
    (is (= "Bot · agent of Ben" agent))
    (is (= "Ben" human))))

(deftest preferences-toggles
  (let [boxes (find-all #(= "checkbox" (:type (attrs-of %))) (views/page started))
        labels (find-all (tag? "label") (find-one (class? "prefs") (views/page started)))]
    (is (= 2 (count boxes)))
    (is (some #(str/includes? (text-of %) "soon") labels))))

(deftest preference-checkboxes-emit-negation
  (doseq [[k idx] [[:reduced-motion? 0] [:sound? 1]]
          cur [true false]]
    (let [s (step started [:prefs/changed {k cur}])
          box (nth (find-all #(= "checkbox" (:type (attrs-of %))) (views/page s)) idx)]
      (is (= cur (:checked (attrs-of box))))
      (is (= [[:prefs/changed {k (not cur)}]] (get-in (attrs-of box) [:on :change]))
          (pr-str k cur)))))

(deftest agent-glyphs-carry-presence-state
  (let [glyphs #(find-all (class? "glyph") (views/page %))
        states #(map (comp :data-state attrs-of) (glyphs %))]
    (is (seq (glyphs started)))
    (is (every? #{"idle"} (states started)))
    (is (every? #(= "true" (:aria-hidden (attrs-of %))) (glyphs started)))
    (let [t (thinking started)]
      (is (some #{"thinking"} (states t)))
      (is (every? #(= "true" (:aria-hidden (attrs-of %))) (glyphs t))))
    (let [header-glyphs (find-all (class? "glyph") (find-one (tag? "header") (views/page started)))]
      (is (= 1 (count header-glyphs)) "one per agent member, none for humans"))))

;; --- stylesheet -------------------------------------------------------------

(defn- read-file [path]
  #?(:clj (slurp path)
     :cljs (.readFileSync (js/require "fs") path "utf8")))

(deftest stylesheet-marks-chosen-delivery-by-more-than-colour
  (let [css (read-file "public/css/halfahead.css")]
    (is (re-find #"\.delivery input:checked \+ span \{[^}]*text-decoration: underline" css))
    (is (not (re-find #"\.delivery label \{[^}]*border" css)))))

(deftest stylesheet-defines-effects-and-reduced-motion
  (let [css (read-file "public/css/halfahead.css")
        outside-root (str/replace css #":root\s*\{[^}]*\}" "")
        outside-root (str/replace outside-root #"@media \(prefers-color-scheme[^{]*\{\s*\s*\}" "")]
    (doseq [c [".fx-appear" ".fx-type" ".fx-slam" ".fx-whisper" ".fx-rise"
               "@media (prefers-reduced-motion: reduce)"]]
      (is (str/includes? css c) c))
    (is (nil? (re-find #"#[0-9a-fA-F]{3,8}\b|rgba?\(|hsla?\(" outside-root))
        "colours live only in :root")))

(deftest stylesheet-has-schemes-and-honest-hiding
  (let [css (read-file "public/css/halfahead.css")]
    (is (str/includes? css "prefers-color-scheme: dark"))
    (is (re-find #"(?is)/\*.*contrast.*:1.*\*/" css) "contrast pairs are stated")
    (is (re-find #"\.replay\s*\{\s*opacity:\s*0" css))
    (is (not (re-find #"\.replay[^{]*\{[^}]*display:\s*none" css))
        "replay stays reachable by Tab")
    (is (str/includes? css ".replay") "hover/focus reveal")
    (is (str/includes? css "focus-within"))))

(deftest index-links-stylesheet
  (is (str/includes? (read-file "public/index.html") "css/halfahead.css")))

(defn- luminance [hex]
  (let [chan (fn [i]
               (let [c (/ (#?(:clj Integer/parseInt :cljs js/parseInt)
                            (subs hex i (+ i 2)) 16) 255.0)]
                 (if (<= c 0.03928) (/ c 12.92) (Math/pow (/ (+ c 0.055) 1.055) 2.4))))]
    (+ (* 0.2126 (chan 1)) (* 0.7152 (chan 3)) (* 0.0722 (chan 5)))))

(defn- contrast [a b]
  (let [[hi lo] (reverse (sort [(luminance a) (luminance b)]))]
    (/ (+ hi 0.05) (+ lo 0.05))))

(defn- token [css re] (second (re-find re css)))

(deftest send-button-contrast-and-disabled-style
  (let [css (read-file "public/css/halfahead.css")
        stated (re-find #"/\*[^*]*on-signal on signal,\s*([\d.]+):1[^*]*\*/\s*\.composer button \{" css)
        root (re-find #":root\s*\{[^}]*\}" css)
        dark (re-find #"prefers-color-scheme: dark\)\s*\{\s*:root\s*\{[^}]*\}" css)
        pair (fn [block] [(token block #"--on-signal:\s*(#[0-9a-f]{6})")
                          (token block #"--signal:\s*(#[0-9a-f]{6})")])]
    (is stated "enabled pair and ratio are stated on the rule")
    (is (>= (#?(:clj Double/parseDouble :cljs js/parseFloat) (second stated)) 4.5))
    (is (>= (apply contrast (pair root)) 4.5))
    (is (>= (apply contrast (pair dark)) 4.5))
    (let [disabled (re-find #"\.composer button:disabled\s*\{[^}]*\}" css)]
      (is (str/includes? disabled "background: transparent"))
      (is (str/includes? disabled "border-color"))
      (is (not (str/includes? disabled "opacity"))))))

(deftest labels-and-rhythm-in-stylesheet
  (let [css (read-file "public/css/halfahead.css")]
    (is (re-find #"\.composer label, \.prefs label \{" css) "prefs labels share the label type")
    (is (re-find #"\.prefs label \{[^}]*font-size: var\(--step-label\)" css))
    (is (re-find #"--step-label: 0\.875rem" css))
    (is (re-find #"\.transcript li \{\s*padding: var\(--space-2\) 0;\s*border-top: 1px solid var\(--rule\)" css))
    (is (re-find #"\.transcript li:first-child \{ border-top: 0; \}" css))))

;; --- credits ----------------------------------------------------------------

(def with-credits
  (step started [:scene/ended {:now-ms 5000 :date "21 October 2026"}]))

(deftest end-scene-button-ends-the-scene
  (let [buttons (find-all (class? "end-scene") (views/page started))]
    (is (= 1 (count buttons)))
    (is (= [[:scene/ended]] (get-in (attrs-of (first buttons)) [:on :click])))
    (is (= [] (find-all #(= "Credits" (:aria-label (attrs-of %))) (views/page started))))))

(deftest credits-overlay-is-an-accessible-dialog
  (let [page    (views/page with-credits)
        dialogs (find-all #(= "Credits" (:aria-label (attrs-of %))) page)
        dialog  (first dialogs)
        text    (get-in with-credits [:credits :text])]
    (is (= 1 (count dialogs)))
    (is (= "dialog" (:role (attrs-of dialog))))
    (is (= "true" (:aria-modal (attrs-of dialog))))
    (is (= [[:scene/key]] (get-in (attrs-of dialog) [:on :keydown])))
    (is (= "-1" (:tabindex (attrs-of dialog))))
    (is (= [[:dom/focus]] (:replicant/on-mount (attrs-of dialog))))
    (is (empty? (find-all #(contains? (attrs-of %) :autofocus) dialog)))
    (is (seq (find-all #(and ((class? "sr-only") %) (= text (text-of %))) dialog)))
    (is (seq (find-all #(= "true" (:aria-hidden (attrs-of %))) dialog)))
    (let [close (first (find-all #(and ((tag? "button") %) (= "Close" (text-of %))) dialog))]
      (is (some? close))
      (is (= [[:scene/dismissed]] (get-in (attrs-of close) [:on :click]))))))
