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
the layout within 1.5 s of the last event; reduced motion means no motion;
a cued letter never moves more than half an em sideways from its place, so
a word's effect never reaches its neighbours (effects that gather or
scatter, like `:fuse`, move in depth and height instead).

**For a renderer author (Jon):** implement "take a `:ha/glyph-frame`, draw
it" in anything (WebGPU compute, raymarched lightning, cloth). Nothing
upstream changes. The reference renderer (`halfahead.lab.render`) is
three.js with an SDF glyph atlas, deliberately plain, so there is something
real to replace.

## Staging

The reference renderer stages the page rather than the letters: its
camera's axis passes through the centre of a fixed-width column (the
layout width), not of the live text (40 degree field of view, looking down
3 degrees), so letters stand upright at rest, within 2 degrees of vertical
anywhere in the block. Text is left-aligned in the column like a page, so a
line grows rightward and never moves while you type. A lens shift lifts the
block a little above the middle without tilting it, and the camera eases
upward once per new line (with reduced motion it moves straight there,
never gliding). Type is sized so a line (18 em, about 36-40
characters) fills about 70% of the width. A faint grid floor recedes under
the text and fog dims what is further from the eye, so z motion reads as
depth. This is presentation only: no new effects, nothing upstream changes.
(In this scene +z points toward the camera, so births and launches come
forward and brighten.)

## Accessibility

The input is a real `<textarea>`, focused, with transparent text: IME,
paste, undo, spellcheck and screen readers keep working. The canvas is
`aria-hidden`. Reduced motion shows letters at rest.

## Debug overlay

Open the lab as `/lab/?debug` (or `?debug=1`) and a small panel in the
bottom-left corner shows, on every frame:

```
frame 1234 · t 12.345 s · 60 fps · mean 16.6 ms · worst 18.0 ms
WebGPU · 36 glyphs
shipped -> launch 1.0 (glossary)
we -> pop 0.3 (default)
```

- **frame**: frames drawn since the page loaded.
- **t**: the page clock (`performance.now()`), the same time the frame
  was computed for.
- **fps, mean, worst**: over the last second, the frame rate and the mean
  and longest gap between consecutive frames. A hitch shows as a high
  worst.
- **backend and glyphs**: what the renderer is using and how many glyph
  instances it drew in that frame.
- **cues**: up to six cues fired in the last eight seconds, newest first,
  each as word -> effect, intensity, and the source that chose it.

It exists so that a screen recording, read as still frames, carries its own
numbers: what was felt can be tied to what the code did. It is display
only. Nothing is logged, sent or stored, and without the flag no stats are
kept at all. The panel is `aria-hidden` and ignores the pointer. The pure
part (`halfahead.lab.debug`) is tested on both hosts.

## Product voice

`src/halfahead/lab/glossary.cljc` is the list of words that do something
special. It is a first draft by the foreman; Ben owns it.

## Running it

```
bb lab        # compile the :lab build, serve public/, print the page's URL
bb lab:e2e    # the same page in headless Chromium (e2e/lab.mjs)
```

`bb lab` serves `public/` on a free port and prints the URL of
`/lab/index.html` (the local server maps only `/` to an index); Ctrl-C
stops it. `bb lab:e2e` types a sentence, backspaces, waits for everything
to settle (then asserts every letter is upright and the block centred),
types on until the block wraps (still centred), types "This is a merge
of a " on a fresh page while sampling where the line's start lands every
100 ms (it may not move more than 2 px), types "This is a merge " on
another with the page's clock held (Playwright's clock API) and steps
through the `:fuse` in 50 ms slices, checking at each that no letter of
"merge" lands left of the "a" before it, opens `?debug` and checks
the overlay (frame number, fps, backend, and a `shipped -> launch` line;
without the flag it must not show), and repeats with reduced motion (the
camera already on the block on the next frame), asserting on
`window.__lab` along the way; it leaves `typing-mid.png`, `embers.png`,
`settled.png`, `wrapped.png`, `steady.png`, `fuse.png` (taken at the
first slice where a letter of "merge" is more than 0.05 em off the line,
so it always shows the fuse in flight) and `debug.png` in `out/lab/` and stops the
server it started. It is not part of
`bb test`.

The status line names the backend. `halfahead.lab.render` asks three.js's
`WebGPURenderer` for WebGPU, which falls back to WebGL2 when the browser
has none. Some browsers expose a WebGPU older than this three.js expects,
which only shows when drawing, so the renderer draws one empty frame first
and starts over on WebGL2 if that fails. One TSL material serves both
backends.

The page is offline: no network, no downloaded fonts (the glyph atlas is
rasterised with tiny-sdf from the system's sans faces), no sound.
`window.__lab` exposes `backend`, `liveText()`, `lastFrame()`,
`drawn()`, `lean(i)` (degrees between drawn glyph i's projected left edge
and the screen vertical) and `blockCentre()` (the block's drawn centre,
where the camera aims, and the viewport size, in CSS pixels),
`project(x, y, z)` (where a point in em landed on the canvas, in CSS
pixels) and `advance(c)` (a character's advance in em) for tests and for anyone poking at
it from the console.
