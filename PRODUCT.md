# Half Ahead: product brief

## The brief, as given

> Half Ahead is a community for AI agentic engineers. It's part tradecraft,
> part support group. We are ahead of the rest of humanity but not at the
> very tip of the wave. We're mostly happy to be here. (README)

> Half Ahead values humanity and appreciates technology. We wrestle with the
> introduction of AI into our industries and societies. We marvel at these
> new capabilities. We experience ahead of others what changes AI could
> bring, and we have care for its impact on humanity. We are technically
> proficient. We are creative. We are human. We hold opinions of the future.
> We seek and support community to share views, techniques, news and skills.
> We celebrate and challenge one another humanely. We have a chance to make
> AI valuable and powerful in our societies. We have the luxury of being able
> to connect the technologies to humanity and to begin to fulfill our shared
> dreams. We have the opportunity to share our scouting. We are not the only
> community but this is ours.
>
> We value: humanity, considered points of view, correctness with narrative
> flair, mild irreverence to provoke new perspectives, openness, and an
> honoring of our lineage without being anchored in the past.
>
> Initially we are here in conversations that flourish better here than in
> the traditional messaging and social networks. We do this by emphasizing
> the graphical and temporal nature of writing and reading. At first this is
> an investigation of chat with just one human (me) and an agent. […] I like
> to use Google's Firebase for initial prototypes, of which this is one. But
> I am also open to expanding my horizons.
>
> I'll seed with some of the dynamic RISD-inspired textual graphics of the
> game You Don't Know Jack with audio flair. I also am thinking of the best
> movie credit rolls that make you want to sit through it all. Perhaps a
> visually rich without distracting conversational interface like with
> J.A.R.V.I.S. could be inspiration. A sidekick like in TRON? […]
>
> Eventually I want to invite other people and their agents here to see what
> we can share and create. […] I have a set of friends and acquaintances
> that are open and even hungry for something like this, I hope. I also want
> to be able to meet IRL.
>
> Finally I want this experimental community project to run in an earned
> dark loop. (kickoff, 2026-10-21; light copy edits, elisions marked)

## Who it is for

- **v0: one human and one agent.** The founder, talking with an agent that
  has a character. This is an investigation, not a launch.
- **Next: friends and acquaintances who build with agents**, and *their*
  agents. Tens of people, invite-only. They are practitioners, so they
  will notice a gimmick at once and forgive a rough edge that teaches
  something.
- **Then: in person.** Half Ahead meets in real rooms too, and the app
  should make meeting easier, not replace it.

## What it must do well

1. **Make conversation better than chat.** Writing and reading have shape
   and tempo here. A message can arrive the way a good line is delivered,
   and a conversation can be re-read the way you would re-watch a scene.
   Flair serves meaning; it never changes or hides what was said.
2. **Give the agent a character, not a costume.** It is a sidekick with a
   point of view and a limited, legible repertoire. You always know it is
   an agent, whose agent it is, and what it is doing.
3. **Be humane.** Celebrate and challenge, never perform for an algorithm.
   No engagement metrics, no infinite feed, no dark patterns.
4. **Be correct.** Considered points of view and correctness with narrative
   flair: claims carry their sources; the agent says when it does not know.
5. **Be accessible.** Motion and sound are opt-in or reducible. A reader
   who turns off all flair loses no content.
6. **Be a lab.** The founder is investigating; the architecture must stay
   open to experiments in how conversation looks and moves.

## v0: the smallest real thing

One human, one agent, one conversation, on local Firebase emulators.

- The human writes a message; it persists in Firestore.
- A worker beside the front end sees it, asks the agent engine for a reply,
  and writes the reply back. The agent chooses a **delivery** for each
  line from a small closed vocabulary.
- The front end **performs** each message: a pure function turns text plus
  delivery into a timeline of beats, and the view plays it. With reduced
  motion, the final text appears at once.
- The conversation can be **replayed** in time.

**Next, roughly in order:** sign-in with an invite gate · several humans,
each with their own agent · a "credits" view of a conversation · opt-in
sound · scouting notes (sourced finds shared with the community) ·
gatherings (IRL meetups) · deploy at **halfahead.org** (the founder owns
the domain).

## Non-goals (for now)

Public sign-up, feeds and algorithmic ranking, likes and follower counts,
voice and video, native apps, monetization, and any analytics beyond what
the factory needs to test itself.

## How it is built

By [Lupa](https://github.com/benzenwen/lupa) driving
[basefactory](https://github.com/benzenwen/basefactory): specs before code,
small orders, every change tested and reviewed, and a charter
(`lupa.charter.edn`) that says how much a human reviews. It starts fully
lit. It earns darkness only on evidence. See `DECISIONS.md`.
