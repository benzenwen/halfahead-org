# The score: a language for text that performs

*Foreman's design, 2026-11-17, answering Ben's question: "what would a rich,
capable, creative description of animated text look like?" Choices I made
alone are logged in `open-questions.md`; what was tested is in
`lab-notebook.md`. Shapes are in `src/halfahead/schema.cljc` (the code
wins a disagreement).*

## The short answer

Borrow the structure music already worked out. A composer writes what is
*meant* (notes plus expression marks), a performer with a *style* decides
how that sounds, an *instrument* has a few physical parameters, and time
is bound to a *pulse*. For text:

| layer | in music | here | who writes it |
|---|---|---|---|
| **marks** | *dolce*, *ff*, an accent, a fermata | what a span of text is doing: stress, hold, aside, turn… | people by typing; agents explicitly |
| **style** | the performer | a point in a small style space; turns a mark into a gesture | you pick it, or it learns you |
| **gestures** | an ornament, a bowing | a named motion: swell, pop, slam, hush | styles choose; agents may name one |
| **patches** | a synthesizer patch | a gesture written out: modulators on channels | agents and designers |
| **channels** | pitch, loudness, timbre | what one letter can vary: place, turn, size, weight, ink, light, focus | the engine |
| **clock** | the beat | the reader's pace, or the writer's keystrokes | measured |

So, to "graphically or emotionally?": **neither on top.** The first tier
is *rhetorical* (what the words are doing for the reader), coloured by
*emotion* (two numbers), and rendered *graphically* by a style. A writer
means "this matters" or "I'm not sure", never "scale 1.3 with overshoot";
and the same mark has to come out as a gilded glint in one style and a
slam in another, which is impossible if the top tier is already graphic.

And to "are there base engines that composite into styles?": yes, one.
A **text synthesizer**: about sixteen channels per letter, a handful of
curves and oscillators, and staggering across letters. Every effect the
lab has today is a few lines of data on it (experiment H1), and so is
every new one.

## Marks: the first tier

A mark says what a span is doing. Eight kinds, small on purpose, like
the expression marks on a page of music:

| mark | the words are… | examples | plain-text tells |
|---|---|---|---|
| `:stress` | what matters | "this is the *one* thing" | capitals, `*asterisks*`, `!` |
| `:hold` | to be lingered on | "I loooove it", "sooo close" | repeated letters, `…` |
| `:aside` | said under the breath | "(not that anyone asked)" | parentheses |
| `:turn` | a change of direction | "…but then", a punchline | "but", "however", `—` |
| `:doubt` | unsure, hedged, ironic | "sure, that'll work" | `?`, "maybe", "I guess" |
| `:build` | climbing | "bigger, faster, stranger" | lists, repetition |
| `:land` | the conclusion | "Ship it." | a short final sentence |
| `:voice` | someone else's words | a quotation | quotation marks |

Each mark carries an `:amount` (0 to 1) and, optionally, an **affect**:
`:energy` (calm −1 to excited +1) and `:warmth` (harsh −1 to tender +1),
the two axes of the circumplex model of emotion. A tender stress and a
furious stress are the same mark with different affect; a style turns the
first into a soft bloom and the second into a slam. Named feelings
(`:tender`, `:furious`, `:wistful`, `:giddy`…) are shorthand for points
on those two axes.

People never write marks. The lab reads them from the text (the "tells"
above, the glossary, the typing rhythm). Agents write them explicitly.

## Channels: what one letter can do

| group | channels | notes |
|---|---|---|
| place | `:x :y :z` | em; sideways stays within half an em (0032) |
| turn | `:rx :ry :rz` | radians |
| size | `:scale :sx :sy` | `:sx :sy` stretch one axis |
| slant | `:skew` | italics on demand |
| weight | `:weight` | −1 hairline to +1 black; free with an SDF atlas |
| light | `:opacity :glow` | |
| focus | `:blur` | soft edges: distance, a whisper |
| shape | `:wobble` | the jelly deformation |
| ink | `:tint :fill` | `:tint` mixes toward the mark's ink; `:fill` is a left-to-right wipe, the lyric highlight |

Inks are a closed palette (`:text :signal :gold :cool :muted`) that the
renderer maps to colours for paper and for the dark stage, so contrast is
the renderer's job and nobody can author unreadable text.

Beyond the letter there are four more instruments, each already data:
the **camera** (shots), the **stage** (the scene and its moments), **sound**
(synthesized voices) and the **page** (lights, margins).

## Patches: gestures written out

A patch is a duration, a stagger across the letters, and up to twelve
modulations. A modulation moves one channel by an amount along a curve,
inside a window of the patch's time, optionally multiplied by an
oscillator:

```clojure
;; :swell, measured from the Music app's held syllable: each letter lifts
;; a sixth of an em as the highlight reaches it, 120 ms after its
;; neighbour, rises for 0.9 s and settles for longer. Amounts are at full
;; intensity; "loooove" plays at 0.7, a sixth of an em.
{:ms 2400
 :stagger {:ms-per-glyph 120 :max-ms 900 :order :forward}
 :mods [{:channel :y    :amount 0.25 :curve :rise-sine :from 0.0   :to 0.375}
        {:channel :y    :amount 0.25 :curve :fade2     :from 0.375 :to 1.0}
        {:channel :glow :amount 0.22 :curve :arch}]}
```

Curves: `:one :rise :fall :fade :fade2 :arch :arc :rise-sine :spring
:spring-fall`. Oscillators: sine, cosine, triangle or noise with a
period, phased the same for every letter, by position (a travelling
wave) or by letter identity (deterministic jitter).

**Safe by construction.** A patch is checked before it runs
(`lab.synth/problems`): every channel is back at rest at the end, and no
channel leaves its bounds (half an em sideways, never fully transparent,
at most 2.4 s plus the stagger). An
agent can write any patch that passes; none that passes can leave text
displaced, illegible or moving for ever. That is the charter's first
invariant made mechanical.

## Gestures: the vocabulary

The eleven effects the lab had (pop, launch, shimmer, skitter, quake,
melt, rewind, fuse, jitter, glow, wobble) become patches, and eight join
them:

| gesture | what you see | what it is for |
|---|---|---|
| `:swell` | a travelling lift and glow through the letters | a hold: "loooove" |
| `:bloom` | weight and light rise softly and return | tender stress |
| `:slam` | arrives large from in front, heavy, and bounces | hard stress |
| `:hush` | a touch smaller, lighter, softer, then clears | an aside |
| `:lean` | slants like italics, eases back | doubt, irony |
| `:nod` | a small dip and return | landing, agreement |
| `:shiver` | a fine fast tremble with a little blur | anxiety |
| `:draw` | ink wipes in left to right | being written, being read |

## Styles: the performer

A style is a point in a four-dimensional space, plus a table from marks
to gestures:

- **energy**: slow and small to fast and large (tempo, amplitude)
- **flourish**: spare to lavish (how often anything performs at all)
- **softness**: crisp and snappy to gliding (overshoot, blur, glow)
- **warmth**: cool ink and glassy sound to gold and wood

Plain, Elegant, Caffeinated, Drunken and Coarse are named points (two
have a trait the axes do not cover: Drunken sways, Coarse moves in
steps). "Subtle" to "in your face" is the diagonal of flourish and
energy. Because a style is a point, it can be *learned*.

## The clock: pacing is bound to reading

A lyric is paced by the music. A message is paced by the eye. So time in
a performance is counted in **beats**, one beat being the time this
reader takes for one word (240 ms at 250 words a minute), and a
**reading head** moves through the text at that pace:

- a mark performs when the head reaches it, and its ink fills as the head
  passes, like a lyric;
- a `:hold` stretches the head's stay (a fermata);
- a fast reader gets a faster performance, a slow reader a slower one;
- nothing holds a reader beyond 5 s (WCAG 2.2.2): a message longer than
  that appears at once and only its strongest marks perform, in reading
  order. Restraint is what makes skimming work.

While you type, the clock is your own keystrokes, as it is today.

## Dress: what stays when the motion ends

Gestures return to rest. **Dress** is what a mark leaves behind, the way
bold and italic do, and it is where the illuminated manuscript comes in:

| dress | at rest | from |
|---|---|---|
| `:versal` | an enlarged first letter | the initial that opens a chapter |
| `:rubric` | the signal ink | red-letter headings |
| `:gilt` | gold that glints as light passes | gold leaf, which moved in candlelight |
| `:strong` | heavier weight | bold |
| `:whisper` | smaller, lighter, muted | the interlinear note |

## Illumination, now

In a scriptorium the scribe wrote the words, the illuminator made them
shine, and later readers wrote glosses in the margins. That division of
labour maps onto a room of people and agents with almost no forcing:

- **You are the scribe; your agent is the illuminator.** You type plain
  words. Your agent, on your device, in your style, marks and dresses
  them. Nobody tweaks parameters.
- **Other agents write in the margin.** A gloss beside your sentence (a
  source, a correction, a counterpoint) instead of another message in the
  column. Margins are also where many agents can be present without
  crowding the conversation: the drolleries of this age (experiment 6).
- **Gold that responds.** Gilding caught the candle as the page turned.
  Ours catches the pointer, the tilt of a phone, the time of day: alive
  while perfectly still.
- **The page keeps time.** Old lines recede and dim (the crawl); a
  conversation becomes a scroll with depth.

## How people and agents drive it

**Agents** speak the whole language: marks with affect on spans, a named
gesture or a raw patch when they want a signature move, dress, a shot, a
gloss. One page of reference in the persona; every score is validated,
and an invalid one degrades to plain text (experiment H4).

**People** get two instruments and no parameters:

1. **The flick** ("better like this, or like this?"). Now and then a
   line plays two ways; you flick toward the one you like, and your style
   point moves. An optometrist's exam for motion (experiment H5).
2. **The board.** A few pads that mark the last phrase (STRESS, HOLD,
   ASIDE, TURN, LAND, and a couple of big canned moments) and one XY
   pad for energy and flourish. Keyboard first. The Cramer end of the
   dial.

## Release timeline

| release | name | what arrives |
|---|---|---|
| R0 | *Stage* (merged) | cues, styles, shots, sound, reveal levels, the stage seam |
| R1 | *Instrument* | channels, patches, gestures as data; swell; weight, ink, focus, fill |
| R2 | *Notation* | marks with affect; style tables; the reading head; agents write scores |
| R3 | *Performer* | the flick and the board; styles that learn you |
| R4 | *Illumination* | dress at rest: versals, rubrics, gilt; glosses in the margin |
| R5 | *Company* | many agents in the margins; Jon's scenes as stages; the whole UI dissolving |
| R6 | *Scriptorium* | patches and styles as things people and agents publish and trade |

Each release is a superset: a score written for R1 plays unchanged in R6.
