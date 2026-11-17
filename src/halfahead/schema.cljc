(ns halfahead.schema
  "Every shape Half Ahead persists or passes between processes, as Malli
  schemas in one registry, addressed by :ha/* keyword. docs/SCHEMA.md is
  the human-readable mirror; the two must agree. Entity maps are closed:
  an unknown key is invalid. A change here is a design change: make it
  deliberately, in its own work order."
  (:require [clojure.string :as str]
            [malli.core :as m]
            [malli.error :as me]))

(defn- text [min max]
  [:and [:string {:min min :max max}]
   [:fn {:error/message "must not be blank"} #(not (str/blank? %))]])

(def ^:private optional {:optional true})

(def max-performance-ms
  "No flourish may hold a reader longer than this. Five seconds, because
  WCAG 2.2.2 (Level A) exempts motion that starts automatically and stops
  within five seconds from needing a pause control (DECISIONS.md H14)."
  5000)

(def ^:private registry
  (merge
   (m/default-schemas)
   {;; --- value types ------------------------------------------------------
    :ha/id          [:re #"^[A-Za-z0-9_-]{1,128}$"]
    :ha/timestamp   [:fn {:error/message "must be an inst"} inst?]
    :ha/message-text (text 1 4000)

    ;; How a line is delivered. Closed on purpose: the agent picks one of
    ;; these and never authors motion itself. Unknown values decode to
    ;; :plain (see halfahead.codec).
    ;;   :plain     appears at once
    ;;   :typed     arrives at a readable typing pace
    ;;   :headline  the emphasised words land first, large, then the line
    ;;              settles around them (the game-show card)
    ;;   :aside     arrives small and quiet, as from the wings
    ;;   :credits   lines roll up into place, one after another
    :ha/delivery    [:enum :plain :typed :headline :aside :credits]

    ;; What a beat of a timeline does to its span of text while it plays.
    :ha/effect      [:enum :appear :type :slam :whisper :rise]

    ;; A half-open character range [from to) within a message's text.
    :ha/span        [:and [:tuple [:int {:min 0}] [:int {:min 0}]]
                     [:fn {:error/message "from must be < to"}
                      (fn [[from to]] (< from to))]]

    ;; --- entities (persisted) ---------------------------------------------
    :ha/member
    [:and
     [:map {:closed true}
      [:id :ha/id]
      [:kind [:enum :human :agent]]
      [:display-name (text 1 80)]
      ;; for an agent, the human it belongs to
      [:owner-id optional :ha/id]
      [:joined-at :ha/timestamp]]
     [:fn {:error/message "an agent needs an owner-id; a human has none"}
      (fn [{:keys [kind owner-id]}]
        (= (= kind :agent) (some? owner-id)))]]

    :ha/conversation
    [:map {:closed true}
     [:id :ha/id]
     [:title (text 1 120)]
     [:member-ids [:vector {:min 1 :max 32} :ha/id]]
     [:created-by :ha/id]
     [:created-at :ha/timestamp]]

    :ha/message
    [:map {:closed true}
     [:id :ha/id]
     [:conversation-id :ha/id]
     [:author-id :ha/id]
     [:text :ha/message-text]
     [:delivery :ha/delivery]
     ;; character spans the delivery may stress (used by :headline)
     [:emphasis [:vector {:max 8} :ha/span]]
     [:sent-at :ha/timestamp]
     [:in-reply-to optional :ha/id]]

    ;; Whether a member is working on a reply in a conversation, so nobody
    ;; is left in silence. One document per member per conversation.
    :ha/presence
    [:map {:closed true}
     [:member-id :ha/id]
     [:conversation-id :ha/id]
     [:state [:enum :idle :thinking]]
     [:since :ha/timestamp]]

    ;; --- performance (derived, never persisted) ----------------------------
    ;; halfahead.perform builds a timeline from text, delivery, emphasis
    ;; and a seed. Invariants beyond this shape, checked by tests: the
    ;; beats' spans partition [0, (count text)), and every beat ends by
    ;; :duration-ms, so the final frame shows exactly the text.
    :ha/beat
    [:map {:closed true}
     [:span :ha/span]
     [:effect :ha/effect]
     [:at-ms [:int {:min 0}]]
     [:ms [:int {:min 0}]]]

    :ha/timeline
    [:map {:closed true}
     [:duration-ms [:int {:min 0 :max max-performance-ms}]]
     [:beats [:vector {:min 1} :ha/beat]]]

    ;; --- the worker's engine contract --------------------------------------
    :ha/turn
    [:map {:closed true}
     [:role [:enum :human :agent]]
     [:text :ha/message-text]]

    :ha/engine-request
    [:map {:closed true}
     [:persona (text 1 8000)]
     [:history [:vector {:max 200} :ha/turn]]]

    ;; What an engine must return. :emphasis holds phrases, which the worker
    ;; resolves to spans in :text; a phrase not found is dropped.
    :ha/engine-reply
    [:map {:closed true}
     [:text :ha/message-text]
     [:delivery :ha/delivery]
     [:emphasis [:vector {:max 8} (text 1 200)]]
     [:cost-usd optional [:and number? [:>= 0]]]]

    ;; --- the typing lab (derived, never persisted) -------------------------
    ;; The lab renders the draft being typed as letters in 3D space. The
    ;; pure core turns edits into glyphs with stable identities, words into
    ;; cues, and (glyphs, cues, t) into a frame of poses; a renderer only
    ;; draws frames. Invariant beyond these shapes, checked by tests: the
    ;; live (non-ghost) poses of a frame, in order, spell exactly the draft.

    :ha/finite      [:and number?
                     [:fn {:error/message "must be finite"}
                      #(and (== % %) (not (#{##Inf ##-Inf} %)))]]
    :ha/unit        [:and :ha/finite [:>= 0] [:<= 1]]

    ;; One character of the draft: a UTF-16 unit, or a surrogate pair kept
    ;; whole. :id is stable across edits and never reused.
    :ha/glyph
    [:map {:closed true}
     [:id [:int {:min 0}]]
     [:char [:string {:min 1 :max 2}]]
     [:born-ms :int]]

    ;; A deleted glyph on its way out, remembered where it last stood.
    :ha/ghost
    [:map {:closed true}
     [:id [:int {:min 0}]]
     [:char [:string {:min 1 :max 2}]]
     [:died-ms :int]
     [:index [:int {:min 0}]]]

    ;; What a finished word does. Closed, like deliveries: lookups choose,
    ;; renderers draw, nobody invents motion in between.
    :ha/word-effect [:enum :pop :launch :shimmer :skitter :quake :melt
                     :rewind :fuse :jitter :glow :wobble]

    :ha/word-cue
    [:map {:closed true}
     [:word (text 1 64)]
     [:effect :ha/word-effect]
     [:intensity :ha/unit]
     [:source [:enum :glossary :punctuation :shape :default]]]

    ;; A cue placed on the draft: which glyphs it moves, and when it fired.
    :ha/placed-cue
    [:map {:closed true}
     [:glyph-ids [:vector {:min 1} [:int {:min 0}]]]
     [:cue :ha/word-cue]
     [:at-ms :int]]

    ;; How the whole draft moves: a named set of physics parameters
    ;; (tempo, springiness, amplitude, drift) that every effect is tuned by.
    ;; :auto means inferred from the writer's own typing rhythm.
    :ha/style [:enum :plain :elegant :caffeinated :drunken :coarse]
    :ha/style-choice [:enum :auto :plain :elegant :caffeinated :drunken :coarse]

    ;; How a finished phrase is staged, like a trailer editor's cut. Closed:
    ;; a director (rules now, an on-device model later) chooses; the pure
    ;; core performs.
    ;;   :line  stays in the line (the default)
    ;;   :card  takes over the stage, big, a beat per word, then folds back
    ;;   :push  the camera moves in on the phrase, then returns
    ;;   :pull  the camera pulls back to reveal the paragraph, then returns
    :ha/shot [:enum :line :card :push :pull]

    :ha/direction
    [:map {:closed true}
     [:glyph-ids [:vector {:min 1} [:int {:min 0}]]]
     [:shot :ha/shot]
     [:energy :ha/unit]
     [:at-ms :int]
     [:source [:enum :rules :model]]]

    :ha/draft
    [:map {:closed true}
     [:text :string]
     [:glyphs [:vector :ha/glyph]]
     [:ghosts [:vector :ha/ghost]]
     [:cues [:vector :ha/placed-cue]]
     [:next-id [:int {:min 0}]]
     [:directions optional [:vector :ha/direction]]]

    ;; One glyph as the renderer should draw it at a moment. Units: em
    ;; (1 = the font size); angles in radians. :wobble is the amplitude of a
    ;; jelly deformation the renderer applies to the glyph's own quad.
    :ha/glyph-pose
    [:map {:closed true}
     [:id [:int {:min 0}]]
     [:char [:string {:min 1 :max 2}]]
     [:ghost? :boolean]
     [:x :ha/finite] [:y :ha/finite] [:z :ha/finite]
     [:rx :ha/finite] [:ry :ha/finite] [:rz :ha/finite]
     [:scale [:and :ha/finite [:>= 0]]]
     [:opacity :ha/unit]
     [:glow :ha/unit]
     [:wobble :ha/unit]]

    ;; Where the camera is and what it looks at, in the same em units as the
    ;; poses. Part of the frame so shots are data and testable; a renderer
    ;; follows it and decides nothing.
    :ha/camera
    [:map {:closed true}
     [:x :ha/finite] [:y :ha/finite] [:z :ha/finite]
     [:tx :ha/finite] [:ty :ha/finite] [:tz :ha/finite]
     [:fov [:and :ha/finite [:> 0] [:< 180]]]]

    :ha/glyph-frame
    [:map {:closed true}
     [:t-ms :int]
     [:poses [:vector :ha/glyph-pose]]
     [:caret [:map {:closed true} [:x :ha/finite] [:y :ha/finite]]]
     [:camera optional :ha/camera]
     [:style optional :ha/style]
     ;; How much of the stage has been revealed: level 0 is a familiar
     ;; flat chat box with the house lights up (:lights 0); level 3 is the
     ;; full dark stage (:lights 1). See docs/product (experiment 1).
     [:reveal optional [:map {:closed true}
                        [:level [:int {:min 0 :max 3}]]
                        [:lights :ha/unit]]]]}))

(def ^:private compiled
  (memoize (fn [k] (m/schema k {:registry registry}))))

(defn schema
  "The compiled Malli schema for :ha/* keyword k. For codecs that walk an
  entity's keys; callers checking data use valid? and explain."
  [k]
  (compiled k))

(defn valid?
  "True when x conforms to the shape named by k."
  [k x]
  (m/validate (schema k) x))

(defn explain
  "nil when x is a valid k, otherwise a humanized error map."
  [k x]
  (some-> (m/explain (schema k) x) me/humanize))
