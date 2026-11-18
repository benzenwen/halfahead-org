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
- **`:ha/word-cue`**: what a finished word does, one of the nineteen
  gestures (see Gestures and patches). Lookup order: Half Ahead's
  glossary; elongation (a letter held three times or more, whatever its
  case, as in "loooove", swells: `:swell` at 0.6 for three, 0.1 more per
  further letter, up to 1); punctuation; the word's shape; a quiet
  default. All on the device, instantly; a local model can join later
  behind the same function.
- **`:ha/glyph-frame`**: for every letter at time *t*, its position and
  rotation (em units, radians), scale, opacity, glow, and a *wobble*
  amplitude the renderer applies as a jelly deformation of the glyph's
  own quad. A pose also carries `:sx :sy :skew :weight :blur :fill` while
  a gesture has one away from rest (see Gestures and patches). Plus the
  caret, and the `:ha/camera` (position, target and field of view, in
  em), so where the frame is seen from is data too.

Invariants, tested: the live letters of every frame spell exactly what is
in the textarea; every number is finite; everything settles exactly onto
the layout within 5 s of the last event, in every style, and within
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

- **Tempo** multiplies every duration, staggers included. The longest
  gesture a patch may be is 2400 ms plus a 900 ms stagger, so the slowest
  (elegant) cue still lands within 1.5 × (2400 + 900) = 4950 ms, which is
  why the settle bound is 5 s in every style, and at any style point:
  `style/from-point` never gives a tempo above 1.5 (see below).
- **Overshoot** is how much of the birth spring's overshoot is kept (0 is a
  smoothstep). Below 1, a cue's progress is eased too.
- **Amplitude** multiplies displacement (position, rotation, scale,
  stretch and slant), not opacity, glow, wobble, weight, blur or fill.
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
Elegant, Caffeinated, Drunken, Coarse, and Mine for the board's point)
chooses. Ctrl+1 to Ctrl+6 choose in
the same order. Either way the textarea keeps (or gets back) the focus. On
some systems the browser keeps Ctrl+digit for switching tabs; the picker
always works. The style last picked is remembered on the device
(`localStorage`, key `halfahead.lab.style`), Mine included, so a reload
starts in it; Auto when nothing, or anything else, is stored. Only the
writer's own picks are remembered (the picker, Ctrl+1 to Ctrl+6, the XY
pad, a flick): the tour's style steps are not.

Every frame names the style in force at its time (`:style`), unless the
choice is a style point (see The board), which has no name.

A style also sits at a point in the style space (`style/point`: energy,
flourish, softness, warmth), which the score's marks are performed and
dressed by. **Plain is plain motion with a restrained flourish**, 0.35:
its motion parameters are the lab's unstyled ones exactly, as they were,
but it performs a mark of amount 0.62 or more (a held word, a landing,
CAPITALS) and dresses asides and strong stresses (see Dress), never with
a versal. Plain is where Auto rests when the typing rhythm lapses, and
Auto is the default, so a visitor typing at an ordinary pace sees some of
the score. Bareness is the reveal's job: level 0 shows none of it.

Any point has motion parameters of its own, `style/from-point`: energy
sets the tempo, on straight lines through (0, 1.5), (0.2, 1.36),
(0.5, 1.0) and (1, 0.5), and the amplitude, through (0, 0.75), (0.5, 1.0) and (1, 1.25);
softness takes away overshoot (2 × (1 − softness)); no sway, no steps.
The middle of the space is Plain's motion exactly, and the calmest point
is as slow as Elegant and no slower, so the 5 s bound holds for every
point. `pose/frame` takes a point as its `:style-choice`: births and cues
then move by `from-point` of it, and the frame has no `:style`.

## The board

docs/product/score.md, "How people and agents drive it": people will not
tune parameters, so the lab gives them an instrument. The board is hidden
until asked for: the **Board** button in the bar, or Ctrl+B. Whether it
is out is remembered on the device (`localStorage`, key
`halfahead.lab.board`), like Sound and Pace. Everything it decides is in
`halfahead.lab.board` (pure, both hosts); the shell only turns events
into its data.

**Pads.** A toolbar labelled "Board" of seven real buttons, each showing
its name and its key:

| Pad | Key | What it says of the phrase |
|---|---|---|
| STRESS | Alt+1 | this matters |
| HOLD | Alt+2 | stay on it |
| ASIDE | Alt+3 | under the breath |
| TURN | Alt+4 | but |
| DOUBT | Alt+5 | not sure |
| LAND | Alt+6 | and that is that |
| BOOM | Alt+7 | the big moment: a card |

A pad is pressed with a pointer, with Enter or Space when it has the
focus (Tab reaches each), or with Alt+1 to Alt+7 from anywhere while the
board is out, the textarea included, which keeps its focus and gains no
character. A pointer on a pad never takes the focus from the textarea.

**The target** (`board/target text selection`) is what a pad acts on, a
`[from to)` span in UTF-16 units: the selection, trimmed of whitespace,
when it holds more than whitespace; otherwise the last phrase, from the
end of the previous sentence (after `. ! ? …` followed by whitespace, or
a newline; "3.5" stays whole) to the end of the text without trailing
whitespace, and no more than its last 12 words. With nothing to mark a
pad does nothing.

**A press** (`board/press pad point glyphs span t amount`) of a mark pad
places one cue on the glyphs wholly in the span, `:source :board`,
`:word` the span's text (at most 64 units): the gesture the style point
in force performs a mark of that kind and amount with
(`marks/gesture-for`). A pad is a deliberate act, so a style too spare
to perform the mark unasked still does: it gets the gesture the same
style would choose with every flourish. BOOM places a direction, a
`:card` of energy `amount`, on those glyphs. Both go into the draft as
typing's own do: logged in the overlay, voiced when Sound is on, and
gone when an edit removes their glyphs. A board cue counts as use for the
reveal like any cue.

**Amount** (`board/amount held-ms`) is how hard: 0.7 for a tap (up to
150 ms) rising evenly to 1.0 for a pad (or its key) held 600 ms or
longer. Every press therefore plays when it is released, on the pad it
went down on. `board/hold` keeps which pads are down, pointer holds and
key holds apart, and says which press a release completes, so an
abandoned press never plays: a pointer that leaves the pad, is cancelled
or comes up elsewhere, a pad that loses the focus with Enter still down,
and the window losing the focus all drop it, and a digit released plays
a pad only if Alt+digit put it down (a plain 1 types a 1). A click with
no pointer or key behind it (a screen reader's) is a tap. Holds are
timed by the events' own timestamps.

**A press is always seen.** On a fresh page the reveal level is 0 and
nothing performs. A mark pad leaves the level at 1 or more and BOOM at 3
(a card needs the stage), from the moment the press is placed: by the
cue's own use where that reaches it, else by as many wands as it takes,
so the level is kept like any other.

**The XY pad** is a square with a dot: the writer's style, energy across
and flourish up. Clicking or dragging sets the point, the arrow keys move
it by 0.05 when it has the focus, and it is a `slider` named "Style:
energy across, flourish up" whose value text reads like "Energy 60%,
flourish 40%". Moving it makes the style **Mine**: the picker gains that
option and shows it, and the frame is given the point as its
`:style-choice`, so marks are chosen, dressed and timed by it (softness
and warmth stay where the last named style left them; Plain's at first).
Picking a named style, by the picker or Ctrl+1 to Ctrl+6, moves the dot
to where that style sits (`style/point`). The point is remembered on the
device (`halfahead.lab.board.point`, read back only if it is a valid
`:ha/style-point`), and so is the style picked (see Styles), so a reload
starts in Mine with the dot where it was. The tour's own style steps
leave the dot alone. Sound has no voice for a point yet: under Mine it sounds as Plain.

**Narrow screens.** From 900 px up the board is a column in the right
margin, beside the text (which fills the middle 70%). Below that it
would cover the line being written, so it docks as a band along the
bottom, pads in two rows with the XY pad beside them, and the picker,
the debug panel and the tour's caption move up above it.

`window.__lab.board()` returns `{visible, point, target}`;
`__lab.cues()` the draft's placed cues and `__lab.textBox()` the box in
CSS pixels around the live letters as last drawn.

## The flick

docs/product/score.md, "How people and agents drive it", and H5 and H8
in docs/product/lab-notebook.md: nobody tunes four axes, but anybody can
say which of two readings they like better. The **Which?** button in the
bar, or Ctrl+/, asks for one exam on the text as it stands. The lab
never starts one by itself. The button is disabled with no text and with
reduced motion, where there would be nothing to compare.

**What it asks.** The lab reads the line back twice through the
read-back's own path (see Reading back): as **A**, the style in force
now, then as **B**, one step away along one axis, each at the viewer's
Pace and in its own point, dress included. A chip under the bar reads
"A" while A plays, "B" while B plays, then "A or B?" with two buttons.
ArrowLeft or the A button chooses A, ArrowRight or the B button chooses
B; the arrows choose nothing before both have played, and while an exam
runs they move neither the caret nor the XY pad. The text cannot be
edited while an exam runs (the textarea is read-only, and still holds
all of it). Escape at any time calls the exam off and changes nothing:
the style, the dress and what is stored are as they were. So does any
other command: a read-back, a pick of a style, the XY pad, a pad of the
board, the tour.

**What a choice does.** The learner (`halfahead.lab.learn`) moves toward
the reading chosen (`flick/decide`), and the style becomes **Mine** at
the learner's point: the picker shows it and the board's XY dot sits
there. The learner's state is kept in the page and remembered on the
device (`localStorage`, key `halfahead.lab.learn`, read back only if it
has the learner's shape); nothing leaves the device. The learner's
point is the lab's style point: an exam starts from the style in force
when it is asked for (a named style's point, the one Auto reads at that
moment, or the board's under Mine), and `flick/resume` carries the
learner on if that is where it already is, and otherwise starts it again
there. Moving the XY pad or picking another style therefore starts the
learning afresh from that style.

**How it ends.** `flick/offer` never offers a pair that would look the
same, and an axis whose step has fallen below 1/16 is finished. When no
axis has a pair left to tell apart on this line, the chip reads "Nothing
more to tell apart on this line" for two seconds and nothing else
happens. Another line may still have something to ask.

**What it can learn from a line** (H8). Energy (speed) and softness
(bounce) show on any line, since every letter moves by them. Flourish
shows only where the line gives it something to do: a mark to perform or
a phrase to dress; on a bare line it is not asked. Warmth is not learned
yet: in a style it decides one thing, whether a dressed stress is gold
or red, so its pair is almost never one that can be told apart.

The chip is a polite live region, always in the page, so a screen reader
hears "A", "B", "A or B?". `window.__lab.flick()` returns `{phase, axis,
pointA, pointB, flicks, point}`: `phase` is `"a"`, `"gap"`, `"b"` or
`"choose"`, and null with the offer's fields when no exam runs; `flicks`
and `point` are the learner's (0 and null before any choice).

## Gestures and patches

What a cue does is a **gesture**, and every gesture is a **patch**: plain
data (`:ha/patch`) in `halfahead.lab.gestures`, played by the one small
synthesizer in `halfahead.lab.synth`. `lab.pose` has no motion of its own
for cues; it asks the synthesizer what a patch does to each letter at
each instant, then applies the style. The design is in
`docs/product/score.md`.

A patch is:

- **`:ms`**, how long it moves one letter (50 to 2400);
- **`:stagger`** (optional): each letter starts `:ms-per-glyph` after the
  one before, in `:order` (`:forward`, `:reverse` or `:centre-out`),
  compressed so the last starts within `:max-ms` (900 at most);
- **`:mods`**, one to twelve modulations. Each moves one **channel** by an
  `:amount` (at intensity 1; the cue's intensity multiplies it) along a
  **curve** (`:one :rise :fall :fade :fade2 :arch :arc :rise-sine :spring
  :spring-fall`), inside a window `:from` to `:to` of the patch's time
  (the whole of it by default), optionally multiplied by an **oscillator**
  (`:osc`: a sine, cosine, triangle or noise with a period, phased the
  same for every letter, by position, or by letter identity for
  deterministic jitter). `:each :even` or `:odd` limits it to those
  positions in the word and `:alternate?` flips its sign on odd ones.
  Modulations on one channel add.

```clojure
;; :swell: each letter lifts a quarter of an em (at intensity 1), 120 ms
;; after its neighbour, rising for 0.9 s and settling for 1.5 s
{:ms 2400
 :stagger {:ms-per-glyph 120 :max-ms 900}
 :mods [{:channel :y    :amount 0.25 :curve :rise-sine :from 0     :to 0.375}
        {:channel :y    :amount 0.25 :curve :fade2     :from 0.375 :to 1}
        {:channel :glow :amount 0.22 :curve :arch}]}
```

The nineteen gestures (amounts at intensity 1; "ripple" is the first
eleven's stagger, 25 ms a letter and at most 100 ms):

| Gesture | ms | Stagger (ms a letter, at most) | Channels | What you see |
|---|---|---|---|---|
| `:pop` | 400 | ripple | scale | a quick swell and back |
| `:launch` | 1400 | ripple | y, z, then wobble | up and forward in an arc, landing with a jelly wobble |
| `:shimmer` | 1400 | ripple | glow, y | pulses of light and a slight bob, dying away |
| `:skitter` | 900 | ripple | x | a fast sideways scurry, each letter on its own phase |
| `:quake` | 1000 | ripple | x, y, glow | each letter circles its place and flashes |
| `:melt` | 1400 | ripple | y, rx | droops and tips, then swings back |
| `:rewind` | 700 | ripple, last letter first | rz | a full turn backwards |
| `:fuse` | 900 | ripple | z, y | arrives from in front, alternately above and below the line |
| `:jitter` | 1400 | ripple | x, y | a fine nervous tremble |
| `:glow` | 1000 | ripple | glow | light rises and falls |
| `:wobble` | 1400 | ripple | wobble | jelly |
| `:swell` | 2400 | 120, 900 | y, glow | a travelling lift and glow through the letters: a held note |
| `:bloom` | 1600 | 40, 200 | weight, glow, scale | weight and light rise softly and return |
| `:slam` | 900 | 15, 60 | scale, z, weight, glow | arrives large from in front, heavy, and bounces |
| `:hush` | 1400 | 20, 100 | scale, weight, blur, opacity | a touch smaller, lighter, softer, then clears |
| `:lean` | 1300 | 30, 150 | skew | slants like italics, eases back |
| `:nod` | 700 | 20, 100 | y, rx | a small dip and return |
| `:shiver` | 1100 | none | x, blur | a fine fast tremble with a little blur |
| `:draw` | 500 | none | fill, glow | ink wipes in left to right |

The first eleven are the lab's original hand-written effects, transcribed;
`gestures_test` keeps the old functions as a reference and checks that
each patch gives the same deltas to within 1e-9 (hypothesis H1 of
`docs/product/lab-notebook.md`). The glossary has every gesture perform
its own name.

**The gate is `synth/problems`.** A patch is accepted only when
`(synth/problems patch)` is `[]`: it is a valid `:ha/patch`, every
channel is back at rest as it ends, and no channel can leave its bounds
(half an em sideways, never fully transparent, and so on) for any letter
at any instant. The tests hold every patch in `lab.gestures` to that, and
to the time cap: `(synth/total-ms patch 40)` × 1.5, the slowest style's
tempo, is at most 5000 ms.

**To add a gesture:** an order adds its name to `:ha/word-effect` in the
schema; write its patch in `halfahead.lab.gestures`; give it its own name
in the glossary; add a row above. Nothing in `lab.pose` changes, and the
tests refuse a patch with problems or without a name.

**The score's channels.** `:sx` and `:sy` (stretch, resting at 1, never
below 0), `:skew` (slant, 0), `:weight` (0, within -1 to 1), `:blur` (0,
within 0 to 1) and `:fill` (how much is inked, 1, within 0 to 1) reach the
frame, each present in a pose only while it is away from rest, so a frame
without them is the frame it always was. The reference renderer draws
them (see Channels), so `:bloom`, `:slam`, `:hush`, `:lean`, `:shiver` and
`:draw` show in full. A `:tint` a patch gives is kept (within 0 to 1) only
on a letter that dress has given an `:ink` (see Dress); on any other it is
dropped, there being no ink to move towards.

## Channels

Everything a pose can vary beyond place, turn, size and light, and how
the reference renderer (`halfahead.lab.render`) draws it. A missing key is
its rest value. Each is a per-instance attribute of the one instanced
mesh, read by the one TSL material: a frame using all of them is still
one draw call.

| key | rest | range | drawn as |
|---|---|---|---|
| `:sx` `:sy` | 1 | ≥ 0 | The quad's width and height, times `:scale`, about the quad's centre. |
| `:skew` | 0 | any | The quad sheared along x by `:skew` times the height above the letter's baseline: + leans right, and the baseline stays put. Composed into the instance matrix after the stretch and before the turn. |
| `:weight` | 0 | −1 to 1 | The distance field's edge moved: at +1 out by 2.6 atlas pixels (of a 48 px em), at −1 in by 1.3. Those limits keep a black letter inside its quad (the field runs out 8 px from the outline) with its counters open, and leave a hairline its core. |
| `:blur` | 0 | 0 to 1 | The edge's soft band widened from ±0.7 atlas pixels to ±3.6. The band never reaches the quad's border nor past the field's middle, so at 1 the letter is very soft but still a letter. |
| `:fill` | 1 | 0 to 1 | The part of the quad left of `:fill` (in the quad's own 0 to 1 width) at full alpha and the rest at 35%, with a boundary 0.08 wide; 0 is all dim, 1 all inked. The wipe is across each letter's own quad. |
| `:ink` | `:text` | the palette | The letter's colour: `:text` mixed `:tint` of the way towards the ink, both for the frame's lights. |
| `:tint` | 1 with an `:ink` | 0 to 1 | How far towards the ink. Without an `:ink` there is nothing to move towards. |

### Vertex buffers: the limit, and adding a channel

WebGPU guarantees a pipeline 8 vertex buffers and 16 vertex attributes
(`maxVertexBuffers`, `maxVertexAttributes`), and every per-instance
attribute is a buffer of its own. WebGL2 has no such limit, so a layout
that is too wide draws on WebGL2 and draws nothing at all on WebGPU: with
one attribute per channel (ten buffers) the lab was blank on WebGPU for a
day. The channels are therefore packed into four attributes, listed as
data in `halfahead.lab.instance`:

| attribute | width | holds |
|---|---|---|
| `glyphRect` | 4 | the glyph's rectangle in the atlas |
| `glyphLook` | 4 | opacity, glow, wobble, ghost |
| `glyphShape` | 3 | `:weight`, `:blur`, `:fill` |
| `glyphInk` | 3 | the letter's colour, in linear light |

With the quad's position and uv and the instance matrix that is 7 buffers
and 10 attribute slots (a mat4 takes four); the quad has no normal, which
the renderer deletes. `test/halfahead/lab/instance_test.cljc` fails when
the layout goes past 7, so one buffer stays spare. (`:sx`, `:sy` and
`:skew` cost nothing: they are in the instance matrix.)

To add a channel, put it in a spare component of a packed attribute,
never in a new attribute: widen `glyphShape` or `glyphInk` to 4 in
`lab.instance` and name the channel in its `:holds`, read the component in
`glyph-material`, and write it in `draw!`. Only when all four are full
does a fifth attribute come up, and that takes the last spare buffer.

The glow's halo shows only where the halo does: a letter's own edge
keeps the letter's ink however soft it is, where before every edge pixel
was mixed towards the halo's colour. The halo is in the letter's own ink
when the letter has one other than `:text` (as mixed by its `:tint`), so
gold glows gold and a rubric red; a letter in the text ink, and an ember,
glows vermilion as before. The shader tells by comparing `glyphInk` with
the `:text` ink's uniform, which a text letter carries exactly: no new
attribute and no new draw call.

## The palette

`halfahead.lab.ink` maps the closed palette (`:ha/ink`) to colours, one for
paper (`:lights` 0, background `#f6f1e7`) and one for the dark stage
(`:lights` 1, `#141210`); between the two it is a straight mix.
`(ink/colour ink lights)` is `[r g b]` in 0 to 1, and `(ink/contrast a b)`
the WCAG 2 contrast ratio. Nobody authors a colour.

| ink | on paper | contrast | on the stage | contrast |
|---|---|---|---|---|
| `:text` | `#1d1a16` | 15.40 | `#efe6d6` | 15.09 |
| `:signal` | `#c2401c` | 4.61 | `#ff7a55` | 7.27 |
| `:gold` | `#7d5a00` | 5.59 | `#e8b84a` | 10.13 |
| `:cool` | `#1f5f8b` | 6.08 | `#7fb8e6` | 8.81 |
| `:muted` | `#5a5044` | 7.00 | `#b9ad9a` | 8.46 |

`:text`, `:signal` and `:muted` are the page's own. The tests hold every
ink to 4.5:1 against its background and every pair of inks at least 0.2
apart (distance in RGB, 0 to 1 a component) on each background.

## The specimen page

`/lab/?specimen` draws a type specimen on every frame instead of what is
typed: rows of "Halfahead" (as much of it as lets a row fit the layout
width), each row one channel at fixed values and everything else at rest.
`?reveal=0` or `1` shows it on paper; `2`, `3` or nothing, on the dark
stage. Typing does nothing there. The frame is
`(halfahead.lab.specimen/frame {:advance f :width-em n :aspect n :lights
0|1})`, pure and valid like any other, seen straight on by a camera that
fits the rows.

| row | values, left to right |
|---|---|
| weight | −1, −0.5, 0, 0.5, 1 |
| blur | 0, 0.33, 0.66, 1 |
| skew | −0.3, 0, 0.3 |
| stretch | at rest, `:sx` 1.4, `:sy` 1.4 |
| fill | 0, 0.25, 0.5, 0.75, 1, on a single H (the wipe is per letter) |
| ink | `:text`, `:signal`, `:gold`, `:cool`, `:muted` |

`(specimen/samples opts)` lists the samples as `{:row :value :glyph-ids}`.
On the page `window.__lab.specimen()` returns them, each with `box` (`x0
y0 x1 y1`, the CSS pixels its quads were drawn in), and
`window.__lab.inkColour(ink, lights)` the 0 to 255 colour of an ink, so a
still can be measured: `bb lab:e2e` does (see Running it).

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

## Reading back

A lyric is paced by the music; a message is paced by the eye
(docs/product/score.md, "The clock"). **Ctrl+Enter**, or the **Read back**
button in the bar, reads the current draft back as its reader would get
it. The text dims, then fills with ink word by word at the reader's pace,
and each mark's gesture begins as the reading head reaches it.

- **The head.** `halfahead.lab.pace` lays the draft's score out in time
  for one reader: a stop for every word, contiguous from 0. The head moves
  linearly through a word during its stop and is at the next word's start
  as that stop begins. Each frame gets `:fills`, a map glyph id -> how much
  of the glyph is inked: 1 behind the head, 0 ahead of it, and the share
  of the one letter it is in. `lab.pose` puts that on the pose as `:fill`
  (the channel a `:draw` uses, multiplied with any a cue gives), so the
  renderer wipes the ink in from the left; an unread letter is drawn the
  way fill 0 is on the specimen page, faint, not absent.
- **Beats.** One beat is one word's time: 240 ms at 250 words a minute. A
  word longer than 8 characters stays 1.3 beats; a word ending a sentence
  (`. ! ? …`) half a beat more, one ending in `, ; :` a quarter more.
- **Holds.** A `:hold` the style performs stretches the head's stay on its
  word by (1 + amount): "loooove" stays 1.7 beats in Elegant. A mark the
  style in force does not perform (see Styles: the more flourish, the
  fainter a mark still performs) is left out before pacing, so in Plain,
  whose flourish is 0.35, the acceptance sentence's "loooove" swells and
  is held and "Ship it!" nods, while its aside, turn, doubt and stress
  pass unperformed; at flourish 0 nothing would perform or be held, and
  the ink would just fill at the reader's pace.
- **Its style.** A reading keeps the style it started in: the named style
  in force when it is asked for (under Auto, the one the rhythm suggests
  at that moment) plans it, and while it plays the shell gives the frame
  that style as `:style-choice`, whatever Auto would infer meanwhile. So a
  gesture granted Elegant's length is played at Elegant's tempo even
  though the rhythm lapses to Plain a few seconds after the last key.
  When the reading is over, or ended, the picker's choice applies again.
- **Gestures.** Each performed mark's gesture (`marks/gesture-for`) is a
  cue on the mark's letters at the stop of the first word it touches,
  `:source :score`. It is given its natural length (`reading/natural-ms`:
  the patch's time over the letters times the tempo `lab.pose` will play
  it at, in whole ms rounded up) or what is left before the cap. That
  tempo is the named style's own (`style/params`; Elegant's is 1.5), not
  its style point's (`style/from-point` gives 1.36 there): the point
  chooses the gestures and the dress, the params time them, and `plan`
  takes both, so no cue outlasts its grant and none is withdrawn while a
  letter is still off rest; given less, the cue carries a `:tempo` below 1
  and `lab.pose` plays the same motion faster, stagger included; given
  less than 0.4 of it, it is not played at all.
- **Highlights.** A message too long to pace is not dimmed and has no
  head: only its four strongest marks perform, in reading order, 400 ms
  apart. Restraint is what makes skimming work.
- **The cap.** A performance that starts by itself may hold a reader 5 s
  at most (WCAG 2.2.2), so `pace/reading` paces a message only when the
  head crosses it within 4 s (`:cap-ms`, default 5000, less a second for
  the last word's gesture). At 150 words a minute that is about ten words,
  so the slowest readers got the least pacing. A read-back is asked for
  and Escape stops it, so the lab passes `:cap-ms 30000`: a message is
  paced when the head crosses it within 29 s, and nothing lasts past 30.
- **Pace.** The **Pace** slider beside the style picker sets the reader's
  words a minute, 150 to 450 in steps of 10, 250 to begin with, shown as a
  number. It is remembered on this device in `localStorage`
  (`halfahead.lab.pace`), read and written in try/catch; never sent
  anywhere. A reading already playing keeps the pace it began with.
- **Stopping.** Escape, any edit, or starting another reading ends it at
  once, everything lit. Otherwise it ends when the head has crossed the
  text and every gesture has had the time it was granted.
- **What it leaves alone.** The draft is not changed: the reading's cues
  are merged into the draft for the frame only. They count as use for the
  reveal (a level they reach is kept), they sound when Sound is on, and
  they are not moments, so the stage behind the letters does not react.
  With reduced motion Ctrl+Enter and the button do nothing (the text is
  already all there), and `:fills` is ignored by `pose/frame`.

`halfahead.lab.reading` is the pure part: `plan` (text, style point, the
motion params in force, pace -> the score, its reading and what performs), `cues`, `fills` and `over?`.
`window.__lab.reading()` is null, or the reading playing as data: `t0`,
`durationMs`, `beatMs`, `stops`, `entries`, `performing` and
`headAt(t)`, the head's place (in UTF-16 units of the text) at a time on
the lab's clock.

## Dress

Gestures return to rest. **Dress** is what a mark leaves behind on its
letters, the way bold does (docs/product/score.md, "Dress"). On the paper
page (reveal level 1) a dressed draft looks like a page from a manuscript:
a raised initial, a red-letter phrase, a word in gold, a whispered aside.

| dress | at rest (`dress/looks`) | when the rules give it (`marks/dress-for`) |
|---|---|---|
| `:versal` | 1.8 times the size, on the baseline | the first letter of a message of 12 words or more that opens with a letter, flourish 0.6 or more |
| `:gilt` | the gold ink, with a glint | the strongest stress of amount 0.7 or more (`*starred*`, CAPITALS), flourish 0.3 or more, warmth 0.5 or more |
| `:rubric` | the signal ink | that same stress in a cool style (warmth under 0.5) |
| `:strong` | weight +0.55 | every other stress of 0.7 or more |
| `:whisper` | 0.85 times the size, the muted ink, weight −0.2 | an aside (in parentheses), flourish 0.2 or more |

So Plain (flourish 0.35, warmth 0.5) and Elegant gild and whisper, Coarse
(warmth 0.15) rubricates, and Caffeinated and Drunken open a long message
with a versal; only a point with flourish under 0.2 leaves nothing. A span is dressed once and a message wears at most 8.

- **Where it comes from.** At every edit, and when the writer picks a
  style (the picker or its shortcuts), the draft's dress is
  `(score/from-text text point)`'s `:dress` for the point of the style in
  force at that moment (under Auto, the style the rhythm suggests then),
  placed on the glyphs whose characters lie in each span (`dress/placed`;
  offsets in UTF-16 units, as a reading's). The shell keeps that point
  with the dress and hands the dress to `pose/frame` as
  `:dress {:placed :seen :light}`.
- **It stays.** Between those two moments the dress is not derived again:
  when the typing stops and Auto falls back to Plain, what was put on
  while typing (a versal, say) is still worn, and nothing changes behind
  an idle writer's back. The next edit decides it afresh.
- **Presentation, never text.** Dress adds, drops and changes no
  character: the live poses still spell the draft, the textarea is still
  what assistive technology reads, and a message's text is the same with
  or without it.
- **Easing in.** `dress/seen` remembers when each dress was first seen
  (keyed by what it is and its first glyph), the only memory dress needs;
  the look comes in over 500 ms on a smoothstep from then, size included,
  and at once with reduced motion.
- **Room.** A sized letter's advance is multiplied by its size, so the
  layout makes room: nothing overlaps, lines wrap where they should and
  the caret follows. The letter is scaled from its left edge on the
  baseline. No dropped (multi-line) initials yet.
- **On top of the dressed rest.** Dress is applied to the letter at rest,
  before any motion, so births, cues, shots and read-backs start from and
  return to the dressed pose, and sideways motion stays within half an em
  of the dressed place. Inked poses carry `:ink` and `:tint` (how far from
  the text ink towards it; see The palette).
- **The glint.** Gold is alive without moving. A gilt letter glows 0.12
  by itself; with the mouse over the stage, the pointer is projected onto
  the text plane (`render/plane-point`) and each gilt letter's `:glow` is
  0.12 + 0.5 e^(−d² / (2 × 1.2²)), d the distance in em from the pointer
  to the letter: 0.62 under it, back to about 0.12 four em away. It
  follows the pointer only; there is no shimmer in time, so it is kept
  with reduced motion. A finger or a pen is no light, and when the mouse
  leaves the page the gold is back at 0.12. The halo is the letter's own
  ink (see Channels), so the glint is gold, not pink.
- **Levels.** Dress shows from reveal level 1. At level 0, the familiar
  chat box, frames are exactly as without it.

`halfahead.lab.dress` is the pure part: `looks`, `placed`, `seen`, `sizes`
and `at-rest`. `window.__lab.dress()` returns the placed dress as plain
data (`[{as, "glyph-ids"}]`).

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

## The tour

For someone who does not know what to type, the lab can show itself:
under a minute in which it types into its own text box, a caption for
each scene. It shows a plain chat box, a phrase that stays gold and
catches a light, a held letter holding its note, the lights going down,
a read-back, the same words read by another style, and a card; then it
hands over the keyboard.

The tour is data, `halfahead.lab.tour/script`: eight scenes, each
`{:caption :steps}`, a step one of

| step | what the shell does |
|---|---|
| `{:do :clear}` | empties the textarea and dispatches its `input` event |
| `{:do :type :text s :ms-per-key n}` | sets the textarea's value a key longer every n ms, an `input` event for each |
| `{:do :wait :ms n}` | nothing |
| `{:do :style :style kw}` | picks the style, as the picker does |
| `{:do :reveal :level n}` | wands (as Ctrl+. does) until the level is n; never lowers it |
| `{:do :read-back :wpm n}` | sets the Pace slider to n and starts a read-back, as Ctrl+Enter does |
| `{:do :until-read}` | waits until that read-back is over |
| `{:do :light :over s :ms n}` | moves the light the mouse sets along the drawn letters of the phrase s |

Nothing in the tour draws or decides anything: every step goes through
the path a person's hand takes, so cues, Auto's rhythm, dress, the
director and the reveal behave as they do for typing, and what the tour
shows is what typing does. The captions and texts are product voice;
`tour_test.cljc` pins them.

`(tour/plan script)` flattens the script into a timeline, each step
starting when the one before it ends. A read-back lasts as long as the
pace makes it, so the timeline is a vector of segments cut after each
`:until-read`, and the shell starts the next segment's clock when the
reading is over. `(tour/due segment t-ms done)` gives the steps to play
at t-ms and `(tour/keys-due step elapsed)` how much of a `:type` step's
text is typed by then, so a late tick catches up key by key and no step
is skipped or played twice. `(tour/total-ms script)` is the length
without the read-backs, about 34 s; with them the tour takes about 42 s.
The shell plays it on a timer of its own, not the frame loop, so it
keeps its pace however slowly the frames are drawn.

It starts only when asked: with `?tour` in the address (the landing page
links to `lab/?tour`), or from the **Tour** button in the bar. Words
already in the text box are the visitor's draft: the shell keeps them
aside, the tour begins on an empty box, and when it ends or is stopped
they are put back through the same edit path as typing, the caret at
their end, so the draft is the visitor's again. With an empty box at the
start, what the tour typed stays on a stop, as before. A
caption bar at the bottom centre, a polite live region, shows each
scene's caption beside a **Stop** button. Any key press, click or tap
stops it at once (the event still does what it does, so a letter typed
lands after the text): the scripted typing stops, what is on the
page (or the draft kept aside) is the visitor's to edit, and the caption
reads "Your turn." for 2 s. When it ends or is stopped, the style and the pace the
visitor had come back and the textarea has the focus; the reveal level
stays where the tour took it. The tour records nothing: its pace is not
remembered on the device, and it makes no sound of its own (the lab's
sounds play if Sound is on).

With reduced motion there is no tour. The caption bar says "The tour is
motion. Reduced motion is on, so here is the lab instead." until the
next key or click.

`window.__lab.tour()` returns nil, or `{scene, caption, segment,
running}` while one plays.

## Accessibility

The input is a real `<textarea>`, focused, with transparent text: IME,
paste, undo, spellcheck and screen readers keep working. The canvas is
`aria-hidden`. Reduced motion shows letters at rest. Sound is opt-in, a
labelled checkbox, and every sound has a visible counterpart. Read back is
a real button and Pace a labelled range input; a reading never changes
the textarea's text, so assistive technology has all of it throughout.
The tour's captions are a polite live region, Stop is a real button, and
the tour does not run with reduced motion (see The tour). The board's
flick's chip is a polite live region too, its choices real buttons as
well as arrow keys, and no exam runs with reduced motion (see The
flick). The board's
pads are real buttons in a labelled toolbar, each named and reachable by
Tab, pressed by Enter or Space; the XY pad is a focusable slider with a
name and value text, moved by the arrow keys; all have a visible focus
ring, and the board works at 360 px wide (see The board).

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
dress: gilt "the one thing" · whisper "trust me"
reading paced · 4920 ms · beat 240 ms
loooove -> swell 0.7 (reading)
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
  chose it; with the board's point in force (Mine) it reads `style mine
  (e 0.60 f 0.40 s 0.50 w 0.50)`, the point's four axes.
- **cues**: up to six cues fired in the last eight seconds, newest first,
  each as word -> effect, intensity, and the source that chose it; a
  pad's reads like any other, `did it -> glow 0.7 (board)`.
- **directions**: likewise up to six recent directions, newest first, each
  as the phrase (its first 16 characters) -> shot and energy.
- **camera target**: where this frame's camera looks, in em.
- **reveal**: the frame's reveal level and house lights (0 up, 1 down).
- **stage**: the stage behind the letters and the newest moment it was
  given (`moment none` before the first); see Writing a stage.
- **sound**: whether Sound is on.
- **dress**: only when the draft wears any (see Dress): each dress and
  the text it is on (its first 16 characters), in draft order.
- **reading**: only while a read-back plays (see Reading back): `paced`
  when the head stops on every word or `highlights` when the message is
  too long, how long the reading lasts, its beat, then one line for each
  mark it performs, in the order they begin.

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

Add `?reveal=3` to the URL for the full stage at once (see The reveal),
`?specimen` for the type specimen (see The specimen page), or `?webgl` to
start on WebGL2 without trying WebGPU (see the backend, below).

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
that the frames are performed in that style. On a `?debug` page in the
Plain style it types "I loooove it ", checks the overlay for `loooove ->
swell 0.7 (elongation)`, and reads the frame on every animation frame for
6 s: some letter of "loooove" lifts; none lifts more than 0.25 × 0.7 em;
each letter's highest sampled lift comes no earlier than that of the
letter before; and from 5.5 s on every letter is at rest (`swell.png`,
about a second in). On a `?debug` page it checks
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
nearer its target (`push.png`). It repeats with reduced motion (on every frame the camera target
is on the current line and drawn at the canvas centre). Finally the
`specimen` step opens `/lab/?specimen` and `/lab/?specimen&reveal=0` at
1280 × 900, checks that typing changes nothing, and measures each
screenshot's pixels inside every sample's box, as ink from 0 (the
background) to 1 (the sample's ink) in linear light: the ink grows with
weight, by at least 15% from −1 to 0 and from 0 to +1, and −1 keeps at
least a quarter of 0's; the count of half-inked pixels grows with blur;
the ink in the top third of the box is right of the bottom third's at
skew +0.3 and left at −0.3 (less the upright sample's own offset, since
the word's ascenders are not evenly placed); the inked box is wider for
`:sx` and taller for `:sy` than at rest; at fill 0.5 the left half of the
H holds at least twice the right half's ink, and at 0 and 1 the halves are
even, 0 holding about 35% of 1's; and the most common inked colour of each
ink's sample is within 12/255 of `__lab.inkColour`. They are stills, so no
timing is involved. The same step types a 200 character paragraph on
`/lab/?debug`, waits for it to rest and reports the overlay's mean frame
time (lab-notebook H3; headless numbers are for comparison only, so
nothing is asserted). Last, on `/lab/?debug&reveal=3` in Elegant at pace
250, it types "I loooove this (honestly). But maybe not. Ship it!", waits
until every letter has stood at rest for 500 ms of frames, presses
Ctrl+Enter and samples `lastFrame()` and `reading()` on every animation
frame: during the stops every frame's fills are a lit prefix, at most one
part-filled letter and a dim suffix, the lit length within 1.5 characters
of `headAt` at that frame's time; no letter of "loooove" lifts before the
head reaches it and one does after; once `reading()` is null no pose has a
`:fill`, and 5.5 s later every letter is at rest; the reading is shorter
at pace 400 than at 150 (started with the button); Escape leaves no fill
in the next frame; `reading.png` is taken about 40% through a reading at
150; then in Plain at pace 250 the reading's `performing` is swell then
nod, beginning at the stops of "loooove" and "Ship", and the overlay
lists both. Then the dress step: on `/lab/?debug&reveal=1` (paper) in Elegant it
types "This is \*the one thing\* that matters (trust me)." and waits for
the frames to stop changing: `dress()` is a gilt on "the one thing" whose
letters have ink gold and tint 1 and a whisper on "trust me" whose letters
are smaller than 1 and muted, the overlay lists both, and the most common
inked colour in the gold phrase's box is within 12/255 of
`inkColour('gold', 0)` (`dress-paper.png`). The mouse is moved, slowly
enough not to be a wand, onto the phrase's middle letter: every gilt
letter's `:glow` matches the glint's curve at its own distance within
0.03 (`dress-glint.png`), and in that screenshot the halo pixels round
the letter under the pointer (not paper, 2 to 4 px clear of any gold ink)
are on average nearer in rgb to a mix of paper with gold than to a mix of
paper with vermilion; all glows are back within 0.02 of 0.12 with the
mouse in a corner. In Caffeinated a sentence of 14 words has a first
letter at scale 1.8 and a second starting at or beyond its scaled right
edge; in Plain the sentence wears the same gilt and whisper and the 14
words no versal. On `?reveal=0`, a fresh page for each of the five
styles, "\*gold\* (hm)" (two words, so the level stays 0) has dress
decided but no pose with an ink, a weight, a size or a glow. On
`?reveal=1` with the picker on Auto and the clock held, the sentence
typed at 450 ms a key (Auto reads Elegant) and then the 14 words typed at
40 ms a key (Caffeinated, so a versal) wear the same dress (kinds, glyph
ids, inks, sizes) 1 s after the last key and 8 s later, when Auto reads
Plain. The same data and colour checks as on paper pass on the dark stage
(`?reveal=3`, `dress-dark.png`). The `board` step, on
`/lab/?debug&reveal=3` in Plain, reads the draft's cues and directions
and every frame drawn (each with its own `t-ms`; a letter is moving when
its pose differs from the one it rests in), never the clock. The board
is hidden on load and Ctrl+B shows a toolbar named Board with seven
named pads. With "we did it" typed, a click on STRESS places a `(board)`
glow 0.7 on the whole phrase, shown in the overlay, and a frame shows
its letters moving; Alt+2 places a swell with the focus and the text
unchanged; with "did" selected LAND's cue has only those glyphs and no
other letter moves; BOOM places a `:card`. A press that goes down on
STRESS, moves off it and is released, then a typed 1, places no board
cue and types the digit; the same after Alt+1 goes down and the window
gets a `blur`. DOUBT held 800 ms with Enter, and again with Space, has
amount 1.0, and a quick Enter 0.7, one cue each. A click in the XY pad's
top right corner gives a point above 0.9 on both axes, the picker shows
Mine, the overlay names it, and the arrow keys step it by 0.05; the same
swell is then at rest sooner after its cue than Plain's was still seen
moving (`board.png`, mid-swell). After a reload the board is still out
and the point kept. On a fresh plain page at level 0, STRESS brings the
level to 1 with the phrase moving in a frame at that level, and BOOM
brings it to 3. At 360 × 640 with the board out and two lines typed, the
board is a band along the bottom whose box does not intersect
`textBox()`, every pad at least 24 px each way and inside the viewport,
and a pad still plays (`board-narrow.png`); the boxes are apart at 1000
px too. On `/lab/?tour&debug` (the `tour`
step) it follows the tour by data, never by the clock: a recorder in the
page notes every change of the caption, and the scenes must run 1 to 8
with their captions. Scene 1 begins at level 0 on paper; in scene 2 the
gilt is on "the one thing" and a frame with a letter glowing above 0.4
matches dress's curve for a light placed where its two brightest letters
put it (`tour-2.png`); scene 3 shows a swell cue in the overlay; scene 4
reaches level 3, and scenes 2 to 4 must begin at level 1 or below; scene
5 reads back in Elegant with the Pace slider at 200 (`tour-5.png`, taken
as it starts and checked to have been mid-reading), scene 6 again in
Caffeinated; scene 7 places a `:card`. At the end `tour()` is nil, the
textarea is empty and focused, and the picker (Auto) and the pace (a
stored 300) are as before, the stored pace untouched. It prints the
tour's measured length (`tour: N.N s end to end on <backend>`). Then,
started from the Tour button with Drunken picked, a Shift key in scene 2
stops it at once: the text typed so far stays, an x typed lands after
it, Drunken is back. Started from the button over a typed "my own
words", the tour plays on an empty box, and a Shift in scene 2 gives the
draft back, focused, the caret at its end; started again and left to
finish, it gives it back again. With reduced motion emulated, `?tour` starts no
tour and the caption says why. The `flick` step, on a fresh page at
`?reveal=3`, follows `__lab.flick()` and the chip, never the clock, and
prints its backend. The picker shows Auto and Which? is disabled; with
"I loooove this (honestly). But maybe not. Ship it!" typed and at rest
nothing has started. Ctrl+/: the phase goes a, b, choose, with the chip
reading "A", "B", "A or B?", a reading playing in a and b, the textarea
read-only, and A's and B's points differing on exactly the axis
reported (`flick.png`, at the choice). ArrowRight: one flick, the point
is B's, the picker and the XY pad show Mine. Ctrl+/ again, an arrow
during A (nothing), then Escape: the count, the point and both stored
entries are unchanged. Two more exams, toward whichever option has the
higher energy, one by a button and one by a key: energy has not fallen.
After a reload the point, the count and Mine are kept; Elegant, picked,
is kept over another. With a stored learner at Elegant's point whose
steps are all 1/32, Ctrl+/ shows "Nothing more to tell apart on this
line" and no frame has an exam. All along it asserts on
`window.__lab`; it leaves `typing-mid.png`, `embers.png`,
`settled.png`, `wrapped.png`, `reveal-0.png` to `reveal-3.png`, `crawl.png`, `steady.png`, `swell.png`, `card.png`, `push.png`, `fuse.png` (taken at the
first slice where a letter of "merge" is more than 0.05 em off the line,
so it always shows the fuse in flight), `debug.png`, `style-<name>.png` for
each explicit style, and `style-auto-fast.png` and `style-auto-slow.png` in
`out/lab/`, with `specimen-dark.png`, `specimen-paper.png`, `reading.png`, `dress-paper.png`, `dress-glint.png`, `dress-dark.png`, `board.png`, `board-narrow.png`, `tour-2.png`, `tour-5.png`, `flick.png` and `webgpu.png`, and stops the server it started. It is not part of
`bb test`.

### The webgpu step

Every step above runs on WebGL2 in the check's browser: headless Chromium
has a software WebGPU adapter, but its `GPUTexture.createView` rejects the
`swizzle` field three.js 0.187 passes, so the lab falls back. The last
step, `webgpu`, opens a second browser with an init script that drops
`swizzle` before calling the real `createView`, and that records the
vertex buffers and attributes of every descriptor given to
`GPUDevice.createRenderPipeline` and `createRenderPipelineAsync`. On
`/lab/?reveal=3&debug` it types a word, and of the pipelines recorded it
asserts that none has more than 8 buffers or 16 attributes, that the
widest (the glyph pipeline) has no more than `__lab.vertexBuffers()`, and
that no console message says "pipeline creation failed" or "exceeds the
maximum". Then the page may not be blank and silent: either the word's box
holds inked pixels, or the status line names an error. Last, `/lab/?webgl`
must end on WebGL2, record no pipeline at all, and draw the word. The
step's line says which of these it saw. Where the browser has no
`navigator.gpu`, or no pipeline is asked for, it says that no pipeline was
checked and asserts only that the word is drawn.

What it proves: the descriptors are the ones three.js's real WebGPU path
builds from the lab's own scene, and the software device does validate
them (given nine buffers it answers "Vertex buffer count (9) exceeds the
maximum number of vertex buffers (8)", and the step fails). What it
cannot: in a cloud container that device is lost a moment after the first
frame is submitted ("A valid external Instance reference no longer
exists"), so nothing is presented and no pixel is ever checked on WebGPU;
the lab's start-up probe sees the loss and falls back to WebGL2, which is
what the step then photographs. It says nothing about a real GPU, whose
limits may be higher but never lower, nor about the path without the
shim. And three.js binds only the attributes the shader reads, and moves
the instance matrix into a uniform buffer when the geometry is crowded,
so the recorded count can be lower than `lab.instance`'s, never higher.

### The backend

The status line names the backend. `halfahead.lab.render` asks three.js's
`WebGPURenderer` for WebGPU, which falls back to WebGL2 when the browser
has none. Some browsers expose a WebGPU older than this three.js expects,
or with less room than a pipeline asks for, and WebGPU reports nearly
every failure after the call that caused it has returned, where a
try/catch cannot see it. So the renderer first draws a probe frame (one
letter and the caret, so that every pipeline the lab uses is made) inside
WebGPU error scopes, waits for the device to finish the work, and reads
the scopes; it also counts whatever three.js logs as an error meanwhile
(three catches a refused pipeline itself and then skips its draws in
silence), the device being lost, and five seconds without an answer. Any
of those is warned of in the console and the renderer starts over on
WebGL2. An error WebGPU reports after a good start, or the device being
lost then, is warned of once and kept: `window.__lab.gpuErrors()` returns
the messages (five at most) and the status line reads "Rendering with
WebGPU, with errors: add ?webgl to the address". `?webgl` starts the
renderer on WebGL2 directly. One TSL material serves both backends.

The page is offline: no network, no downloaded fonts (the glyph atlas is
rasterised with tiny-sdf from the system's sans faces), no audio files:
sound, when turned on, is synthesized.
`window.__lab` exposes `backend`, `gpuErrors()` and `vertexBuffers()`
(the buffers `lab.instance` allows the glyph pipeline), `liveText()`, `lastFrame()`,
`directions()` (the draft's directions), `drawn()`, `lean(i)` (degrees between drawn glyph i's projected left edge
and the screen vertical) and `blockCentre()` (the block's drawn centre,
where the frame's camera target lands, and the viewport size, in CSS
pixels),
`project(x, y, z)` (where a point in em landed on the canvas, in CSS
pixels), `advance(c)` (a character's advance in em), `soundLog()` and
`bounds()` (see Sound), `reading()` (see Reading back), `dress()` (see Dress), `specimen()` and `inkColour(ink, lights)` (see The
specimen page; `specimen()` is null on the ordinary page) for tests and for anyone poking at
it from the console.
