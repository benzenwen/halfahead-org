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
          finished phrase --> :ha/direction (lab.director)
```

- **`:ha/draft`**: the text, one glyph per character with a stable id and
  the time it was typed, ghosts of deleted characters, and cues placed on
  finished words, and directions placed on finished phrases.
- **`:ha/direction`**: how a finished phrase is staged, one shot from a
  closed set (`:line :card :push :pull`) with an energy; see Directing.
- **`:ha/word-cue`**: what a finished word does, chosen from a closed set
  (`:pop :launch :shimmer :skitter :quake :melt :rewind :fuse :jitter
  :glow :wobble`). Lookup order: Half Ahead's glossary, punctuation, the
  word's shape, a quiet default. All on the device, instantly; a local
  model can join later behind the same function.
- **`:ha/glyph-frame`**: for every letter at time *t*, its position and
  rotation (em units, radians), scale, opacity, glow, and a *wobble*
  amplitude the renderer applies as a jelly deformation of the glyph's
  own quad. Plus the caret, and the `:ha/camera` (position, target and
  field of view, in em), so where the frame is seen from is data too.

Invariants, tested: the live letters of every frame spell exactly what is
in the textarea; every number is finite; everything settles exactly onto
the layout within 2.5 s of the last event, in every style, and within
5 s of the latest direction, and within 900 ms of a reveal level being
reached; reduced motion means no motion and no shots;
apart from a card, which takes a phrase out of the line on purpose, no
letter ever moves more than half an em sideways from its place, so
a word's effect never reaches its neighbours (effects that gather or
scatter, like `:fuse`, move in depth and height instead); every frame has
a valid camera, a pure function of the draft, the time and the options,
whose target is on the current line 450 ms after that line's first letter
(and at once with reduced motion); and lines receded by the crawl settle
exactly onto their receded place.

**For a renderer author (Jon):** implement "take a `:ha/glyph-frame`, draw
it" in anything (WebGPU compute, raymarched lightning, cloth). Nothing
upstream changes. The reference renderer (`halfahead.lab.render`) is
three.js with an SDF glyph atlas, deliberately plain, so there is something
real to replace.

## The reveal

Experiment 1 of the product outline: start with a box that looks like
every chat box, and let it show what it can do as it is used. Four levels,
decided in `halfahead.lab.reveal` (pure) and carried in every frame as
`:reveal {:level :lights}`:

| Level | What you see | Reached by |
|---|---|---|
| 0 | flat and familiar: ink on paper (`#f6f1e7`), the camera straight on, no floor, each letter only a third of its usual pop, in the plane; words do nothing | the start |
| 1 | small in-line word effects (the iMessage level): cues perform, but in the plane only (no depth, no turning out of it), within the half-em bound | 3 finished words |
| 2 | depth: cues and births in full, the floor fades in and the house lights go down to the dark stage; the director starts reading phrases | 4 cues that are not the quiet default (glossary, punctuation or shape) |
| 3 | the full stage: shots (cards, pushes, pulls) and the crawl | a phrase directed with a shot other than `:line` |

**Wands.** Ctrl+. or a mouse flick over the stage (at least 600 px in
under 250 ms; slow pointing never counts) raises the level by one, from
whatever it is then. **`?reveal=N`** (0 to 3) starts there; a plain
`/lab/` starts at 0. Levels never go down: the shell passes back the
levels reached so far (`:reached`), so deleting words, which takes their
cues away, cannot undo a level.

Level 3 counts only a spectacle placed after level 2 on a phrase with a
letter written since: the director's first pass at level 2 may direct a
phrase finished earlier, and that card neither opens level 3 nor is
performed. Every birth, cue and shot is performed at the level in force
when it happened, so reaching a level never jerks a letter already moving.

**The transition is the moment.** On reaching level 2, the house lights
(`:lights`, 0 up to 1 down) go down over 900 ms, eased; the background,
the ink, the fog and the floor follow them (the floor only from level 2),
the page's own chrome switches palette at the half-way point
(`<html data-lights>`), and the camera tilts from straight on to its 2
degrees. Every letter then on the page peels off it: a lift of 0.6 em that
settles over 700 ms. On reaching level 3 the crawl eases in over 900 ms.
Started at a level with `?reveal`, none of this plays; it is just there.
With reduced motion levels still advance, but the lights are at the
level's value at once, nothing peels and the crawl is just there.

`pose/frame` without `:reveal-opts` is the full stage (level 3), so every
invariant below is tested at every level, started anywhere and with
wands.

## Staging

The camera is decided in `halfahead.lab.pose`, as part of every frame; the
renderer puts its camera where the frame says and decides nothing. It looks
at the line being written (the line of the last letter): its target is
the centre of the column (the layout width) on that line's baseline, not
the centre of the live text, so a line grows rightward from a fixed start
and never moves while you type. When a new line starts, the target eases
from the line before to the new one over 450 ms, timed from the birth of
the new line's first letter (with reduced motion it is there at once). It
is derived from the draft alone, so deleting back to an earlier line snaps
rather than eases. The camera sits in front of the target, looking down 2
degrees on the dark stage (straight on with the house lights up; 40
degree field of view), at the distance where the column fills
75% of the view's width for the viewport's aspect (`:aspect`, which the
shell passes in), so letters stand upright at rest, within 2 degrees of
vertical anywhere in the block. Type is sized so a line (18 em, about
36-40 characters) fills about that width. A faint grid floor (the grid
stage; see Writing a stage) recedes under the current line and fog dims what is further from the eye, so z
motion reads as depth. (In this scene +z points toward the camera, so
births and launches come forward and brighten.)

### The crawl

At level 3, a draft taller than the screen does not fall off the top (below
it, older lines scroll up like a chat box's): like an end-credits crawl, finished lines recede up and back into the dark. The
current line and the three above it are untouched; each line beyond that
moves back 0.9 em, loses 0.18 of its opacity (never below 0.12) and tilts
back 0.1 rad (never past 0.5 rad), one step more per line. The recession is
part of each letter's resting pose, so it holds when settled, every motion
still ends exactly on it, and it slides along with the camera's 450 ms ease
when a new line starts.

## Writing a stage

The scene behind the letters is a *stage*, a module you can swap: the grid
floor is the first (`halfahead.lab.stage.grid`), and a storm, a city or a
field of stars can be another. `/lab/?stage=name` picks one; an unknown or
missing name is the grid. The renderer (`halfahead.lab.render`) owns the
three.js scene, the camera, fog and house lights, and draws the letters;
it calls the stage on every frame.

A stage is a map of four functions over a state of its own (a map):

```clojure
{:init    (fn [three-scene opts] state)  ; add your objects to the scene
 :update  (fn [state frame t] state)     ; every frame, before it is drawn
 :moment  (fn [state moment] state)      ; each moment, before :update
 :dispose (fn [state] nil)}              ; remove and free your objects
```

- **`opts`** is `{:ink node}`: a TSL colour uniform holding the ink's
  colour, which follows the house lights (dark ink on paper, light ink on
  the dark stage). Use it if your scene should be drawn "in ink".
- **`frame`** is the `:ha/glyph-frame` being drawn, read only: `:t-ms`,
  `:poses` (every letter's place), `:caret`, `:camera` (`:x :y :z`, target
  `:tx :ty :tz`, `:fov`, in em; +z toward the viewer), `:style` and
  `:reveal` (`:level` 0-3 and `:lights`, 0 up to 1 down). `t` is its
  `:t-ms`, the page clock in ms. The grid, for one, rests under the camera
  target and shows only from level 2, as far as the lights are down.
- **A moment** is something that just happened, from
  `halfahead.lab.moments`, as plain data:

  ```clojure
  {:kind :cue   :effect :quake :intensity 1.0 :word "crash" :at-ms 12345}
  {:kind :shot  :shot :card :energy 0.9 :at-ms 12345}
  {:kind :level :level 2 :at-ms 12345}
  ```

  A cue fired on a finished word (effects as in `:ha/word-cue`), a
  direction placed on a finished phrase (`:line :card :push :pull`), a
  reveal level reached. Each arrives exactly once, in time order, a cue
  before a shot before a level at the same ms; a word that only lost
  letters is not a new moment, and a word typed again is. Ignore what you
  do not care about.

Keep your timing derived from `t` and the `:at-ms` you kept (for example
`brightness = intensity · decay(t - flash-at)`), not counted per frame, so
a slow frame never makes an effect last longer. The grid's whole reaction
is this: a `:quake` cue brightens its lines for 900 ms in proportion to
the intensity (a hint of lightning), and a `:card` shot pulses its far
lines once.

To add one, write `src/halfahead/lab/stage/yours.cljs` exporting
`(def stage {...})` and add it to `registry` in `halfahead.lab.stage`:

```clojure
(def registry {:grid grid/stage :yours yours/stage})
```

The rules:

- **A stage decides nothing about the letters.** Not where they are, not
  the camera, not timing, not the text. It only draws behind them, and it
  must keep them readable.
- **At rest it is at rest.** With no moments it looks the same from frame
  to frame (or close to it); with reduced motion the shell sends it no
  moments at all, so it never flashes or pulses then.
- **Clean up.** `:dispose` removes and frees everything `:init` added; the
  renderer calls it when it falls back from WebGPU to WebGL2, then calls
  `:init` again on the new scene.
- **Show your state.** Keep numbers, keywords and strings in the state map
  for what you animate; `window.__lab.stageState()` returns those (the JS
  objects are left out), which is how the e2e checks the grid's flash.

## Styles

A style is a named set of physics parameters (`halfahead.lab.style`) that
every birth and every cue is performed in. It is applied around the plain
motion: real time is mapped onto the plain shape's time, and the plain
displacements are scaled, so the effects themselves stay one closed set.

| Style | Feel | Tempo | Overshoot | Amplitude | Sway | Steps |
|---|---|---|---|---|---|---|
| Plain | the lab's motion as it was | 1.0 | 1.0 | 1.0 | none | smooth |
| Elegant | a glide: slower, no overshoot, everything eased | 1.5 | 0 | 0.8 | none | smooth |
| Caffeinated | quick, springy, a small fast tremor that dies away | 0.6 | 1.8 | 1.15 | 0.03 em every 45 ms | smooth |
| Drunken | slow, loose, a lazy sway that dies away | 1.4 | 1.6 | 1.1 | 0.12 em every 900 ms | smooth |
| Coarse | fast and blunt: three hard steps, no easing | 0.5 | 1.0 | 1.25 | none | 3 |

- **Tempo** multiplies every duration, staggers included. The slowest
  (elegant) cue still lands within 1.5 × (100 + 1400) = 2250 ms, which is
  why the settle bound is 2.5 s in every style.
- **Overshoot** is how much of the birth spring's overshoot is kept (0 is a
  smoothstep). Below 1, a cue's progress is eased too.
- **Amplitude** multiplies displacement (position, rotation, scale), not
  opacity, glow or wobble.
- **Sway** is a drift around the letter's place while it moves, fading as
  (1 - p)² so it is gone before the motion ends.
- **Steps** holds the motion in that many still poses.

Every invariant above holds in every style. In particular, the half-em
clamp is applied after the style, so sway and amplitude can never push a
letter into its neighbour's room. Each birth is performed in the style in
force when the letter was born, and each cue in the style in force when it
fired, so a change of style never jerks a letter already moving. Embers
and the camera's line ease are not styled.

**Auto** (the default) infers the style from how you type. It looks at the
last 12 letters born in the last 6 s (letters pasted together count as one
keystroke) and takes the median gap between them and how much the gaps vary
(standard deviation over mean, with 0.5 as the line between steady and
erratic):

| Median gap | Steady | Erratic |
|---|---|---|
| under 110 ms | Caffeinated | Coarse |
| 110 to 320 ms | Plain | Plain |
| over 320 ms | Elegant | Drunken |

With fewer than 4 gaps it is Plain. A letter's own style is read only from
the letters born before it, so typing on never restyles what you already
wrote. This is local and private: the rhythm is read from the birth times
already in the in-memory draft, and nothing is recorded, stored or sent.

A quiet picker in the bottom-right corner ("Motion style": Auto, Plain,
Elegant, Caffeinated, Drunken, Coarse) chooses. Ctrl+1 to Ctrl+6 choose in
the same order. Either way the textarea keeps (or gets back) the focus. On
some systems the browser keeps Ctrl+digit for switching tabs; the picker
always works.

Every frame names the style in force at its time (`:style`).

## Directing

Ben: "Think first about using the whole space. Then restraint. Like the
best movie trailers." The director (`halfahead.lab.director`, pure) reads
each finished phrase and chooses how it is staged. The shell calls
`(direct draft now)` after every edit and on every frame (for phrases a
pause ended). Rules only, on the device; a local model can later sit
behind the same function.

A **phrase** is the run of letters after the previous phrase's end, up to
a `.`, `!`, `?` or newline, or, once no letter has been born for 1200 ms,
up to the last letter. A phrase a pause ended stays ended when typing
resumes. Its glyph ids are its non-whitespace letters; a phrase with no
word (`?!`, `...`) is not directed, and a phrase is directed once, however
it is edited later.

| First match | Shot | Energy |
|---|---|---|
| ends in `!`, or more than half its words in CAPITALS | `:card` | 0.9 |
| ends in `?` | `:push` | 0.6 |
| 12 or more words, ending in `.` | `:pull` | 0.5 |
| anything else | `:line` | 0.2 |

**The spectacle budget.** A shot other than `:line` is placed only if the
previous one is at least 10 s earlier; otherwise the phrase gets `:line`.
So the big moments stay rare and land, and a paste of several `!`
sentences gives one card. This is tested as a property over any sequence
of edits and times. Directions keep only the ids of letters still present,
and one left with none is dropped, as cues are, with one exception: a
spectacle whose letters were all deleted stays, unperformed, until its 10 s
are up, because it is the only record of when the last spectacle was.
(Otherwise deleting a card and typing `!` again would fire another at
once.)

`pose/frame` performs the shots, at most one at a time (the latest begun):

- **`:card`**: the phrase's letters leave the line for the centre of the
  view (the camera's target), set large enough that the phrase spans 70%
  of the view's width (at most 8 times its size), one word per 220 ms beat
  (shorter beats for long phrases), held 1200 ms, then folded back to the
  line over 400 ms. Every other letter fades to at most 0.08 and recedes
  2 em behind the line meanwhile (so perspective shrinks it), then
  returns exactly. Under
  4500 ms in all. Positions assume the renderer scales a glyph about the
  middle of its advance and 0.35 em above its baseline.
- **`:push`**: the camera comes 35% nearer, looking at the phrase's centre,
  over 500 ms, holds 700 ms, and eases back over 500 ms.
- **`:pull`**: the camera goes 40% further back over 600 ms, holds 900 ms,
  and eases back over 600 ms.
- **`:line`**: nothing extra.

Shots are blended on top of births and cues, so everything is exactly at
rest 5 s after the latest direction. With reduced motion no shot is
performed.

## Sound

Experiment 3 of the product outline: sound to calm, to excite, to
intrigue. Every new word cue and every new direction can sound, voiced by
the motion style in force (the one picked, or the one Auto reads from the
last frame). Sound is data (`halfahead.lab.sound`, pure and tested on both
hosts): a few voices, each `{:wave :hz :at-ms :ms :gain :detune :pan}`,
with `:wave` one of `:sine`, `:triangle`, `:square` or `:noise` (for noise,
`:hz` is a lowpass cutoff). `halfahead.lab.audio` is the only Web Audio
code; it synthesizes every voice with an oscillator or a shared buffer of
white noise, with a short attack and release.

Each effect has a little figure, a contour of notes. `:pop` (and so the
default cue, `:pop` at 0.3) is a single note and the quietest; `:quake`,
`:launch` and `:shimmer` have the most notes and the widest contours.
Intensity raises the level and draws the figure out a little. The style
voices the figure:

| Style | Sound | Feel |
|---|---|---|
| Plain | the figure's first note only, one soft sine blip | neutral |
| Elegant | sines, long and swelling in, the contour snapped to unisons, fourths, fifths and octaves | calms |
| Caffeinated | short bright triangle ticks (60 ms at most), quicker, left and right in turn | excites |
| Drunken | each note a pair of sines detuned 18 cents apart, beating in a slow wobble, 40 ms late, spread left and right | intrigues |
| Coarse | a noise burst over a low square thud | thumps |

Shots: `:line` is silent; `:card` is a trailer hit, a noise swell rising
in level and brightness, then a 55 Hz thump (square in Coarse, triangle in
Caffeinated, a sine otherwise), over within 800 ms; `:push` is a tone
rising in three steps, `:pull` one falling. Energy raises the level;
Drunken detunes them.

The bounds, enforced on every sound and tested as properties over
generated cues and directions: at most 900 ms long, each voice's gain at
most 0.12 and their sum at most 0.25, 40 to 4000 Hz, pan within -1..1.

A **Sound** checkbox beside the motion style picker turns it on. It is off
by default and never autoplays: the AudioContext is made by the click that
turns it on, and if it was left on in an earlier visit, nothing sounds
until the first key or click on the page. The choice is remembered for
this viewer on this device only (localStorage `halfahead.lab.sound`; the
page works the same when storage is blocked, with Sound off). Nothing is
fetched, recorded or sent. Every sound is redundant with what is seen: it
only accompanies a cue or shot that is drawn anyway.

`window.__lab.soundLog()` returns the last 20 sounds played, as data
(`{kind: "cue"|"shot", style, sound, ...}` with the cue's word, effect and
intensity, or the shot and energy); it is empty while Sound is off.
`window.__lab.bounds()` returns the bounds.

## Accessibility

The input is a real `<textarea>`, focused, with transparent text: IME,
paste, undo, spellcheck and screen readers keep working. The canvas is
`aria-hidden`. Reduced motion shows letters at rest. Sound is opt-in, a
labelled checkbox, and every sound has a visible counterpart.

## Debug overlay

Open the lab as `/lab/?debug` (or `?debug=1`) and a small panel in the
bottom-left corner shows, on every frame:

```
frame 1234 · t 12.345 s · 60 fps · mean 16.6 ms · worst 18.0 ms
WebGPU · 36 glyphs · style caffeinated (auto)
shipped -> launch 1.0 (glossary)
we -> pop 0.3 (default)
WHAT A DAY! -> card 0.9
camera target x 9.00 · y -5.40 · z 0.00
reveal level 3 · lights 1.00
stage grid · moment cue quake 1.0 at 12.345 s
sound off
```

- **frame**: frames drawn since the page loaded.
- **t**: the page clock (`performance.now()`), the same time the frame
  was computed for.
- **fps, mean, worst**: over the last second, the frame rate and the mean
  and longest gap between consecutive frames. A hitch shows as a high
  worst.
- **backend and glyphs**: what the renderer is using and how many glyph
  instances it drew in that frame.
- **style**: the motion style in force, with `(auto)` when the rhythm
  chose it.
- **cues**: up to six cues fired in the last eight seconds, newest first,
  each as word -> effect, intensity, and the source that chose it.
- **directions**: likewise up to six recent directions, newest first, each
  as the phrase (its first 16 characters) -> shot and energy.
- **camera target**: where this frame's camera looks, in em.
- **reveal**: the frame's reveal level and house lights (0 up, 1 down).
- **stage**: the stage behind the letters and the newest moment it was
  given (`moment none` before the first); see Writing a stage.
- **sound**: whether Sound is on.

The panel grows as wide as its longest line, up to the viewport, and wraps
beyond that, so no line is cut off.

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

Add `?reveal=3` to the URL for the full stage at once (see The reveal).

`bb lab` serves `public/` on a free port and prints the URL of
`/lab/index.html` (the local server maps only `/` to an index); Ctrl-C
stops it. `bb lab:e2e` runs every step on the full stage (`?reveal=3`)
except the reveal's. It types a sentence, backspaces, waits for everything
to settle (then asserts every letter is upright and the block centred),
types on until the block wraps (still centred). On a plain `/lab/` it
then walks the reveal: level 0 with a paper background (the luminance of
one screenshot pixel at the left edge, decoded from the PNG, since a WebGPU
canvas cannot be read back reliably); level 1 after "one two three ";
level 2 after "deploy crash merge finally " and a dark background 1.5 s
later; level 3 after "WHAT A DAY! " with the card performed (a letter of
it at scale > 1.5), leaving `reveal-0.png` to `reveal-3.png`. On a fresh
plain page Ctrl+. raises level 0 to 1, a slow sweep of the mouse does
nothing, and a synthesized flick (pointer moves dispatched at once)
raises it to 2. It types 14 lines on a fresh
page and checks that every letter is either on screen or dimmed to 0.3
opacity or less and that the current line is in the middle 60% of the
height, types "This is a merge
of a " on a fresh page while sampling where the line's start lands every
100 ms (it may not move more than 2 px), types "This is a merge " on
another with the page's clock held (Playwright's clock API) and steps
through the `:fuse` in 50 ms slices, checking at each that no letter of
"merge" lands left of the "a" before it, opens `?debug` and checks
the overlay (frame number, fps, backend, the style in force, and a
`shipped -> launch` line; a camera target line, a `reveal level 3`
line, and no line cut off;
without the flag it must not show). It then types fast (40 ms a key) and
then slowly (450 ms a key) on a fresh page with the clock held, checking
that Auto reads caffeinated and then elegant. On one more page it picks
each explicit style, with the picker or with Ctrl+4 and Ctrl+6, checks
that the textarea keeps the focus, and types "we shipped it " to check
that the frames are performed in that style. On a `?debug` page it checks
that Sound starts off (overlay `sound off`) and that typing "crash " then
logs nothing; clicks Sound on (the textarea keeps the focus), types
"crash " and checks that exactly one sound, a `quake` cue, was logged with
every voice within `__lab.bounds()`; types "WHAT A DAY! " and checks for a
card hit with a noise swell and a low thump; reloads to see Sound still
on, then turns it off and reloads to see it off. With the clock held on
another page it directs: after "hello there " and a pause, it types
"WHAT A DAY! " and checks that 900 ms later a letter of the card is at
scale > 2, every other letter at opacity ≤ 0.08 and the letters of "hello
there" at least 1.9 em behind where they settle (`card.png`), and that
5 s later all are at scale 1; types "why not? " within 10 s of the card
and checks it was directed `:line` (the budget); and 11 s after the card
types "is it? " and checks that a `:push` brought the camera at least 25%
nearer its target (`push.png`). Finally it repeats with reduced motion (on every frame the camera target
is on the current line and drawn at the canvas centre), asserting on
`window.__lab` along the way; it leaves `typing-mid.png`, `embers.png`,
`settled.png`, `wrapped.png`, `reveal-0.png` to `reveal-3.png`, `crawl.png`, `steady.png`, `card.png`, `push.png`, `fuse.png` (taken at the
first slice where a letter of "merge" is more than 0.05 em off the line,
so it always shows the fuse in flight), `debug.png`, `style-<name>.png` for
each explicit style, and `style-auto-fast.png` and `style-auto-slow.png` in
`out/lab/`, and stops the server it started. It is not part of
`bb test`.

The status line names the backend. `halfahead.lab.render` asks three.js's
`WebGPURenderer` for WebGPU, which falls back to WebGL2 when the browser
has none. Some browsers expose a WebGPU older than this three.js expects,
which only shows when drawing, so the renderer draws one empty frame first
and starts over on WebGL2 if that fails. One TSL material serves both
backends.

The page is offline: no network, no downloaded fonts (the glyph atlas is
rasterised with tiny-sdf from the system's sans faces), no audio files:
sound, when turned on, is synthesized.
`window.__lab` exposes `backend`, `liveText()`, `lastFrame()`,
`directions()` (the draft's directions), `drawn()`, `lean(i)` (degrees between drawn glyph i's projected left edge
and the screen vertical) and `blockCentre()` (the block's drawn centre,
where the frame's camera target lands, and the viewport size, in CSS
pixels),
`project(x, y, z)` (where a point in em landed on the canvas, in CSS
pixels), `advance(c)` (a character's advance in em), `soundLog()` and
`bounds()` (see Sound) for tests and for anyone poking at
it from the console.
