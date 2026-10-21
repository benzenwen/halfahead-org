# Design doctrine: Half Ahead

Injected into every plan, build, and review prompt; the review station
judges diffs against it. Product context is in PRODUCT.md, the data model in
docs/SCHEMA.md, and the reasons behind both in DECISIONS.md.

## Architecture: functional core, imperative shell

- **One plain state map.** Views are pure functions from state to hiccup.
  Event handlers in hiccup are data (`{:on {:click [[:composer/send]]}}`),
  never functions.
- **Actions are handled purely:** `(handle state action) -> {:state s
  :effects [[:fx/name & args] ...]}`. No I/O, clock, or randomness inside;
  the time, ids, and seeds an action needs travel in the action.
- **Effects are data,** executed only by interpreters (`halfahead.fx.*` in
  the browser, `halfahead.worker.io` in the worker). Interpreters stay thin:
  effect in, call out, result back as an action. Anything worth testing
  lives in the pure core.
- **One core, two hosts.** Pure code is `.cljc` and must run both in the
  browser (ClojureScript) and in the worker (Babashka). Do not use
  JVM-only or browser-only interop in `.cljc`; isolate it behind reader
  conditionals only at the edges, and prefer not to.

## Data first

- **`halfahead.schema` (Malli) is the source of truth** for every persisted
  entity and every message between processes. docs/SCHEMA.md is its
  human-readable mirror; the two must agree.
- **Never edit `halfahead.schema` unless the work order says to.** If a
  shape seems wrong, say so in your final message; do not route around it.
- **Decode at the boundary.** Data read from Firestore or from an engine is
  decoded and validated before it enters state. Invalid data raises
  `ex-info` with a namespaced `:type`; it is never passed along silently.
- Firestore field names are camelCase; entity maps use plain kebab-case
  keyword keys, and the entity's kind is the `:ha/*` schema keyword. Only
  the codec converts between them.
- Assistive technology gets the full text at once; animated text is
  `aria-hidden` beside it.
- **Derive, don't store,** when a fact can be computed from others (for
  example, whether a message still awaits the agent's reply).

## Flair is data

- A message carries text and a `:delivery` from the closed vocabulary in
  the schema. `halfahead.perform` turns text and delivery into a
  **timeline**: plain data, deterministic for a given seed.
- **The final frame of every timeline shows exactly the message text.**
  Flair may reorder, delay, scale, or highlight, but never add, drop, or
  change a character of content. Test this as a property.
- Timelines have bounded duration (see the schema), so nobody waits on a
  flourish. With reduced motion, the timeline has zero duration.
- The view **plays** timelines; it never invents motion of its own.
- The agent chooses a delivery; it never emits markup, styles, or code.
  An unknown delivery decodes to `:plain`.

## The agent

- It is always shown as an agent, and as whose agent it is. It never
  claims to be human.
- It has a character, not a costume: a point of view, a limited and
  legible repertoire, and the honesty to say "I don't know".
- While it is working, the conversation shows that it is (presence), so a
  person is never left in silence.

## Humane by default

- No engagement mechanics: no streaks, counts of likes, infinite feeds, or
  notifications designed to pull people back.
- **Accessible:** semantic elements, an accessible name on every
  icon-only control, visible focus, `prefers-reduced-motion` honoured,
  sound opt-in and never autoplayed, readable measure (about 70ch),
  layouts that work at 360 px wide.
- **Honest states:** empty, loading, and error states are designed, never
  blank.

## Security

- Firestore rules are the only server for the browser. **Deny by
  default.** Every rules change ships with emulator tests for both allowed
  and denied cases.
- Never trust identity or time from the client: author ids equal
  `request.auth.uid`, timestamps equal `request.time`.
- Tests and development use the emulator with project id `demo-halfahead`
  only. Nothing in tests touches a real project or the network.
- This repository is public. No secrets, keys beyond the public web
  config, members' personal data, or text from private repositories.

## Modules and code (Ousterhout)

- **Deep modules:** a small public surface over real work; everything else
  is `defn-`. No pass-through layers, no speculative options, no protocols
  with one implementation.
- **Define errors out of existence** where the semantics allow; real errors
  are `ex-info` with a `:type`, never magic values.
- Names are the first documentation. Comments say why, never what.
- Strategic over tactical: if a change makes the design worse, fix the
  design first.

## Tests

- Every acceptance criterion becomes a test that would fail if the feature
  were deleted. Prefer property checks where a pure function has an
  invariant.
- `bb test` runs offline: the Babashka suite over `test/**/*_test.clj[c]`,
  then every `.cljc` test again under node (so the shared core is proven
  on both hosts), then a compile of the app. Tests of shared code are
  `.cljc`; worker-only tests are `.clj`. Views are tested by asserting on
  the hiccup they return.
- Test files mirror source: `src/halfahead/x.cljc` →
  `test/halfahead/x_test.cljc`. The runner discovers them; **never edit
  `test/halfahead/test_runner.clj`.**

## Change hygiene

- Stay inside the order's scope. `bb.edn`, `package.json`,
  `shadow-cljs.edn`, and `firebase.json` change only when the order says
  so.
- Never kill processes you did not start; clean up by PID.
- Small diffs. If a diff needs a tour guide, split the order.
