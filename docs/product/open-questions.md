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
| Q15 | Factory spend | **Settled by you:** the cap is $250 (raised from $150 on 2026-11-17, when $142.64 was spent) | Orders cost about $7 on this model, not $3 | Say a number |
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
| Q28 | How bright is the swell's glow? | 0.22 at full intensity, half the first value | In the first screenshot the halo around "loooove" read as an alarm; the Music app's glow is soft | One number in `lab.gestures` |
| Q29 | Which way is up, for an agent? | The reference now says `y` up is +, `x` right is + | In the H4 run an agent's "sag" went up because the page never said | Low |
| Q30 | What does Plain mean? | Plain keeps plain motion but a flourish of 0.35 (a held word, a landing and capitals perform; asides whisper; the strongest stress is gilt); dress is decided when the text changes and stays; a reading keeps the style it started in (order 0055). You said "keep going" after I flagged it | As first built, Plain had no flourish, Auto (the default) falls back to Plain, and dress vanished six seconds after typing stopped | One order to change the number |
| Q31 | The glint's halo | Left vermilion for now, so gold glints pink | The renderer's glow has always been vermilion; tinting it with the letter's ink is a small shader change, outside the dress order's scope | One small order |
| Q32 | What an "at rest" letter is | Since dress, rest includes its dress (a whispered letter rests smaller); tests mean "stopped changing" | The builder changed one browser check to say so, and said so | — |
| Q33 | The board did not fit $12 | Left the cap alone; split the flick into two orders (0053 pure, 0058 page) and resumed the board from its saved diff, so its fourth run paid for a review and not a third build | The charter says the cap is yours. The board took four runs and $26.87: one lost to a container restart, two that built everything and then had too little left for the review (a rerun throws the built work away) | Yours: `:usd-per-order` in `lupa.charter.edn`; $20 fits a page-sized order with one rework |
| Q34 | How slow may the calmest style be? | Tempo 1.5 at energy 0 (it was 1.6); from energy 0.2 up the curve is as it was, so Elegant, Plain and the rest are unchanged | At 1.6 a point at energy 0 ran 5280 ms, past the 5 s cap; my first fix moved Elegant too and broke a test, and the builder's fourth knot was the better answer | One line in `lab.style` |
| Q35 | Warmth, the fourth axis of a style | Left in the style point; the flick passes over it, because nothing on a line shows it | H8: warmth decides one thing (a dressed stress is gold or red), so the flick asked about it in 0 to 7% of runs. It needs more to do (the temperature of a glow, a gentler gesture) or it should go | A design choice more than an order |

## Still yours from before

- Level 0 of the reveal is a blank page, not a Slack-like composer.
- Experiment 2 (the "Previously on" recap, which would send the
  conversation to Scout) and experiment 5 (the lab inside the app).
- Jon: is his renderer three.js, and would he license a scene?
- The glossary voice (`src/halfahead/lab/glossary.cljc`).
