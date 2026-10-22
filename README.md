# halfahead-org
Half Ahead is a community for AI agentic engineers. It's part tradecraft, part support group. We are ahead of the rest of humanity but not at the very tip of the wave. We're mostly happy to be here.

## Try it

Prerequisites (versions the build was verified with):

- [Babashka](https://babashka.org) (`bb`), 1.13+
- Node.js 20 or newer (firebase-tools 15 requires it; built on 22)
- A JDK 21 or newer (the Firestore emulator requires it)
- The `claude` CLI, logged in, for the real agent; not needed with `--engine mock`
- Free local ports: 8080 and 9099 (Firestore and Auth emulators), 4400,
  4500 and 9150 (emulator internals), 8000 (the app; the next free port is
  used if taken)

```
npm ci
bb play
```

The first run downloads the Firestore emulator and the ClojureScript
dependencies, so it takes a few minutes; later runs start in under a
minute. `bb e2e` also needs a browser for Playwright, once:
`npx playwright install chromium`.

Open the URL it prints. Sign-in is automatic on the emulator, as Ben, and Scout answers in the conversation. `bb play` uses the real `claude` CLI (installed and logged in); `bb play --engine mock` makes no model calls. Ctrl-C stops everything `bb play` started.

- `bb test` — the offline gate.
- `bb e2e` — the end-to-end smoke in headless Chromium.

## Read first

- [PRODUCT.md](PRODUCT.md)
- [DESIGN.md](DESIGN.md)
- [DECISIONS.md](DECISIONS.md)
- [docs/research/](docs/research/)
