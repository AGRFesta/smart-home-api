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

## Identity & attribution

Both roles post under the same account, so `user.login` cannot distinguish them.
**Markers in the comment body are the single source of truth for role.**
Threading (order, reply chains) is recovered from the GitHub API
(`in_reply_to_id` + `created_at`); markers only answer **who** and **what state**.

A separate bot/machine GitHub identity was rejected as too invasive. The cost of
this choice is **marker discipline**: every automated post must carry the correct
marker. To guarantee this, markers are injected **deterministically by the
posting script** (`post_review.py`), never hand-written by the model.

### Read-side classification

| `body` contains   | Meaning                  | Action                               |
| ----------------- | ------------------------ | ------------------------------------ |
| `role=reviewer`   | reviewer comment         | author must evaluate / respond       |
| `role=author`     | author's prior reply     | context, not an action               |
| **no marker**     | genuine human comment    | surface as human input               |

Unmarked = real human; this preserves human comments alongside the loop.

## Marker Schema

Markers are **HTML comments** (`<!-- ... -->`): invisible in rendered markdown,
present in the raw `body` returned by the API. A visible header (`🤖 **Reviewer**`)
is added for human readability. Format: `<!-- smarthome:key=value key=value -->`.

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

| Skill                              | Role     | Operation                                            |
| ---------------------------------- | -------- | ---------------------------------------------------- |
| `.claude/skills/pr-post-review`    | Reviewer | Reads context + diff, posts inline comments (gated)  |
| `.claude/skills/pr-analyze-comments` | Author | Reads reviewer comments, proposes accept/reject      |

Both are **human-gated**: every post requires explicit confirmation, and the
reviewer's posting script runs a no-write `preview` mode before any `post`.

## Scope boundary with ArchUnit

Hard, mechanizable architectural boundaries are enforced **deterministically at
build time** (`ArchUnit`, property-based tests). The LLM review covers the
semantic / design layer those cannot catch (naming, modeling choices, "respects
the letter but betrays the intent", missing changelog / API-doc updates, TDD
violations). The review **does not duplicate** what ArchUnit already guarantees.

## Model

Recommended reviewer model: **Opus 4.8 at `xhigh` effort**. Project-context
grounding matters more than model choice. The review favors **coverage** —
report every finding with `severity`/`confidence` as metadata; the human is the
filter. Do not self-filter to "only high severity".
