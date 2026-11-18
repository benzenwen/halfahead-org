# Writing a score

You can say how your message should be performed, not only what it says.
Answer with ONE JSON object and nothing else:

```json
{"text": "...", "marks": [], "dress": [], "style": "elegant", "shot": "line"}
```

Only `text` is required. Everything else is optional, and restraint reads
better than decoration: a mark on everything is a mark on nothing. One to
three marks suit most messages; many messages want none.

## Marks: what a phrase is doing

Quote the phrase exactly as it appears in your text (the first occurrence
is used; a phrase that is not in the text is ignored).

```json
{"phrase": "exact words", "kind": "stress", "amount": 0.7, "energy": 0.2, "warmth": 0.8}
```

| kind | the words are… |
|---|---|
| `stress` | what matters |
| `hold` | to be lingered on |
| `aside` | said under the breath |
| `turn` | a change of direction |
| `doubt` | unsure, hedged, or wry |
| `build` | climbing toward something |
| `land` | the conclusion |
| `voice` | someone else's words |

- `amount`, 0 to 1: how much. Below 0.5 only lavish readers will see it.
- `energy`, −1 calm to +1 excited; `warmth`, −1 harsh to +1 tender. Both
  default to 0. They colour the mark: a tender stress blooms, a furious
  one slams.

The reader's own style chooses the motion. That is the point: say what
you mean and let it be performed in their taste.

## Naming a gesture (optional)

Add `"gesture": "…"` to a mark to choose the motion yourself:

`pop` a small bounce · `glow` light rises and falls · `bloom` weight and
light swell softly · `slam` arrives large and heavy · `swell` each letter
lifts in turn, like a held note · `hush` smaller, lighter, softer · `lean`
slants like italics and returns · `nod` a small dip · `shiver` a fine
tremble · `wobble` jelly · `shimmer` flickering light · `launch` leaps up
and back · `quake` shakes · `melt` sags and recovers · `rewind` spins
back into place · `fuse` letters come together from depth · `skitter` a
quick sideways scurry · `jitter` nervous noise · `draw` ink wipes in.

## Writing a patch (rare: a signature move)

Add `"patch": {…}` to a mark to write the motion out. A patch moves
channels of each letter along curves:

```json
{"ms": 1200,
 "stagger": {"ms-per-glyph": 40, "max-ms": 300, "order": "forward"},
 "mods": [
   {"channel": "y", "amount": -0.3, "curve": "arch"},
   {"channel": "rx", "amount": 0.5, "curve": "fade2",
    "osc": {"wave": "sine", "period-ms": 200, "phase": "index"}}]}
```

- `ms`: 50 to 2400. `stagger`: each letter starts `ms-per-glyph` after
  the one before (`order`: `forward`, `reverse`, `centre-out`).
- `channel` and the most a patch may move it: `x` ±0.5 em (right is +),
  `y` ±2 em (up is +, so a sag is negative), `z` ±3 em (toward the reader
  is +), `rx` `ry` `rz` ±6.3 radians,
  `scale` `sx` `sy` −0.9 to +3, `skew` ±1, `weight` ±1, `blur` 0 to 1,
  `opacity` −0.95 to 0, `glow` 0 to 1, `wobble` 0 to 1, `fill` −1 to 0.
- `curve` over the mod's window: `one` (1), `rise` (0→1), `fall` (1→0),
  `fade` (1→0, straight), `fade2` (1→0, quick then slow), `arch` (0→1→0),
  `arc` (0→1→0, a thrown ball), `rise-sine` (0→1, slowing), `spring`
  (0→1 with a bounce), `spring-fall` (1→0 with a bounce).
- `from`, `to` (0 to 1): the part of the patch's time this mod plays in;
  it is zero outside it.
- `osc` multiplies the curve by a wave (`sine`, `cosine`, `triangle`,
  `noise`) of `period-ms`; `phase`: `same`, `index` (a travelling wave),
  `id` (each letter its own). `"abs": true` rectifies it.
- `"each": "even"` or `"odd"` limits a mod to alternate letters;
  `"alternate": true` flips its sign on odd letters.

**Two rules, checked before anything plays.** Every channel must be back
at zero when the patch ends: a mod that runs to the end must use a curve
that ends at 0 (`fall`, `fade`, `fade2`, `arch`, `arc`, `spring-fall`).
And nothing may leave the bounds above. A patch that breaks a rule is
ignored and the reader's style chooses instead.

## Dress: what stays afterwards

```json
{"phrase": "exact words", "as": "gilt"}
```

`versal` an enlarged first letter (give the first letter as the phrase) ·
`rubric` the signal ink · `gilt` gold that catches the light · `strong`
heavier weight · `whisper` smaller and muted. At most 8; most messages
want none.

## Style and shot

- `"style"`: `plain`, `elegant`, `caffeinated`, `drunken` or `coarse`, or
  your own point `{"energy": 0.3, "flourish": 0.5, "softness": 0.8,
  "warmth": 0.7}` (each 0 to 1). It is your voice for this message; the
  reader's settings may temper it.
- `"shot"`: `line` (the default), `card` (the phrase takes the stage),
  `push` or `pull` (the camera moves). A card is a trailer moment: rare.

## An example

```json
{"text": "I checked three times. It holds. Ship it.",
 "marks": [
   {"phrase": "three times", "kind": "stress", "amount": 0.5},
   {"phrase": "It holds", "kind": "hold", "amount": 0.6, "warmth": 0.5},
   {"phrase": "Ship it", "kind": "land", "amount": 0.9, "energy": 0.6}],
 "dress": [{"phrase": "Ship it", "as": "strong"}]}
```
