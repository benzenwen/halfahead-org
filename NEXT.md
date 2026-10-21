# Next: what needs Ben, and what the factory does after

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
