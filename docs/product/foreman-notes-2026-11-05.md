# Foreman's notes on the product outline

*2026-11-05, written while the factory ran ahead. A response to
`halfahead-org-product-outline.md`, not an edit of it. Ben decides; this is
what the factory would build, in what order, and where the research
pushes back.*

## The thesis, as I read it

Text is back at the centre (chat, terminals, LLMs), and Half Ahead's bet is
that a group chat with a sorcerer's-apprentice wand, theatrical enough to
lure AI-pilled engineers, will make text carry more than words. It starts
as a Slack knock-off with a little iMessage-style animation and reveals
more. The first users are close friends who will be honest.

The lab already holds the pieces this needs: letters as data in 3D, word
cues, styles picked or inferred from typing, a camera that is data, a
credits crawl, and (landing now) a director that stages phrases under a
spectacle budget. The question each experiment asks is how to bring them
to people without losing the reading.

## The six experiments

**1. Progressive reveal (Slack-like, then the wand).** Build it as reveal
*levels*, data in every frame: 0 flat and familiar (a normal composer,
frontal camera, no floor), 1 small in-line word effects (the iMessage
level), 2 depth (letters lift, the floor fades in), 3 the full stage
(shots, crawl, styles). The transition between levels is itself a moment:
letters peel off the flat surface into space. Levels advance by use (cued
words, time) or by a deliberate gesture (a flick of the mouse, a long
press). Dissolving the *whole* UI into particles is a renderer feat: sample
each element's box into particles and let the DOM element fade as they
fly. That is Jon territory; the seam is the same (data in, frames out).
*Factory next: reveal levels in the lab.*

**2. Catch-up and skimming.** Pushback first: the research is clear that
speed-reading presentations (one word at a time, RSVP) trade comprehension
for speed (Rayner et al. 2016; docs/research/conversation-design.md), so
"miniaturised animation to skim faster" risks the opposite of catching up.
Two better shapes, both already half-built:
- **Skim replay:** the app's "Replay scene" at 4x, where plain lines appear
  at once and only the *shots* perform in full. Restraint makes this work:
  if shots are rare, they *are* the highlights.
- **"Previously on Half Ahead":** a trailer-style recap of what you
  missed, the director choosing the key phrases as cards. It needs a
  summary, and sent messages are already shared with the room and its
  agents, so Scout can write it (the on-device rule covers drafts, not
  the conversation). Ben's call.

**3. Sound to calm, excite, intrigue.** Map it to styles and shots, all
synthesized on the device (no audio files), opt-in, never autoplayed,
quiet, and always redundant with something visible: Elegant soft pads
(calm), Caffeinated bright ticks (excite), Drunken detuned wobble
(intrigue), Coarse thuds, and a card gets a trailer "hit". Jon's campfire
crackle is the spirit. *Factory next: lab sound.*

**4. Jon's scenes as the stage.** Needs Jon's code and his permission
(this repo is public). The factory can prepare the seam: a *stage*
module behind the glyph renderer with a tiny interface (draw at time t,
react to events such as "card" or "quake"), and today's grid floor as the
first stage. Jon's storm could then flash lightning on a quake without
either side knowing the other's internals. *Factory next: the stage seam.*

**5. The AI's toolbox should be far larger than a human's.** Agree, and
the shape is already there: humans get automatic cues (glossary, rules,
inferred style); agents choose explicitly from the full vocabulary, per
phrase: shot, style, emphasis, timing, within bounds. Concretely,
`:ha/engine-reply` gains optional direction fields, Scout's prompt learns
the vocabulary, and the app performs agent messages on the stage before
they land in the readable transcript. This is the step that brings the lab
into the app; it deserves Ben's yes first.

**6. Multitudes of agents on screen.** Each agent a small presence on the
stage (the Bit glyph, its shape its state), many of them moving as a
flock that parts when someone speaks; messages arrive from the speaker's
position. A flock is a pure simulation, so it can be tested for
determinism and frame cost (50 agents at 60 fps on the M5). After 5.

## Order for tonight

Decision-free, so the factory can run ahead: **3 (sound), 1 (reveal
levels), 4 (stage seam)**, in the lab. Waiting on Ben: 2 (recap via
Scout?), 5 (bring the lab into the app), 6 (after 5).
