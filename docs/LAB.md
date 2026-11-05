# The typing lab

A playground for making the text box itself fast, physical and fun: you
type, and your letters spring into 3D space; finished words do things;
deleted letters drift off as embers. Inspired by Jellyvision's kinetic
type, end credits, Jon's WebGPU and WebGL2 demos (jalfern.com), and mrdoob's
Codevember 2026 ("Fold", a draggable jelly sheet; then "Shatter", "Melt",
"Tangle"). Inspiration only: no code is copied from either.

## The seam

Everything that *decides* is pure Clojure (`.cljc`), tested on two hosts.
Everything that *draws* is a renderer that only draws frames. The contract
between them is data, defined in `src/halfahead/schema.cljc`:

```
textarea value --edit--> :ha/draft --frame(t)--> :ha/glyph-frame --> renderer
                 (lab.draft)   ^         (lab.pose)                (lab.render)
                               |
            finished word --> :ha/word-cue (lab.cues, from lab.glossary)
```

- **`:ha/draft`**: the text, one glyph per character with a stable id and
  the time it was typed, ghosts of deleted characters, and cues placed on
  finished words.
- **`:ha/word-cue`**: what a finished word does, chosen from a closed set
  (`:pop :launch :shimmer :skitter :quake :melt :rewind :fuse :jitter
  :glow :wobble`). Lookup order: Half Ahead's glossary, punctuation, the
  word's shape, a quiet default. All on the device, instantly; a local
  model can join later behind the same function.
- **`:ha/glyph-frame`**: for every letter at time *t*, its position and
  rotation (em units, radians), scale, opacity, glow, and a *wobble*
  amplitude the renderer applies as a jelly deformation of the glyph's
  own quad. Plus the caret.

Invariants, tested: the live letters of every frame spell exactly what is
in the textarea; every number is finite; everything settles exactly onto
the layout within 1.5 s of the last event; reduced motion means no motion.

**For a renderer author (Jon):** implement "take a `:ha/glyph-frame`, draw
it" in anything (WebGPU compute, raymarched lightning, cloth). Nothing
upstream changes. The reference renderer (`halfahead.lab.render`) is
three.js with an SDF glyph atlas, deliberately plain, so there is something
real to replace.

## Accessibility

The input is a real `<textarea>`, focused, with transparent text: IME,
paste, undo, spellcheck and screen readers keep working. The canvas is
`aria-hidden`. Reduced motion shows letters at rest.

## Product voice

`src/halfahead/lab/glossary.cljc` is the list of words that do something
special. It is a first draft by the foreman; Ben owns it.
