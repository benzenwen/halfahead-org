# Schema

The human-readable mirror of `src/halfahead/schema.cljc`. The two must
agree; the code wins a disagreement, and the disagreement is a bug.

## Firestore layout

```
members/{memberId}                                  :ha/member
conversations/{conversationId}                      :ha/conversation
conversations/{conversationId}/messages/{messageId} :ha/message
conversations/{conversationId}/presence/{memberId}  :ha/presence
```

Field names are camelCase on the wire and kebab-case keywords in Clojure;
only the codec converts. Timestamps are Firestore timestamps on the wire
and insts in Clojure.

## Entities

**Member** — a human or an agent. An agent always has an `owner-id`, the
human it belongs to; a human never does. That is how the interface can
always say *whose* agent is speaking.

| key | type | notes |
|---|---|---|
| `id` | id | Firebase uid for humans; chosen id for agents |
| `kind` | `:human` \| `:agent` | |
| `display-name` | 1–80 chars | |
| `owner-id` | id | agents only, required |
| `joined-at` | timestamp | |

**Conversation** — a titled room with 1–32 members.

| key | type |
|---|---|
| `id`, `created-by` | id |
| `title` | 1–120 chars |
| `member-ids` | vector of ids |
| `created-at` | timestamp |

**Message** — what was said, and how it should be delivered.

| key | type | notes |
|---|---|---|
| `id`, `conversation-id`, `author-id` | id | |
| `text` | 1–4000 chars, not blank | the content; flair never changes it |
| `delivery` | `:plain` `:typed` `:headline` `:aside` `:credits` | closed vocabulary |
| `emphasis` | up to 8 spans `[from to)` | characters `:headline` may stress |
| `sent-at` | timestamp | server time |
| `in-reply-to` | id | optional |

**Presence** — whether a member is working on a reply, so nobody waits
in silence. `state` is `:idle` or `:thinking`, with `since`.

## Derived shapes (never stored)

**Timeline** — what `halfahead.perform` makes from a message:
`{:duration-ms n :beats [{:span [from to] :effect e :at-ms t :ms d} …]}`.

- `duration-ms` is at most 8000: no flourish holds a reader longer.
- Effects: `:appear` `:type` `:slam` `:whisper` `:rise`.
- Invariants (tested, not expressible in the schema): the beats' spans
  partition the text exactly, and every beat ends by `duration-ms`. So the
  final frame always shows exactly the message text.

## Worker contract

The worker sends an engine an `:ha/engine-request` (`persona` and
`history`, a vector of `{:role :human|:agent :text}` turns) and expects an
`:ha/engine-reply`: `text`, `delivery`, `emphasis` as *phrases* (the worker
resolves them to spans; a phrase not found in the text is dropped), and an
optional `cost-usd`.
