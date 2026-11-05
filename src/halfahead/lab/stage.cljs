(ns halfahead.lab.stage
  "The scene behind the letters, as a pluggable stage. A stage is a map of
  four functions over a state of its own:

    {:init    (fn [three-scene opts] state)  ; add its objects to the scene
     :update  (fn [state frame t] state)     ; every frame, before drawing
     :moment  (fn [state moment] state)      ; each moment, before :update
     :dispose (fn [state] nil)}              ; remove and free its objects

  opts is {:ink n}, n a TSL colour uniform that follows the house lights.
  frame is the :ha/glyph-frame being drawn (read it, never change it), t
  its :t-ms, and a moment one of lab.moments' maps, each delivered once.
  A stage only draws behind the letters: it decides nothing about them,
  the camera, timing or text, and with no moments it looks the same from
  frame to frame. lab.render owns the three.js scene and calls the stage;
  docs/LAB.md, 'Writing a stage', says more."
  (:require [halfahead.lab.stage.grid :as grid]))

(def registry
  "Every stage the lab can show, by the name ?stage= selects it with."
  {:grid grid/stage})

(def ^:private default-stage :grid)

(defn named
  "[k stage] for name, a string from ?stage= or nil; an unknown or missing
  name is the default, the grid, so a typo gives the plain floor."
  [name]
  (let [k (keyword name)]
    (if (contains? registry k)
      [k (registry k)]
      [default-stage (registry default-stage)])))
