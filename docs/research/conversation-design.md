# Conversation design: inspirations, criticism, and what we take

Research for Half Ahead's conversation interface, 2026-10-21. The founder's
seeds: the kinetic type and audio of *You Don't Know Jack*, end-credit
sequences worth sitting through, a "visually rich without distracting"
J.A.R.V.I.S., a sidekick like TRON's, academic and art criticism, and
CMU's interaction research.

**How far to trust this page.** Done in one session, inline, mostly from
search results; several primary sources (CMU's pages, the Jack Principles
PDF) could not be fetched from the build environment. Each claim is
marked: **[V]** checked against a primary or institutional source,
**[S]** from search results or secondary summaries, **[M]** from memory,
not checked this session.

## What the sources say

**You Don't Know Jack and the Jack Principles.** Jellyvision's 1995 game
was "mostly text with clever typography" and leaned on audio to set the
scene, with no 3D sets or avatars [S]. Harry Gottlieb generalized what
made its host feel present into *The Jack Principles of the Interactive
Conversation Interface*, which secondary sources summarize as three
principles: **maintain pacing, create the illusion of awareness, maintain
the illusion** [S]. Jellyvision later applied them to benefits software
(ALEX, 2009) [S]. The "RISD" connection in the brief could not be
confirmed from any source found [—]; worth asking someone who was there.

**Kinetic typography at CMU.** Johnny Lee, Jodi Forlizzi and Scott Hudson,
"The Kinetic Typography Engine: an extensible system for animating
expressive text", UIST 2002, pp. 81–90, from CMU's Human-Computer
Interaction Institute [S, bibliographic details consistent across the UIST
archive]. The same group's follow-up, Lee, Jun, Forlizzi and Hudson,
"Using kinetic typography to convey emotion in text-based interpersonal
communication", DIS 2006, pp. 41–49 [S] is the closest academic ancestor
of what Half Ahead is doing: expressive motion in *conversation*, not in
titles. Forlizzi's Kinedit gave users control of size, font and placement
[S]. A 2014 study found predefined kinetic effects evoked the intended
emotions, with energy carried by size, transparency, direction, amount of
movement, velocity and acceleration [S]. **This is the "CMU interaction
lab" to integrate:** the HCII kinetic-typography line, not a lab of that
name.

**End credits people stay for.** Critics describe "coda credits" that
"grab on to the final emotional note and let it ride out"; others use the
time for outtakes and gags [S]. Legibility of names is the craft's
constant constraint [S]. No audience-behaviour research was found on *why*
people stay [—].

**J.A.R.V.I.S., TRON and science-fiction interfaces.** Shedroff and
Noessel's *Make It So* (2012) and scifiinterfaces.com study film
interfaces for real lessons; Noessel warns that some lovely film designs,
"if implemented", would be catastrophic, and that the failures are often
the richest to study [S]. No source specific to JARVIS or TRON was found
this session [—]. TRON's (1982) sidekick **Bit** can only say "yes" and
"no", yet reads as emotional; its three states are three polyhedra (yes:
octahedron; no: a stellated icosahedron; neutral: a compound form) [S].

**Reading in time.** Rayner, Schotter, Masson, Potter and Treiman, "So
much to read, so little time", *Psychological Science in the Public
Interest* 17 (2016): there is a speed–comprehension trade-off; RSVP
(one word at a time, as in Spritz) removes the reader's ability to look
back, and losing regressions tends to hurt comprehension [S, abstract and
APS summaries].

**Motion and accessibility.** WCAG 2.2.2 Pause, Stop, Hide (Level A):
moving content that starts automatically, lasts more than five seconds,
and is shown alongside other content needs a way to pause, stop or hide
it; auto-updating content has no five-second exception [V, W3C
Understanding pages via search]. WCAG 2.3.3 (AAA): motion triggered by
interaction can be disabled; CSS `prefers-reduced-motion` is a sufficient
technique for 2.3.3 but not, by itself, for 2.2.2 [S].

**Agents as characters, and the critique.** Brenda Laurel's *Computers as
Theatre* (1991) treats the interface as drama, with human and computer
agents as characters [S]. Sherry Turkle's *Artificial Intimacy* (due
2026-09-29) argues that chatbots offering themselves as friends teach
people that "pretend empathy is empathy enough", and she calls being told
you are wonderful by a machine a "corporate endgame" [S]. Clippy (Office
Assistant) is the canonical interruptive agent [M].

## What we take (principles)

1. **Pacing: never dead air.** The agent's presence shows the moment it
   starts working, and replies arrive with rhythm, not lag. (Jack:
   pacing.) *Built:* the presence line.
2. **Awareness, not personhood.** Be specifically responsive to what was
   said (Jack: awareness), and never pretend to be a person (Turkle). The
   agent is always labeled as an agent and as whose. *Built.*
3. **Motion carries tone, the text carries meaning.** Deliveries are a
   small vocabulary of tone (CMU's kinetic-typography results); the text
   is never altered. *Built:* the "final frame equals the text" property.
4. **Read in place, in order, with the past visible.** No word-at-a-time
   presentation; segments appear where they will stay; history arrives
   settled and can be re-read and replayed. (Rayner et al.) *Built.*
5. **Five seconds, then stillness.** Every performance ends within five
   seconds (WCAG 2.2.2), can be skipped, and is absent under reduced
   motion. *Built:* the cap (H14); *to build:* a keyboard-reachable skip.
6. **A sidekick's repertoire is small and legible.** Bit's three shapes
   are the model for Scout's presence: a glyph with a few states
   (idle, thinking, yes, no) rather than a face or a voice.
7. **Coda, not credits spam.** A conversation can end with a short credit
   roll: who took part (humans and their agents), the sources the agent
   cited, thanks. The final note rides out; nobody has to sit through it.
8. **Steal the presence, not the HUD.** JARVIS's lesson is a voice that is
   there when needed; its translucent overlays are exactly what
   *Make It So* warns about. Visual richness lives in type and timing,
   not chrome.
9. **Sound is a garnish and always opt-in.** YDKJ used audio to *set the
   scene* without 3D sets; we take that economy, never autoplay, and keep
   every cue redundant with something visible.

## Where the critics would push back on the brief

- **YDKJ was built for quips, not considered points of view.** Its type
  shouts because a quiz question is short. Half Ahead values "considered
  points of view"; most messages should be delivered plainly, and the
  engine prompt already says "prefer plain". Flair is the exception that
  marks a moment.
- **J.A.R.V.I.S. is a butler.** An omniscient, deferential servant is the
  opposite of "celebrate and challenge one another". The brief's other
  instinct, a TRON-style sidekick, fits the values better.
- **Replay and permanence cut both ways.** Some conversations flourish
  because they are not kept (Latent Space's paper club is deliberately
  unrecorded; see communities.md). An "off the record" mode may matter
  more than replay for a support group.
- **A charming agent is the engagement trap with better manners.**
  Turkle's critique applies to Scout as much as to any companion app. The
  persona's "never keep anyone talking longer than is good for them" has
  to be backed by product choices (no streaks, no nudges), not just words.

## Three directions for the look

1. **Game-show card (YDKJ).** Black stage, one big face of type, emphasis
   words slamming in, a short sting on arrival (opt-in). Most fun, most
   distinctive, and the most at risk of shouting; works only if plain
   delivery stays the norm.
2. **Title sequence (Saul Bass to coda credits).** Paper and ink, cut-paper
   shapes, generous margins; messages set like title cards; conversations
   close with a credit roll. Most "considered", the strongest fit with
   correctness-with-flair; risk of preciousness.
3. **Tron grid, honestly lit.** Dark field, thin luminous rules, Scout as a
   Bit-like polyhedron whose shape is its state; motion as light rather
   than movement. Distinctive and calm; risk of sci-fi cliché and low
   contrast.

**Recommendation:** start from 2 with one element of 1 (the `:headline`
slam is the game-show card, used rarely) and the Bit glyph from 3 for
Scout's presence. It honours all three seeds, keeps reading first, and
fits the values. The founder decides; see DECISIONS.md.

## Sources

[Jellyvision (Wikipedia)](https://en.wikipedia.org/wiki/Jellyvision) ·
[Karl Kapp on the interactive conversation interface](https://karlkapp.com/in-games-and-learning-interfaces-strive-for-an-interactive-conversation-interface/) ·
[You Don't Know Jack (1995) (Wikipedia)](https://en.wikipedia.org/wiki/You_Don%27t_Know_Jack_(1995_video_game)) ·
[CGM review of You Don't Know Jack](https://www.cgmagonline.com/review/you-dont-know-jack/) ·
[CMU: What is Kinetic Typography](https://www.cs.cmu.edu/~johnny/kt) ·
[UIST archive: kinetic](https://uist.acm.org/archive/html/keywords/kwkinetic.html) ·
[DIS 2006 paper record](https://cir.nii.ac.jp/crid/1363670319143139712) ·
[CHI 2004 paper citing Kinedit and Bodine & Pignol](https://www-ui.is.s.u-tokyo.ac.jp/~takeo/papers/chi2004.pdf) ·
[2014 study of predefined kinetic effects](https://koreascience.kr/article/JAKO201409864556113.do) ·
[Kottke: the best ending movie credits](https://kottke.org/17/01/the-best-ending-movie-credits-of-all-time) ·
[GamesRadar: best credit sequences](https://www.gamesradar.com/best-movie-credits) ·
[Rosenfeld Media: Noessel on sci-fi interface failures](https://rosenfeldmedia.com/chris-noessel-on-learning-from-science-fiction-interface-failures/) ·
[Noessel, dConstruct 2015](https://archive.dconstruct.org/2015/catastrophic) ·
[Bit (TRON wiki)](https://tron.fandom.com/wiki/Bit) ·
[Wolfram: the legacy of TRON](https://blog.wolfram.com/2010/12/14/the-legacy-of-tron/) ·
[Rayner et al. 2016 record](https://profiles.wustl.edu/en/publications/so-much-to-read-so-little-time-how-do-we-read-and-can-speed-readi/) ·
[APS: speed reading too good to be true](https://www.psychologicalscience.org/observer/speed-reading-promises-are-too-good-to-be-true-scientists-find-2) ·
[W3C Understanding 2.2.2](https://www.w3.org/WAI/WCAG20/Understanding/pause-stop-hide.html) ·
[W3C Understanding 2.3.3](https://w3c.github.io/wcag/understanding/animation-from-interactions.html) ·
[Hidde de Vries on 2.2.2 and reduced motion](https://hidde.blog/meeting-2-22-pause-stop-hide-with-prefers-reduced-motion/) ·
[Laurel, Computers as Theatre (Goodreads)](https://www.goodreads.com/book/show/239018) ·
[Turkle, Artificial Intimacy (MIT Press Bookstore)](https://mitpressbookstore.mit.edu/book/9780316612739) ·
[PBS: Can AI companionship cure loneliness?](https://video.iowapbs.org/video/can-ai-companionship-cure-loneliness-or-deepen-it-qznu4n/)
