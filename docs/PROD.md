# The path to production

*Foreman, 2026-11-17, answering Ben: "what do I need to give you to let
you push to prod?" Short version: two settings, one sentence, and one
decision about the charter. Nothing here has been deployed.*

## What "prod" is, in two steps

**Step 1: the lab at halfahead.org.** The typing lab is static files and
runs entirely in the visitor's browser (drafts never leave the device), so
GitHub Pages is all it needs. The site would be a one-screen landing page
with a link to `/lab/`.

**Step 2: the app (conversations with Scout).** Not ready to go public.
Today it only talks to local emulators. It needs a real Firebase project,
sign-in providers, the Firestore rules deployed from CI, and somewhere for
the Scout worker to run with a model key and the spend cap. That is a
separate decision with real cost and abuse questions; the foreman will
write it up when you want it.

## What exists now

- `.github/workflows/ci.yml` runs the offline gate (`bb test`) on GitHub
  for every pull request, on main, and on the foreman's branches. It
  deploys nothing. Until now the only gate ran inside the foreman's
  container.
- Workflow files can be pushed by the foreman's GitHub access (tested).

## What you need to give, for step 1

1. **A sentence**, here in chat: *"You may merge your own pull requests
   to main when the gate is green, and deploy the lab to GitHub Pages."*
   The foreman's rules require your words for anything outward-facing.
2. **The charter.** `lupa.charter.edn` is yours, and two lines forbid
   this today:
   - invariant: "Nothing merges to main without a human; the factory
     merges only into integration branches."
   - escalate: "Anything that would deploy, send a message to a person, or
     spend money outside the agent engine."

   Proposed wording, if you agree (say so and the foreman makes the
   change in a pull request for you to merge):
   - "The foreman may merge to main when the gate is green on GitHub and
     no escalation is open; a human can revert. The factory itself merges
     only into integration branches."
   - "Anything that would deploy outside GitHub Pages, send a message to
     a person, or spend money outside the agent engine."
3. **Repository setting**: Settings → Pages → Build and deployment →
   Source: **GitHub Actions**.
4. **The domain**: Settings → Pages → Custom domain: `halfahead.org`,
   and at your DNS provider the four A records GitHub documents for an
   apex domain (plus AAAA if you want IPv6), or a CNAME for `www`. Tick
   "Enforce HTTPS" once the certificate is issued.

After 1 to 3 the foreman adds a deploy workflow (build the lab, publish
the landing page and `/lab/`), merges, and checks the live page in a
browser. The domain (4) can follow; until then the site is at
`benzenwen.github.io/halfahead-org`.

## What stays as it is

- **Review stays at 1.0.** Autonomy here means the foreman merges and
  deploys without asking; it does not mean less review. Review catches
  about half of the defects we know of, so nothing has earned less.
- **Merge commits, not squashes**, so Lupa can still tell what is merged.
- **The $150 cap** on factory spend, until you name another number.
- **Reverting** is one click: Pages redeploys whatever main holds.
