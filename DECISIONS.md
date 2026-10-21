# Decisions

Every decision that shapes Half Ahead, newest last. Each says who made
it, what else was considered, and what it would cost to reverse. **Ben**
means the founder decided; **foreman** means the building session chose a
default that Ben can overturn. Big ones are flagged ★.

## 2026-10-21 — kickoff

### H1 ★ Half Ahead is Lupa's product loop — foreman default, Ben: "go ahead"
Half Ahead is built by Lupa driving basefactory, in consume mode
(`bb --config ../lupa/bb.edn lupa tick` from this repo). It is the first
Lupa product with real users, which is what Lupa's own plan says it needs
before it can earn darkness.
*Alternatives:* foreman-driven basefactory runs without Lupa (thinkfishes'
path); a hand-built prototype. *Reverse:* cheap; orders are basefactory
orders either way.

### H2 The app lives in this repository, which is public — foreman default
Openness is a stated value, and the client-side Firebase config is public
by design. The cost is hygiene: no secrets, no members' personal data, no
text copied from private repositories (charter invariant).
*Alternatives:* a private app repo with this one for community docs.
*Reverse:* moderate (move history).

### H3 ★ Stack: ClojureScript + Firebase, front end on GitHub Pages — Ben (Firebase, Pages), foreman (the rest)
- ClojureScript with shadow-cljs, Replicant (views as pure hiccup, events
  as data) and Malli (schema first). The same shape thinkfishes proved.
- Firebase Auth and Firestore for identity and data; local emulators
  until Ben creates the project.
- Front end deployed as static files to GitHub Pages at
  **halfahead.org** by GitHub Actions (Ben's preference). Consequences:
  sign-in uses the popup flow, because the redirect flow is unreliable
  off Firebase Hosting in browsers that block third-party storage
  (verify against Firebase's current docs before launch); the worker
  cannot live on Pages (H4).
*Alternatives:* Firebase Hosting (preview channels, same-origin auth
helper); re-frame instead of Replicant; a local-first store (Automerge)
instead of Firestore. *Reverse:* hosting is cheap to change; the view
library is medium once views exist.

### H4 The agent speaks through a worker — foreman default
A babashka worker beside the front end watches for messages addressed to
the agent, asks an engine for a reply, and writes it back. Engines sit
behind a `defmulti` on `:engine`: `:mock` for tests, the `claude` CLI for
development (no API key). basefactory's WORKER.md is the pattern. The
worker runs wherever someone runs it (a laptop, a cloud session) until
there is a reason to host it.
*Reverse:* cheap; the Firestore message shape is the contract.

### H5 ★ Flair is data: the agent picks a delivery, a pure function performs it — foreman default
A message carries its text and a `:delivery` from a small closed
vocabulary. A pure function turns text and delivery into a timeline of
beats; the view only plays timelines. The agent never emits animation
code. The invariant that makes this safe to test: **every timeline ends
showing exactly the message text.**
*Why:* testable without a browser, keeps the agent's repertoire legible
(a sidekick, not a puppeteer), and makes "correctness with narrative
flair" a property, not a hope.
*Alternatives:* free-form agent markup; CSS-only effects chosen by the
client. *Reverse:* moderate.

### H6 Accessibility is a floor, not a setting — foreman default
`prefers-reduced-motion` shows final text at once; sound is opt-in and
never autoplays; replay can be paused. Charter invariant.

### H7 Budget for the first overnight drive: $150 — Ben
Charter caps: $30 a tick, $12 an order. Lupa has no total-spend key, so
the foreman tracks the running total in the drive log and stops at the
cap. (Reported to Lupa as a finding.)

### H8 ★ Start fully lit — foreman default, per Lupa's doctrine
Review rate 1.0; `:high` risk always reviewed. Lupa cannot yet record what
a human review finds, so there is no evidence on which to dim anything.
Darkness here is earned later, by Lupa's Phase 2 instruments.

### H9 Where work lands — Ben
- The factory merges only into the integration branch
  `claude/laughing-dijkstra-g9b3k8`; Ben reviews a **draft PR** to `main`.
- Suggestions for Lupa go as PRs to Lupa's private design repo, since Lupa
  is headed for open source. Suggestions for basefactory go as field
  reports to basefactory. Neither is edited directly.

### H10 Domain: halfahead.org — Ben
Owned by Ben. Pages custom domain when the first deploy is approved.

### H11 ★ The agent is "Scout", with a drafted persona — foreman default, needs Ben
`resources/agent/persona.md` is a v0 character written from the brief:
Ben's agent, never pretending to be human, a curious and precise sidekick,
mildly irreverent, honest about uncertainty, short replies. "Scout" comes
from the brief's "share our scouting". Both the name and the character are
Ben's to change; the worker reads the file on start.
*First real turn (claude engine, $0.07):* asked "what is the most humane
thing an agent can do in a conversation?", Scout answered "Leave the other
person room to be right, and to be wrong, without hurrying them…". It also
echoed the persona's own phrasing; a sign the persona is too quotable.

### H12 Firestore rules deny everything until the rules order — foreman
Only the emulator's admin (the worker) reads and writes. Client access
arrives with tested rules.
