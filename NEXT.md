# Next: what needs Ben, and what the factory does after

## The score drive, 2026-11-17 (newest first)

The design is `docs/product/score.md`; what was measured is in
`docs/product/lab-notebook.md`; every choice made without you is in
`docs/product/open-questions.md`; the path to production is
`docs/PROD.md`.

### Try it (five minutes)

```sh
bb lab          # prints the URL of /lab/
```

- **Watch the tour first.** Add `?tour` to the lab's address (or press
  Tour): in about 40 seconds the lab types for itself, dresses a line in
  gold, holds a note, dims the lights, reads a line back two ways and
  takes the stage, with a caption for each scene. Any key stops it.
- **The board.** Press **Board** (or Ctrl+B). Type `we did it` and press
  STRESS, or Alt+1 without leaving the text. Hold a pad longer and it
  lands harder. Select one word and press LAND. BOOM is the card. The
  square is your style: energy across, flourish up; drag the dot to the
  top right and press a pad again. The picker then reads **Mine**.
- **The flick.** Type a line with something in it, such as `I loooove
  this (honestly). But maybe not. Ship it!`, and press **Which?** (or
  Ctrl+/). The lab reads it back twice, A then B; press the left or right
  arrow for the one you prefer. Your style moves toward it and the picker
  reads **Mine**. Ask again a dozen times and it runs out of things to
  ask: that is the exam ending, not a fault. It learns speed and bounce
  from any line, flourish only from a line with something to flourish,
  and warmth not at all yet (H8 in the notebook).
- **The held note.** Type `I loooove it ` and watch "loooove": each
  letter lifts in turn, as in the Music app's lyrics.
- **Read back.** Type `I loooove this (honestly). But maybe not. Ship
  it!`, press **Ctrl+Enter** (any style now performs something; Elegant
  and Caffeinated differ most). The line dims and fills
  with ink at the Pace slider's words a minute, and each mark performs
  as the head reaches it. Try 150 and 400. Escape stops it. Then pick
  Caffeinated and do it again: the same marks, a different performance.
- **Dress.** Add `?reveal=1` (the paper page), pick Elegant, type `This
  is *the one thing* that matters (trust me).`: gold and a whisper. Move
  the mouse over the gold. In Caffeinated a sentence of twelve words or
  more gets a raised initial.
- **The gestures.** Type their names: `swell bloom slam hush lean nod
  shiver draw` (and the earlier eleven).
- **The type specimen.** `/lab/?specimen`, and `&reveal=0` for paper:
  weight, focus, slant, stretch, fill and the five inks.
- **An agent writing scores.** `bb lab:score-eval` (28 model calls, about
  $1); the page it is given is `resources/agent/score-reference.md`.
- `?debug` shows what fired; `?reveal=3` skips to the dark stage;
  `?webgl` forces WebGL2 if WebGPU ever misbehaves (the lab also falls
  back by itself now).

### Decisions you owe

1. **The cap.** Settled: you raised it to $250 on 2026-11-17 ($142.64
   was spent). The queue is running again.
2. **Production.** `docs/PROD.md`: one sentence from you, two repository
   settings, and the charter wording. The deploy workflow is written and
   switched off.
3. **What Plain means (Q30).** Chosen by the foreman after you said keep
   going: Plain keeps its plain motion but gets a modest flourish (0.35),
   dress and readings keep the style they began in, and bareness is the
   reveal level's job (order 0055). Say so if you want it otherwise.
4. **A pull request for the score drive.** Everything since 17 November
   is on `claude/laughing-dijkstra-g9b3k8`, not on main, and no pull
   request is open. Say the word and the foreman opens one (merge
   commit, never squash: Q12).
5. **The per-order cap (Q33).** The charter's $12 an order was set when
   an order cost about $3. The board cost $7.90 to build, and twice had
   too little left for its review. The foreman's answer was smaller
   orders and a resume, not a bigger cap; the cap is yours. $20 would
   fit a page-sized order with one rework.
6. **Review the choices.** `docs/product/open-questions.md`, Q1 to Q37.
   The ones that are expensive to change later are Q1 (the first tier is
   rhetorical), Q5 (patches are bounded) and Q25 (a requested read-back
   may run past five seconds).

### Next orders, in the order the foreman would run them

| order | what | why |
|---|---|---|
| done | 0055: Plain's flourish, dress that stays, a reading keeps its style, gold glows gold | Q30, Q31 |
| done | 0057: the WebGPU guard | The lab was blank on WebGPU for nine hours; it cannot ship that way again |
| done | 0056: the tour | "Is there a demo to run and look at" |
| done | 0051: the board | People get an instrument, not parameters |
| done | 0053, 0059: the flick, the pure half | One exam as data; an axis is asked both ways and can finish (H8) |
| done | 0058: the flick, the page | 'Which?' reads the line back two ways; your style moves toward your pick |
| done | 0060: the asterisks of a dressed stress recede | Your first WebGPU screenshot: `*this*` in gold with both asterisks at full strength (Q37) |
| (new) | Board and flick polish | From their reviews, none blocking. Board: a pad press that places nothing still raises the reveal level; a key held on a focused pad shares its hold with Alt+digit; BOOM keeps the director off that phrase; Mine sounds like Plain. Flick: choosing A under a named style still moves you to Mine at that style's point (a point cannot hold everything a named style has); the browser check's "energy has not fallen" starts at the clamp, so it proves little; nothing checks an exam after a reload; the learner's stored shape has no schema entry |
| (new) | The tour shows the board and the flick | The demo that plays by itself stops before the two instruments people get |
| (new) | The gate says what failed | A failed gate reported a stack trace and no test name (basefactory finding 24); the product's `bb test` could print its failures last |
| (new) | Scout writes scores | Bring the score into the app: `:ha/engine-reply` carries one (your yes on experiment 5) |
| (new) | Glosses in the margin | The illuminator's other half: agents annotate beside the text |

## The first drive, 2026-10-21

Written by the foreman at the end of the first overnight drive
(2026-10-21). DECISIONS.md has the reasoning; this page is the to-do list.

## Try it (five minutes)

```sh
npm ci
bb play            # emulators + seed + Scout (claude engine) + the app; prints the URL
bb play --engine mock   # same, no model calls
```

Sign-in is automatic on the emulator (as Ben). Scout runs through the
`claude` CLI on the machine where you run `bb play`, with **no tools at
all** (H13), at about $0.01 and 15–20 s a reply. Things to try:

- the delivery picker under the composer (plain, typed, headline, aside,
  credits); wrap words in `*asterisks*` to choose what a headline stresses;
- **Replay scene** (the whole conversation re-performed in compressed time;
  pause, resume, stop) and **End scene** (the credit roll; Escape closes);
- **Sound** in Preferences (opt-in, synthesized cues, one per delivery);
- your OS's reduced-motion and dark-mode settings (both are honoured).

`bb e2e` runs six browser steps and leaves screenshots in `out/e2e/`.

## Decisions you owe (★ in DECISIONS.md)

1. **H11: Scout.** Name and character are a draft
   (`resources/agent/persona.md`). Edit it, or tell the foreman what to
   change. Known flaw: Scout quoted the persona back almost word for word.
2. **H15: the look.** Title sequence (paper and ink), with a rare
   game-show slam and a Bit-like glyph. Keep, or pick another direction in
   `docs/research/conversation-design.md`.
3. **H16: people and their agents.** Four proposals: agents only under an
   accountable human; an invite tree; written social norms (with
   "challenge the point, not the person; correct what matters to the
   point"); an off-the-record mode.
4. **Review.** The digest lists every order as needing review (the rate is
   1.0). Lupa cannot record what you find yet, so for now comment on the
   PR per order, e.g. "0009 clean" or "0011 found: …". The foreman will
   transcribe them into Lupa's format once it has one (lupa-internal#1,
   finding 4). This is the only way Half Ahead can start earning darkness.

## Where to start reviewing

Rate 1.0 means everything, but the evidence says where the review
station was weakest. Its known misses tonight (each fixed by a later
order that carries `:lupa/fixes`):

| order | what review shipped | found by | fixed by |
|---|---|---|---|
| 0011 app shell | Firestore Timestamps never converted (the page could not load) | 0013's reviewer, running `bb e2e` | 0014 |
| 0011 app shell | unhinted interop that advanced compilation could break | foreman's gate audit | 0015 |
| 0020 credits roll | lines run together, focus never in the dialog | foreman's browser check | 0023 |
| 0005 worker | read the first 300 messages by id, not the latest | foreman audit | 0007 |

Then the security-critical ones: 0008 (agent isolation), 0009 and 0012
(Firestore rules), 0021 (spend cap). Known recall on these defects: 6
caught, 4 shipped. That is why nothing dims yet.

## Authorizations only you can give

### A. A Firebase project (needed before anything is deployed)

1. In the Firebase console, add a project (for example "halfahead"); note
   its project ID.
2. Authentication → Get started → Sign-in method → enable **Google**.
   Settings → Authorized domains → add **halfahead.org**.
3. Firestore → Create database, production mode, a region near you.
4. Project settings → Your apps → add a **Web** app; copy the
   `firebaseConfig` object. Those values are public by design; put them in
   the PR thread or a new `public/firebase-config.json`.
5. Rules deploy: either run `npx firebase deploy --only firestore:rules
   --project <id>` yourself, or create a service account with the
   *Firebase Rules Admin* role and add its JSON key as the GitHub Actions
   secret `FIREBASE_SERVICE_ACCOUNT` so CI can.

Then the factory builds: production config chosen by hostname (emulators
stay on localhost), Google sign-in with a popup (H3), and the worker's
real-project credentials.

### B. GitHub Pages at halfahead.org

1. Repo Settings → Pages → Source: **GitHub Actions**.
2. Custom domain **halfahead.org**. At your registrar: apex `A` records to
   185.199.108.153, 185.199.109.153, 185.199.110.153, 185.199.111.153
   (and `AAAA` 2606:50c0:8000::153, 2606:50c0:8001::153,
   2606:50c0:8002::153, 2606:50c0:8003::153); `CNAME www` →
   benzenwen.github.io. Then *Enforce HTTPS*.
3. Verify the domain for your account (Settings → Pages → Verified
   domains), so nobody else can claim it.

Then the factory writes the workflow: `bb test`, `bb release`, deploy.

### C. Where Scout lives once others join

The CLI login on a laptop lapses and the laptop sleeps. Choose: your Mac
with launchd (basefactory's WORKER.md recipe), a small always-on machine,
or a Cloud Run job. Any of them needs an **Anthropic API key** in that
environment's secrets, and a **monthly ceiling** for model spend (the
worker already logs cost per reply in `.halfahead/worker-ledger.jsonl`).

### D. The dark loop

A scheduled task could run `lupa tick` nightly on this repo with a spend
cap and open a morning PR. **Not recommended yet:** nothing records what
your reviews find, so there is no evidence to dim anything; a nightly loop
now would be unattended, not earned. Sequence: (1) the review instrument
in Lupa; (2) a nightly loop at review rate 1.0 producing a morning PR;
(3) lower rates per area only on recorded evidence.

### E. The field reports

Merge or comment: benzenwen/basefactory#37 and benzenwen/lupa-internal#1.
The first leads with a security finding worth reading even if nothing else
is: factory agents inherit your session's Gmail, Calendar and Drive
connectors.

### F. Friends

When you are ready to invite people, send the list privately; it never
goes in this public repository.

## What the factory does next, once you say go

- Production config and Google sign-in (needs A).
- Pages deploy workflow (needs B).
- Off the record, the invite tree, the norms page (needs H16).
- Opt-in sound cues, synthesized (no audio assets), redundant with
  something visible.
- Scout cites sources; the credit roll lists them.
- Larger orders, to test the deck's question: how chunky can an order be
  before the monkey has to intervene?
