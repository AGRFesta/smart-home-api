# PR Review Workflow

This document describes the **process** by which Pull Requests are reviewed by a
Claude Code reviewer session and answered by the author session. It is a process
reference, **not** an architectural rule: the reviewer skill must not turn this
file into diff-checklist items.

> **Single source of truth for the marker schema.** The marker definitions in
> §Marker Schema below are authoritative. Both skills
> (`.claude/skills/pr-post-review`, `.claude/skills/pr-analyze-comments`)
> reference this section — do not redefine markers elsewhere.

## Roles

- **Author (A)** — implements an issue, opens the PR, later reads reviewer
  comments and either fixes them or rejects them with a justification.
- **Reviewer (B)** — an **independent** session (it did not write the code) that
  reviews the PR against project context and posts inline comments.

Both roles post via the **same GitHub account**. There is no separate bot
identity (a deliberate choice — see §Identity). The human approves every read
and every post: this is **not** an autonomous loop.

## Core principle: the PR is the shared state

No session keeps state across turns. The Pull Request on GitHub is the durable
shared state (current diff, reviewer comments, author replies, new commits).
Every review pass is a **fresh** reviewer-role session that reconstructs
everything by reading the PR. There is no long-lived session and no context
drift; "a new session or the same one?" is therefore a non-question — every
review pass is a new session in the reviewer role.

## Workflow

```
A (implements issue) ──► opens PR
        │
        ▼
B1 (fresh): current diff + CLAUDE.md/docs ──► inline comments   [HUMAN GATE]
        │   records reviewed SHA (in the summary anchor)
        ▼
A (continues): reads comments ──► fix OR reject-with-justification
             ──► push + reply on the PR                          [HUMAN GATE]
        │
        ▼
B2 (fresh): open threads + delta(new SHA vs recorded SHA)
        ├─ verify fixes ──► resolve / reopen
        ├─ evaluate rejections ──► concede / escalate to human
        └─ new issues in the delta ──► new comments              [HUMAN GATE]
        │
        ▼
   ... until every thread is resolved or escalated to the human
```

### Incremental re-review

The reviewer's memory is anchored on the PR, not in the session. Each review
pass records the **reviewed commit SHA** in the summary anchor comment. The next
pass reviews only:

1. **open threads** (its prior comments + the author's replies), and
2. the **delta** `git diff <last-reviewed-SHA>..HEAD`.

It does **not** re-review the whole diff from scratch (this is the defect we are
replacing). New comments are produced only for genuinely new issues in the delta.

### Per-thread state machine

For each open thread, the re-review pass classifies the author's response:

| Author action                  | Reviewer response                                                            |
| ------------------------------ | ---------------------------------------------------------------------------- |
| Accepted + fixed               | Verify the fix actually resolves it → resolve, or reopen if incomplete/wrong |
| Rejected with justification    | Evaluate it → concede & resolve if reasonable; otherwise **one** counter-reply, then escalate to the human |
| New code introduced by the fix | May open **new** threads, but only for genuinely new issues in the delta     |

### Convergence rule

The human gate bounds loops (nothing is sent without approval), and the
reasoning must also converge:

- **Bias to converge** — concede threads where the justification is plausible;
  hold firm only on real problems.
- **No re-arguing** — on a persistent disagreement after one round-trip, do not
  insist; escalate to the human with a summary of both positions.

### Thread closure authority

The **reviewer** marks a thread `resolved` after verifying the fix (the author
replies "fixed in `<sha>`" but leaves it open). The reviewer is the authority on
closure; the human is the final arbiter.

## Finding routing

Every finding is reported. What a finding costs depends on **where it lands**,
and the expensive unit is not the finding — it is the **round**. An inline
comment is a thread, and a thread is three posts (the comment, the author's
reply, the reviewer's verdict) spread across a further pass over the PR. One
finding and twenty findings in the same round cost nearly the same; one finding
that opens a *second* round costs a whole cycle.

So a finding lands either as a **blocking** inline comment — a thread the author
must answer — or as a **non-blocking** bullet in the summary comment:

| Round | Finding                                  | Lands as       |
| ----- | ---------------------------------------- | -------------- |
| 1     | `medium` and above, any file             | inline         |
| 1     | below `medium`, source file              | inline         |
| 1     | below `medium`, documentation file       | non-blocking   |
| 2+    | `medium` and above, any file             | inline         |
| 2+    | below `medium`, any file                 | non-blocking   |

`review_lib.py` applies the two parts of this a script can decide — the file
extension and the round — so a misrouted finding cannot reach the PR. Whether a
finding *inside a source file* is about prose rather than behaviour is a
judgement, and it is made where the reviewer assigns the severity.

### `medium` on a documentation finding is a definition, not a judgement

A floor on a severity the reviewer assigns to its own findings is an invitation
to promote everything to `medium`, at which point the field stops carrying
information and the floor stops existing. The floor therefore rests on a
definition that can be checked against the code rather than argued:

> A documentation finding is `medium` or above **only** when the document states
> something **false about the code, and checkable against it**. Style, wording,
> duplicated rationale, which file owns a section, a deferral whose reason is not
> given: all below `medium`, none promotable.

This is the same line that separates the two kinds of documentation finding by
value. A document that misstates a status code or a parsing rule sends every
reader somewhere wrong, and the reviewer finds it by running the code against the
claim. A paragraph that argues its point twice costs a reader nothing.

### Non-blocking findings

They go in the summary comment, under one heading, with no thread. The author
acts on them in the current commit or does not; there is no reply to write and no
verdict to record. **A later round may not raise a non-blocking finding again**,
whether or not it was acted on — a finding that comes back as a thread was never
non-blocking.

### Why a re-review round has a floor at all

Because a prose fix is not like a code fix. A corrected bug stays corrected and
adds almost no surface; **a corrected paragraph is new prose, and new prose is new
reviewable surface.** The loop closes on itself: a comment rewritten to satisfy a
finding is text nobody has reviewed yet, and reviewing it produces the next
finding. Documentation review, unconstrained, has no fixed point — the floor is
what gives it one.

The `is it still true` instruction below is what makes the surface unbounded in
the first place, and it stays: it is also what catches a document that the diff
under review has just falsified. The floor scopes it to factual claims about the
code, which is the only part of it that pays.

## Identity & attribution

Both roles post under the same account, so `user.login` cannot distinguish them.
**Markers in the comment body are the single source of truth for role.**
Threading (order, reply chains) is recovered from the GitHub API
(`in_reply_to_id` + `created_at`); markers only record **who** and **what state**.

A separate bot/machine GitHub identity was rejected as too invasive. The cost of
this choice is **marker discipline**: every automated post must carry the correct
marker. To guarantee this, markers are injected **deterministically by the review
tooling**, on both sides of the loop, never hand-written by the model. The schema
has a single definition — `.claude/lib/markers.py`, which renders and parses it —
and it sits outside both skill directories so that neither owns it and neither
imports the other.

❗️ **The read side cannot tell a misspelt role from an absent one.** Both fall
through to `human` in the table below, which is why the write side rejects an
unknown role instead of rendering it: by the time a wrong marker reaches GitHub,
nothing downstream will ever question it.

### Read-side classification

| `body` contains   | Meaning                  | Action                               |
| ----------------- | ------------------------ | ------------------------------------ |
| `role=reviewer`   | reviewer comment         | author must evaluate / respond       |
| `role=author`     | author's prior reply     | context, not an action               |
| **no marker**     | genuine human comment    | surface as human input               |

Unmarked = real human; this preserves human comments alongside the loop.

## Marker Schema

Markers are **HTML comments** (`<!-- ... -->`): invisible in rendered markdown,
present in the raw `body` returned by the API. A visible header (`🤖 **Reviewer**`
or `🤖 **Author**`) is added for human readability. Format:
`<!-- smarthome:key=value key=value -->`.

| Marker key      | On                                   | Values                          | Purpose                                   |
| --------------- | ------------------------------------ | ------------------------------- | ----------------------------------------- |
| `role`          | every automated comment (mandatory)  | `reviewer` \| `author`          | Role attribution (replaces `user.login`)  |
| `reviewed-sha`  | reviewer summary anchor              | a commit SHA                    | Delta anchor for incremental re-review    |
| `round`         | reviewer comments & summary          | integer                         | Which review round produced the comment   |
| `decision`      | author reply                         | `accepted` \| `rejected`        | Machine-readable author decision          |
| `verdict`       | reviewer reply (thread closure)      | `resolved` \| `open` \| `escalated` | Thread lifecycle state                |

Examples:

```
<!-- smarthome:role=reviewer round=1 -->
<!-- smarthome:role=author decision=rejected -->
<!-- smarthome:role=reviewer reviewed-sha=abc1234 round=2 -->
<!-- smarthome:role=reviewer verdict=resolved -->
```

The `reviewed-sha` anchor lives on a single PR-level summary comment per round,
which is where the reader (`get_pr_context.py`) looks for it.

## Tooling

| Skill                              | Role     | Operation                                                        |
| ---------------------------------- | -------- | ---------------------------------------------------------------- |
| `.claude/skills/pr-post-review`    | Reviewer | Reads context + diff, posts inline comments and the summary (gated) |
| `.claude/skills/pr-analyze-comments` | Author | Reads reviewer comments, decides accept/reject, posts the replies (gated) |

Both are **human-gated**, and on both sides the gate is the **preview step**: the
session renders what it would send with `preview_review.py` / `preview_replies.py`
(read-only — GET requests only, nothing leaves the machine), presents the rendered
plan, and **stops** until the human approves. Only then does `post_review.py` /
`post_replies.py` write to the PR.

The two sides differ in what a re-run does, because their idempotency rules differ.
The reviewer dedups on content — same finding, same place, same text — since a
re-review may legitimately say something new about a line it has already commented
on. The author dedups on **thread state**: a thread that already carries an author
reply after the last reviewer message is skipped whatever the body says, because a
reply re-drafted between two runs is still an answer to a question already
answered. So re-running the same input after a partial failure sends exactly what
is missing.

The scripts are allow-listed, so posting raises no second permission prompt. This is
a deliberate reversal of an earlier design in which `post_review.py` was withheld
from the allow-list to force one. Two reasons it did not work:

- **A prompt after the decision is not a gate, it is friction.** By the time it
  appears, the human has already read the preview and said yes. It asks the same
  question twice and gets the same answer.
- **It is one config edit away from being a wall.** A `deny` rule — the natural next
  step when "make sure it always asks" is the goal — blocks unconditionally: no
  prompt is shown, so there is nothing to approve, and the session cannot finish its
  own workflow. The reviewer drafts a review it is structurally unable to publish.

The script split still earns its place, for a different reason: preview and post are
separate entry points, so "show me what you would send" **cannot** accidentally send.
That property comes from the split itself, not from the permission layer.

❗️ **The consequence is explicit:** the only thing between a drafted post and a
published one is the session honouring the stop. That is an acceptable trade for a
local, single-author repository where the human reads every preview. It stops being
acceptable the moment the PR has an outside audience — a shared repository, a PR
with external contributors. Restore the mechanical backstop then by dropping the two
post scripts from the allow-list (which makes them prompt), **not** by adding a
`deny` rule (which makes them impossible).

❗️ **Each skill's `allowed-tools` names its own scripts, one by one.** A wildcard
such as `Bash(uv run *)` reads as "this skill runs its own tooling" and is not: it
lets either session run the *other* role's writer, which is how an author session
acquires the ability to post a `verdict` and close its own thread. The enumeration
is the only thing keeping the two roles' write authority apart.

## Scope boundary with ArchUnit

Hard, mechanizable architectural boundaries are enforced **deterministically at
build time** (`ArchUnit`, property-based tests). The LLM review covers the
semantic / design layer those cannot catch (naming, modeling choices, "respects
the letter but betrays the intent", missing changelog / API-doc / Bruno updates,
TDD violations). The review **does not duplicate** what ArchUnit already
guarantees.

❗️ **Re-read the document that owns the area, not only the diff.** `CLAUDE.md`
sends the argument behind a decision into `docs/` and keeps the comment at the
declaration to a sentence. That buys review surface at the price of a copy
nothing compiles: a paragraph in `ARCHITECTURE.md` or `docs/domain/` can be
silently falsified by the very diff under review, and no test, no ArchUnit rule
and no build will say so. So the question on the owning section is not "was it
updated" but **"is it still true"** — the doc that was right when it was written
and is wrong now is the failure mode this convention creates, and this is the
only thing that catches it.

## Model

Recommended reviewer model: **Opus 4.8 at `xhigh` effort**. Project-context
grounding matters more than model choice. The review favors **coverage** —
report every finding with `severity`/`confidence` as metadata; the human is the
filter. Do not self-filter to "only high severity": what a finding costs is
decided by the routing above, and the routing is applied by the tooling, after
the reviewer has written the finding down. Withholding one saves nothing and
loses the record.

## Known limitations

These are **consequences of deliberate design choices**, not bugs. They are
documented so that surprising behaviour is recognised as a known trade-off rather
than mistaken for a defect.

### Markers are trusted, not authenticated

Role attribution relies entirely on a plaintext HTML-comment marker in the body
(`<!-- smarthome:role=... -->`). It is a **label, not a signature**: anyone can type
it, and the read side trusts it blindly.

- *Failure mode:* a genuine human comment that happens to contain
  `<!-- smarthome:role=author decision=accepted -->` is reclassified as an author
  reply ("context, not an action") and silently dropped from the human-input list.
  In the worst case, marker-shaped text inside the diff is a prompt-injection vector.
- *When it bites:* only if the PR is opened to people or content that are not
  trusted. Safe under the current local, single-author assumption.
- *What to do:* do not extend this scheme to a multi-party PR without adding a real
  identity/integrity mechanism first.

### Delta-only re-review does not re-catch round-1 misses

From round 2 on, the reviewer looks only at open threads + `git diff <anchor>..HEAD`.
A defect already present in round 1, in code nobody touches again, is **never**
re-examined — final quality leans heavily on the first pass.

- *Failure mode:* a bug in an alert rule that opens a duplicate alert for the same
  device is missed in round 1, and — since the fix commits never touch
  that code — stays invisible to every later round. A secondary effect: because
  `resolved` threads are excluded from "needs author action", the reviewer may
  re-raise an already-conceded point as a "new" finding in the delta.
- *When it bites:* long multi-round reviews where the real issue predates round 2.
- *What to do:* when a full re-scan is genuinely needed, force a fresh round 1
  (no anchor) rather than relying on the incremental path.

### A minor finding on a re-review round never becomes a thread

From round 2 on, everything below `medium` is non-blocking (§ Finding routing).
The author sees it and may act on it, but nothing requires an answer and no round
is opened for it.

- *Failure mode:* a fix for a round-1 finding introduces a small real defect — a
  test arm that drops an assertion its siblings all make, say — the reviewer rates
  it `low`, and it merges unaddressed. Nothing else catches it: it is exactly the
  class of defect that has no ArchUnit rule and no failing build.
- *When it bites:* whenever a round-1 fix is large enough to carry its own
  mistakes. The exposure is bounded by severity, not by kind — a `medium` defect
  in the same fix still opens the round.
- *What to do:* rate on the defect, not on the size of the diff that carries it.
  A finding that would matter after merge is not `low` because it is small.

### Re-review diff is base-unaware (two-dot vs three-dot)

First review scopes with `git diff <baseRefOid>...HEAD` (three-dot, so it shows only
what the PR added on top of its base). ❗️ The base is pinned by **OID**, read from
`gh pr view` at run time — never by `baseRefName`. Reading the *name* at run time
looks like it solves the problem and does not: a name resolves through the local
clone, and a local `main` that is a few merges behind yields a diff containing
commits that are already on the base, reviewed as if the PR had authored them. The
reviewer skill (§2) pins the OID and then cross-checks its file list against
`gh pr diff --name-only`, which is computed server-side.
Re-review switches to `git diff <anchor>..HEAD` (two-dot), which **knows nothing
about the base**.

- *Failure mode:* while a PR based on `main` is under review, the author runs
  `git merge main` to sync. The next two-dot re-review then includes everything that
  arrived from `main` — code the PR never authored — and the reviewer comments on it.
  This is a **scope-precision** problem, not a correctness one (no PR findings are
  lost).
- *When it bites:* after merging the base branch into the PR branch mid-review.
- *What to do:* prefer **rebase over merge** to sync with the base, so no merge
  commit inflates the two-dot delta.
