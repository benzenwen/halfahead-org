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
| Q18 | Swell's numbers | Lift 0.2 em at full intensity (0.16 em as usually played), 120 ms a letter, 0.9 s rise, 1.5 s settle, from your recording | Measured from 250 frames; my reading of the `l` was unreliable, the other three letters agree | Low |

## Still yours from before

- Level 0 of the reveal is a blank page, not a Slack-like composer.
- Experiment 2 (the "Previously on" recap, which would send the
  conversation to Scout) and experiment 5 (the lab inside the app).
- Jon: is his renderer three.js, and would he license a scene?
- The glossary voice (`src/halfahead/lab/glossary.cljc`).
