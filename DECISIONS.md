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

### H13 ★ The agent has no tools — foreman, a security finding
Scout's first real turn ran `claude -p` with the host session's defaults:
built-in file and shell tools, and every MCP connector the host session
had, which in the founder's cloud session were 40 Gmail, Calendar and
Drive tools. Measured: `--tools ""` removes only the built-ins; adding
`--disallowedTools "mcp__*"` leaves none (and costs about a quarter as
much). `--strict-mcp-config` did not help; `--bare` breaks the host's
authentication. Order 0008 makes the engine run with no tools at all and
the persona as its system prompt. No tool use was observed in the turn
that ran before the fix.
*Rule:* an agent that talks to people other than its owner never inherits
its host's tools or connectors; any tool it gets is granted explicitly.

### H14 Every performance ends within five seconds — foreman, from research
WCAG 2.2.2 (Level A) requires a pause control for moving content that
starts automatically and lasts more than five seconds. The cap was 8000
ms; it is now 5000 (`halfahead.schema/max-performance-ms`). A skip
control stays regardless. Open question: whether a live transcript counts
as "auto-updating content", which has no five-second exception; the
updates follow the reader's own participation, so likely not.
See docs/research/conversation-design.md.

### H15 ★ The look: title sequence, with one game-show card and a Bit — foreman default, needs Ben
Three directions are in docs/research/conversation-design.md (game-show
card, title sequence, Tron grid). Default chosen for the first look order:
**title sequence** (paper and ink, messages set like title cards, a coda
credit roll later), plus the `:headline` slam from the game-show card used
rarely, plus a Bit-like glyph whose shape is Scout's state. Reading comes
first; flair marks moments. *Reverse:* cheap; it is CSS and a few view
elements, and the timelines don't change.

### H16 ★ Agents belong only through an accountable human; an invite tree; written social rules — proposal, needs Ben
From docs/research/communities.md: Moltbook (Jan 2026) showed what
agent-only spaces become without accountable owners. Proposed for when
others join: (1) every agent hangs under its human, who answers for it;
(2) every member hangs under who invited them (Lobsters); (3) a short set
of social norms modelled on Recurse's, with one change to resolve the
tension with "correctness": *challenge the point, not the person; correct
what matters to the point; let the rest go.* (4) an "off the record" mode
alongside replay. Nothing built yet.
