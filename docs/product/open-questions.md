# Open questions and choices made without you

*For Ben. You said: "if there are big questions, make your best aesthetic
choice and keep going, but track them." Each entry is a choice the
foreman made alone, what it rests on, and what changing it would cost.
Newest drive first. Strike through or annotate anything you disagree
with; the cheap ones can flip in one order.*

## Drive of 2026-11-17 (the score)

| # | question | choice | why | cost to change |
|---|---|---|---|---|
| Q1 | Is the first tier graphic or emotional? | Neither: **rhetorical** (eight marks), coloured by emotion, rendered graphically by a style | Writers mean "this matters", not "scale 1.3"; and a style can only reinterpret a mark that is not already graphic | High once agents write scores; decide before R2 ships widely |
| Q2 | How many marks? | Eight: stress, hold, aside, turn, doubt, build, land, voice | Small enough to remember, like expression marks; each has a plain-text tell | Low to add, medium to remove |
| Q3 | Emotion as names or numbers? | Two numbers (energy, warmth) with named feelings as shorthand | Continuous, so styles interpolate; the circumplex is the best-evidenced simple model | Low |
| Q4 | The governing metaphor | Music: score, marks, performer, instrument, beat | It already solved "meaning, interpretation, instrument, time"; your Music observation and "bound pacing" pointed there | Mostly naming |
| Q5 | How free may a patch be? | **Bounded**: checked to return to rest, stay within half an em sideways, never vanish, end within 2.4 s plus stagger | Makes the DSL safe to hand to any agent; big moves belong to shots, which are budgeted | Medium: loosening bounds is easy, tightening after agents rely on them is not |
| Q6 | Colour | A closed palette of five inks, mapped to real colours by the renderer | Contrast stays the renderer's job; "illumination" needs gold and red, not a colour picker | Low |
| Q7 | Do live word cues become marks tonight? | No: cues stay as they are; marks and scores arrive beside them, for whole-message performance. Elongation ("loooove") is the one new live cue | One night should not rewrite the working path; unify in R2 | Medium |
| Q8 | The clock | Beats at the reader's pace (default 250 words a minute), a reading head, a 5 s cap with highlights for long messages | Your point about lyrics; the WCAG cap; skimming needs restraint | Low |
| Q9 | Is lasting dress "altering content"? | No (it is presentation, like bold), but it must keep contrast and a reader can switch it off | The charter says the *text* is never altered | Charter wording, yours |
| Q10 | Who illuminates? | Your own agent, on your device; tonight, rules stand in for it | "Drafts never leave the device" (H20) | — |
| Q11 | "A little darker": lower the review rate? | **No.** Review stays at 1.0; darker means I merge, choose and deploy without asking once you grant it | Review catches about half of known defects (9 of 17); nothing has earned less review | Yours: `lupa.charter.edn` |
| Q12 | Merge method to main | Merge commits, never squash | Lupa reads `factory(<id>)` commits to know what is merged; I recommended squash on the 5th and was wrong | — |
| Q13 | The 19 MB of build output in history | Left in history (the merge commit carries it) | Rewriting main's history is not worth it for a low-stakes repo | A history rewrite, if you ever want one |
| Q14 | What "prod" is first | The lab only, as a static site at halfahead.org; the app waits for a Firebase project and somewhere for Scout to run | The lab is on-device and needs no backend; the app only talks to emulators today | — |
| Q15 | Factory spend | Still inside your $150 cap ($62.96 spent before tonight) | You raised autonomy, not the cap | Say a number |
| Q16 | When does the flick ask? | Only when you ask for it (a key), never unprompted, tonight | An unprompted A/B is an interruption; earn it first | Low |
| Q17 | What counts as a held syllable? | Three or more of the same letter in a row | "loooove", "sooo", "nooo"; two is ordinary spelling | Low |
| Q18 | Swell's numbers | Lift 0.25 em at full intensity (0.17 em for "loooove", before the style's amplitude), 120 ms a letter, 0.9 s rise, 1.5 s settle, from your recording | Measured from 250 frames; my reading of the `l` was unreliable, the other three letters agree | Low |
| Q19 | How does a spring land? | Its residual fades out over the last fifth of its window, so it ends exactly at rest | The reviewer caught that my first definition snapped on the last frame while the safety check said "at rest" | Low |
| Q20 | How strict is the safety check on patches? | Sound for every letter: per-letter oscillators are bounded by their envelope, not sampled | The reviewer showed sampled checks pass patches that leave bounds; the cost is that a few harmless patches are refused | Low |
| Q21 | How do agents point at text? | By quoting the phrase, never by character offsets | Language models miscount characters; a phrase not found is ignored | Low |
| Q22 | `:draw` (ink wiping in) | Every letter wipes at once, no stagger | A staggered wipe needs letters to wait unlit before their turn, which a patch cannot say yet; the reading head does the real left-to-right wipe | Low: a "before my turn" state for patches |
| Q23 | Calm styles and intensity | A mark's intensity is its amount; the style's energy acts only through tempo and amplitude (order 0054) | As first built, energy counted twice and Elegant lifted a held word about 0.06 em, a third of the Music app's lift | One line in `lab.marks` |
| Q24 | Squash or merge? | See Q12; also reported to Lupa as a finding | — | — |
| Q25 | Does the 5 s cap apply to a read-back? | No: a reading someone asks for and can stop with Escape may run to 30 s; anything that starts by itself keeps the cap | H6 measured that under the cap a 150 wpm reader gets no reading head beyond ten words; WCAG 2.2.2 is about motion nobody requested | Charter wording is yours: "no flourish holds a reader longer than 5 s" |
| Q26 | Does a sentence with an ellipsis land? | No: "So... yes." and "I don't know... maybe" get no landing | Trailing off is the opposite of landing; restraint when unsure | Low |
| Q27 | Dress before the board | With the cap as it is, the queue runs dress (illumination) before the board, and the flick last | You asked for beautiful first; the board is power, the flick needs both | Order only |

## Still yours from before

- Level 0 of the reveal is a blank page, not a Slack-like composer.
- Experiment 2 (the "Previously on" recap, which would send the
  conversation to Scout) and experiment 5 (the lab inside the app).
- Jon: is his renderer three.js, and would he license a scene?
- The glossary voice (`src/halfahead/lab/glossary.cljc`).
