# Lab notebook

*Hypotheses, how each is tested, and what came back. A result is only
written here once it has been measured; "pending" means not yet run.
Design: `score.md`. Choices: `open-questions.md`.*

## Observation: the held syllable (2026-11-17)

Ben's recording of the macOS Music lyric view, 4.4 s, 250 frames at
57 fps, 606 × 334. Read in full, frame by frame; values are from
luminance-weighted centroids of each letter's pixels, so they are
measurements of a compressed video, good to about a third of a pixel.

- The line fills with white syllable by syllable (a soft-edged wipe).
- On the held "love" each letter **lifts as the wipe reaches it**: `o`
  2.8 px, `v` 3.0 px, `e` 3.0 px, about 0.17 em at this size. The `l`
  reading (1.4 px) is unreliable: my column range for it was off.
- Peaks are **staggered**: `o` at 0.94 s, `v` at 1.05 s, `e` at 1.21 s
  after the syllable starts, so 110 to 150 ms a letter.
- Each letter rises for about 0.9 s and **settles slowly**: half its lift
  is gone about 0.5 s after the peak, a quarter is still there at 2.2 s.
- Lit letters carry a soft glow. Short syllables get the wipe and no lift.

What it teaches: the motion is reserved for the one place the sound
lingers; it travels in reading order; it is small (a sixth of an em) and
slow. In typed text the held syllable is elongation: "loooove".

## Hypotheses

| # | hypothesis | test | result |
|---|---|---|---|
| H1 | One small synthesizer (≤ 10 curves, 4 waves, windows, stagger) can express every effect the lab has | Re-express the 11 effects as patches; deltas must match the hand-written functions at sampled times | **Holds**: all 11 match within 1e-9 (below) |
| H2 | The held-syllable swell reads as subtle and reserved in typed text when tied to elongation | Build `:swell` from the measurements; property tests on its bounds; Ben's eyes | Built and measured (below); **whether it reads as subtle is Ben's to judge** |
| H3 | Weight, focus, ink, stretch, slant and fill cost nothing extra to draw | Add them as per-instance attributes of the one instanced mesh; compare frame times before and after | **Half holds**: still one draw call, about 15% slower in software WebGL; not measured on a GPU (below) |
| H4 | An agent can write valid, varied scores from one page of reference | N prompts through the `claude` CLI; rate of schema-valid scores on the first try; spread of marks across moods | **Holds on 28 cases, one model, one run** (below) |
| H5 | A style can be learned from about a dozen flicks | Simulate readers with hidden style points and noisy choices; error against number of flicks | **Holds for a careful chooser, fails for a noisy one** (below) |
| H6 | Pacing by the reader's beat keeps every performance inside reading time and the 5 s cap | Properties over generated scores and paces from 100 to 800 words a minute | **Holds, and shows the cap starves slow readers** (below) |
| H7 | Dress (versal, rubric, gilt) stays readable on paper and on the dark stage | Contrast of each ink against both backgrounds ≥ 4.5:1, computed; screenshots | **Holds for the inks** (below); dress itself not built yet |

## Results

### H5: learning a style from flicks (order 0043, 2026-11-17)

`(learn/experiment {:readers 1000 :flicks 32 :noise u :seed 1})`: 1000
simulated readers with hidden style points drawn uniformly, each choosing
the nearer of two options and choosing wrongly with probability `noise`.
The number is the distance from the learner's point to the hidden one
(root mean square over the four axes; 0.292 is where everyone starts).
The builder measured it and the reviewer re-ran it independently; the
tables agree to the last digit.

| wrong choices | | 0 | 4 | 8 | 12 | 16 | 24 | 32 flicks |
|---|---|---|---|---|---|---|---|---|
| none | median | 0.292 | 0.213 | 0.086 | 0.067 | 0.060 | 0.033 | 0.023 |
| none | 80th pct | 0.342 | 0.274 | 0.121 | 0.081 | 0.074 | 0.039 | 0.031 |
| 1 in 10 | median | 0.292 | 0.238 | 0.138 | 0.111 | 0.101 | 0.063 | 0.047 |
| 1 in 10 | 80th pct | 0.342 | 0.311 | 0.245 | 0.199 | 0.164 | 0.112 | 0.086 |
| 1 in 4 | median | 0.292 | 0.271 | 0.250 | 0.224 | 0.190 | 0.165 | 0.144 |
| 1 in 4 | 80th pct | 0.342 | 0.354 | 0.372 | 0.320 | 0.287 | 0.266 | 0.232 |

What it says:

- A careful chooser is within 0.07 of their taste after a dozen flicks
  and 0.03 after two dozen. (Whether 0.07 feels like "mine" is a
  question for real people; not tested.)
- At one wrong choice in ten it takes two dozen flicks to get as close.
- At one in four the staircase barely learns: the median is still 0.144
  after 32 flicks, and the 80th percentile of error is higher after eight
  flicks (0.372) than at the start (0.342). My reading of why, not yet
  tested: the step halves after two refusals and never grows back, so a
  wrong refusal shrinks it for good.

What to do about it (not yet built):

- **Make the two options obviously different.** How often people choose
  "wrongly" between two motions is the unknown that decides everything
  here, and it falls as the difference grows. Big first steps (0.25) help;
  the flick should never offer a difference too small to see.
- **Ask about fewer axes.** Two axes should learn about twice as fast as
  four, since each is asked about twice as often (expected, not
  measured). Energy and flourish first (the board's XY pad); softness and
  warmth later.
- **A learner that forgives.** Never shrink the step on one refusal
  pattern alone; or keep a belief over points and update it (a Bayesian
  staircase). Worth an order once real flicks exist to tune it against.
- Percentiles are nearest rank; each reader's seed comes from the
  experiment's own generator (a Lehmer generator, so both hosts agree).

### H1: one synthesizer for every effect (orders 0042 and 0045, 2026-11-17)

The eleven hand-written effects (pop, launch, shimmer, skitter, quake,
melt, rewind, fuse, jitter, glow, wobble) were rewritten as patches: data
for `lab.synth`, using 6 of its 10 curves (arc, arch, fade, fade2, fall,
rise-sine), sine and cosine waves phased per letter, windows, `:each`,
`:alternate?`, `:abs?` and two stagger orders (all nineteen gestures
together use 8 curves). The old functions were
moved into the test as the reference. Every patch matches its old
function within 1e-9 on every channel, on a 7 ms grid over the effect's
length, for positions 0 to 5, three glyph ids and two intensities. No
exceptions. The hand-written code is deleted; `lab.pose` now plays
patches. Eight new gestures were then added as data alone.

What it cost to get here: the synthesizer's safety check was rejected by
review twice, both times for flaws in the foreman's design. The check now
covers every letter (per-letter oscillators are bounded by their
envelope, not sampled), and springs fade their residual so they land
exactly. A patch with no problems cannot leave its bounds or end off
rest, for any letter.

### H6: pacing by the reader's beat (order 0046, 2026-11-17)

Properties over generated scores and paces from 100 to 800 words a
minute all hold: the reading is valid, nothing passes the cap, stops
cover the words in order, a faster pace never makes a word later, a
stronger hold never shortens its word's stay.

For "I loooove this (honestly). But maybe not. Ship it!" (nine words):

| pace | reading | lasts | longest message that still gets a reading head (five-letter words, no marks) |
|---|---|---|---|
| 150 wpm | highlights only: no head | 2.7 s | 10 words |
| 250 wpm | paced, the head takes 2.76 s | 3.9 s | 16 words |
| 400 wpm | paced, the head takes 1.73 s | 3.0 s | 26 words |

At 250 wpm the head stays 240 ms on an ordinary word, 408 ms on
"loooove" (the hold stretches it by 1.7), 432 ms on "(honestly)." and
360 ms on words that end a sentence.

What it says: the five-second cap gives the slowest readers the least
pacing. At 150 wpm even this nine-word sentence gets no reading head.

What was done about it (choice Q25): the cap is for motion that starts
by itself. A read-back the writer asks for, and can stop with Escape,
may run to 30 s (order 0050). Messages that arrive unasked keep the 5 s
cap, so for those a slow reader still gets highlights only; whether
arrival should instead wait for a tap ("read it to me") is an open
product question.

### H2: the swell (order 0045, 2026-11-17)

Typing "I loooove it " in the Plain style: the cue is `loooove -> swell
0.7 (elongation)`. Sampled on every frame (38 frames over about six
seconds in the headless browser), the highest lift was 0.175 em against a designed
0.175, each letter's peak came no earlier than the one before it (the
peaks spread over 609 ms), and every letter was at rest 5.5 s after the
cue. So the motion matches the measurement it was built from. In the
first screenshot the glow's halo was too strong for a held note; the
foreman halved it (choice Q28). Not judged by a person yet.

### H3 and H7: the new channels and the inks (order 0049, 2026-11-17)

The renderer draws weight, focus, slant, stretch, fill and ink on the
same single instanced mesh and material: still one draw call. Measured on
a still type specimen (`/lab/?specimen`), not in passing frames: inked
area grows with weight (1016 < 1565 < 2056 < 2952 < 3939 px from −1 to
+1), soft pixels grow with blur, the top of a slanted word shifts 8.8 px
left or 8.4 px right, a fill of 0.5 leaves the left half 2.7 times as
inked as the right.

Cost: the builder timed nine readings each of the old and new renderer
in software WebGL: about 106 ms a frame before, about 123 ms after, so
roughly 15% slower from the larger fragment shader. That is a headless
CPU renderer; on a GPU the difference is probably invisible, but nobody
has measured it. The `?debug` overlay on a real machine would settle it.

Inks, WCAG contrast against their background (all at least 4.5:1):

| ink | on paper | contrast | on the stage | contrast |
|---|---|---|---|---|
| text | `#1d1a16` | 15.4 | `#efe6d6` | 15.1 |
| signal | `#c2401c` | 4.6 | `#ff7a55` | 7.3 |
| gold | `#7d5a00` | 5.6 | `#e8b84a` | 10.1 |
| cool | `#1f5f8b` | 6.1 | `#7fb8e6` | 8.8 |
| muted | `#5a5044` | 7.0 | `#b9ad9a` | 8.5 |

The ratios are the builder's side calculation with the same formula as
`ink/contrast`; the test asserts only that each is at least 4.5.

### H4: can an agent write scores? (order 0048, run 2026-11-17)

`bb lab:score-eval`: 28 cases through the `claude` CLI with no tools,
given one page (`resources/agent/score-reference.md`). 18 fixed lines
each meant three ways (tender, furious, wry), 6 replies the agent
composes, 4 briefs that ask for a written-out patch. One run, one model
(the same family as the foreman and the factory), so this is a first
reading, not a benchmark.

| measure | result |
|---|---|
| replies that parsed as JSON | 28 of 28 |
| valid scores | 28 of 28 |
| clean (nothing dropped, every phrase found) | 28 of 28 |
| fixed text left unchanged | 21 of 22 |
| marks per reply | 1.57 on average, 1 to 3 |
| kinds used | land 13, stress 13, hold 10, doubt 7, turn 1; no aside, build or voice |
| tender lines: mean energy, warmth | −0.53, +0.84 (right sign 6 of 6) |
| furious lines: mean energy, warmth | +0.86, −0.88 (right sign 6 of 6) |
| wry lines: energy, warmth | +0.30, −0.30 on all six |
| patch briefs that got a patch | 4 of 4 |
| patches with no problems (the strict check) | 4 of 4 |
| cost | $0.96, about 3 cents a reply |

What it says:

- From one page, this agent writes scores that are valid the first time,
  restrained (it never used more than three marks), and that change with
  what is meant: the same six lines came back as holds when tender,
  stresses and landings when furious, doubts when wry.
- It can write patches that pass the safety check, and it composes them:
  for "Round and round" it wrote a travelling wave (sine on `y` and
  cosine on `rz`, phased by position); for "Gravity" a squash, a weight
  swell and a spring return.
- When it composed its own replies it also used dress (gilt on "you
  shipped it") and chose its own style point.

What it found wrong with the page and the harness:

- **The page never said which way `y` points**, and the agent's "sag"
  went up. Fixed in the reference (up is +).
- The one changed text was the harness's fault: in a patch case the agent
  appended the brief to the message. The prompt for those cases should
  mark the text off more clearly.
- Three kinds were never used (aside, build, voice). The cases did not
  call for them; a wider set of cases should.
- "Wry" came back as the identical +0.30/−0.30 every time: consistent,
  but it suggests the agent maps the feeling's words to one fixed point.

