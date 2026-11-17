(ns halfahead.lab.instance
  "The glyph mesh's vertex layout, as data, so a test can count it.

  Every per-instance attribute is a vertex buffer of its own, beside the
  quad's position and uv and the instance matrix, and WebGPU guarantees a
  pipeline only eight vertex buffers. WebGL2 has no such limit: with ten
  (one attribute per channel) the lab drew every letter on WebGL2 and
  nothing at all on WebGPU, and no check noticed, because the check's
  browser ended on WebGL2.

  So a new channel goes into a spare component of a packed attribute here
  (then the shader and lab.render's draw! read and write it), never into a
  new attribute. The test of this namespace keeps one buffer to spare.")

(def limits
  "What WebGPU guarantees every device: the minimums of maxVertexBuffers and
  maxVertexAttributes. A device may offer more; the lab may not count on it."
  {:max-vertex-buffers 8 :max-vertex-attributes 16})

(def attributes
  "The per-instance attributes of the glyph mesh, in order: :name as the
  shader knows it, :width in floats (a vec of that size), and :holds, the
  channels in it; a channel that is a vector itself (a rectangle, a
  colour) fills the attribute."
  [{:name "glyphRect" :width 4 :holds [:rect]}
   {:name "glyphLook" :width 4 :holds [:opacity :glow :wobble :ghost]}
   {:name "glyphShape" :width 3 :holds [:weight :blur :fill]}
   {:name "glyphInk" :width 3 :holds [:ink]}])

;; The quad's own buffers: position and uv. It has no normal: nothing lights
;; a letter, and an unused normal is still a buffer, so lab.render deletes it.
(def ^:private quad-buffers 2)

;; three.js binds an instanced mesh's matrix as one buffer, always, and a
;; mat4 takes four attribute slots (one per column).
(def ^:private matrix-buffers 1)
(def ^:private matrix-slots 4)

(defn vertex-buffers
  "How many vertex buffers the glyph pipeline binds."
  []
  (+ quad-buffers matrix-buffers (count attributes)))

(defn vertex-attribute-slots
  "How many vertex attribute slots the glyph pipeline uses."
  []
  (+ quad-buffers matrix-slots (count attributes)))
