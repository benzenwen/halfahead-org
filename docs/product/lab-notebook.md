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
| H1 | One small synthesizer (≤ 10 curves, 4 waves, windows, stagger) can express every effect the lab has | Re-express the 11 effects as patches; deltas must match the hand-written functions at sampled times | pending |
| H2 | The held-syllable swell reads as subtle and reserved in typed text when tied to elongation | Build `:swell` from the measurements; property tests on its bounds; Ben's eyes | pending |
| H3 | Weight, focus, ink, stretch, slant and fill cost nothing extra to draw | Add them as per-instance attributes of the one instanced mesh; compare frame times before and after | pending |
| H4 | An agent can write valid, varied scores from one page of reference | N prompts through the `claude` CLI; rate of schema-valid scores on the first try; spread of marks across moods | pending |
| H5 | A style can be learned from about a dozen flicks | Simulate readers with hidden style points and noisy choices; error against number of flicks | pending |
| H6 | Pacing by the reader's beat keeps every performance inside reading time and the 5 s cap | Properties over generated scores and paces from 120 to 600 words a minute | pending |
| H7 | Dress (versal, rubric, gilt) stays readable on paper and on the dark stage | Contrast of each ink against both backgrounds ≥ 4.5:1, computed; screenshots | pending |
