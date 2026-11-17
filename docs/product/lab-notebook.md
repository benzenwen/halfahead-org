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
| H2 | The held-syllable swell reads as subtle and reserved in typed text when tied to elongation | Build `:swell` from the measurements; property tests on its bounds; Ben's eyes | pending |
| H3 | Weight, focus, ink, stretch, slant and fill cost nothing extra to draw | Add them as per-instance attributes of the one instanced mesh; compare frame times before and after | pending |
| H4 | An agent can write valid, varied scores from one page of reference | N prompts through the `claude` CLI; rate of schema-valid scores on the first try; spread of marks across moods | pending |
| H5 | A style can be learned from about a dozen flicks | Simulate readers with hidden style points and noisy choices; error against number of flicks | **Holds for a careful chooser, fails for a noisy one** (below) |
| H6 | Pacing by the reader's beat keeps every performance inside reading time and the 5 s cap | Properties over generated scores and paces from 100 to 800 words a minute | **Holds, and shows the cap starves slow readers** (below) |
| H7 | Dress (versal, rubric, gilt) stays readable on paper and on the dark stage | Contrast of each ink against both backgrounds ≥ 4.5:1, computed; screenshots | pending |

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

