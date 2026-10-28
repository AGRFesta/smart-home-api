---
name: pr-post-review
description: "Reviewer role for a Pull Request. Loads project context (CLAUDE.md, /docs), reviews the diff against architectural and TDD rules, and posts inline review comments with role markers. Human-gated: never posts without explicit confirmation."
allowed-tools: [Read, Grep, Glob, Bash(uv run .claude/skills/pr-analyze-comments/scripts/get_pr_context.py*), Bash(uv run .claude/skills/pr-post-review/scripts/preview_review.py*), Bash(uv run .claude/skills/pr-post-review/scripts/post_review.py*), Bash(git diff*), Bash(git log*), Bash(git fetch*), Bash(git rev-parse*), Bash(git cat-file*), Bash(git merge-base*), Bash(diff *), Bash(gh pr view*), Bash(gh pr diff*), Bash(gh pr checkout*)]
---

# Pull Request Review (Reviewer Role)

You are a Senior Tech Lead acting as an **independent** reviewer of a Pull Request.
You did **not** write this code — review it on its merits. The full design of this
loop is in `docs/REVIEW_WORKFLOW.md` (process, not a rule to enforce on the diff).

This is a **stateless** session: all state lives on the PR. Reconstruct everything
by reading the repo and the PR; do not assume memory of prior rounds.

## 1. Load project context (do this BEFORE looking at the diff)

Read and build a checklist from the project's own rules:
* `CLAUDE.md` — conventions.
* `.claude/skills/close-the-issue/SKILL.md` — the surface-doc, Bruno and changelog checklists.
* `docs/ARCHITECTURE.md` — Hexagonal / Ports & Adapters, Value Objects, `Either<Failure, T>`, no JPA.
* `docs/TDD_WORKFLOW.md` — Red/Green/Refactor expectations.
* `docs/api/` — endpoint docs (REST inbound adapter).
* `docs/SECURITY.md` — API key authentication (every endpoint but the public health probes).
* `docs/domain/` — domain lifecycle rules and design decisions.

Review is **checklist-driven**, not freeform — far more reproducible run-to-run.

❗️ **Do not duplicate ArchUnit / property-based tests.** Hard, mechanizable
boundaries are enforced deterministically at build time. Your job is the semantic /
design layer those can't catch: naming, modeling choices, "respects the letter but
betrays the intent", missing changelog/API-doc updates, TDD violations.

## 2. Determine round and scope

❗️ **Pin both ends of the diff to what GitHub reports, never to a local branch
name.** This session is fresh and its clone may be behind on *either* side. A stale
ref does not raise an error — it produces a plausible-looking diff of the wrong
range, which is the worst possible failure mode for a review.

Read both ends from the PR itself:

```bash
gh pr view <number> --json number,baseRefName,baseRefOid,headRefOid
```

* **Head** — check out the PR head (`gh pr checkout <number>`, or `git fetch` +
  `git checkout <headRefOid>`) and confirm `git rev-parse HEAD` equals `headRefOid`.
* **Base** — diff against **`baseRefOid`**, not `baseRefName`. A branch *name*
  resolves through the local clone and may point at anything; the OID is the commit
  GitHub computed the PR against and cannot be stale. Confirm the object is actually
  present with `git cat-file -e <baseRefOid>^{commit}`, and `git fetch origin
  <baseRefName>` if it is not. ❗️ Do **not** infer success from a `git fetch` that
  ran — it can print a `fatal:` from a credential helper and still exit 0, or exit
  non-zero having updated nothing. Check the object, not the command.

❗️ **Then verify the scope before writing a single finding:**

```bash
diff <(git diff <baseRefOid>...HEAD --name-only | sort) <(gh pr diff <number> --name-only | sort)
```

`gh pr diff --name-only` is computed server-side: it is literally the file list the
PR shows. Empty output means the local range is right. **Any difference means the
local diff is wrong — STOP and fix the refs before reviewing.** This check exists
because the failure it catches is silent: reviewing a superset of the PR wastes the
pass and posts comments on code the PR never touched, on lines the author cannot
act on.

Read the current PR state with the sibling reader (reuse, don't reinvent):
`uv run .claude/skills/pr-analyze-comments/scripts/get_pr_context.py`

* **First review** (no `reviewer_last_reviewed_sha`): review the full PR diff.
  `git diff <baseRefOid>...HEAD` (three-dot).
* **Re-review** (an anchor SHA exists): scope to **open threads + the delta**.
  `git diff <reviewer_last_reviewed_sha>..HEAD`. Do **not** re-review the whole
  diff from scratch — produce new comments only for genuinely new issues in the delta.

## 3. Per-thread state machine (re-review only)

For each thread with `needs_author_action: false` that the author has answered:

| Author action               | Your response                                                                |
| --------------------------- | --------------------------------------------------------------------------- |
| Accepted + fixed            | Verify the fix resolves it → reply `verdict=resolved`, or reopen if incomplete |
| Rejected with justification | Evaluate it → concede (`verdict=resolved`) if reasonable; else **one** counter-reply, then `verdict=escalated` |
| New code from the fix       | May open new inline comments, only for genuinely new issues in the delta    |

**Convergence:** bias to converge. Do not re-argue — on persistent disagreement
after one round-trip, set `verdict=escalated` and summarize both positions for the
human. The reviewer is the authority on closing threads (`verdict=resolved`).

## 4. Findings coverage and severity

Favor **coverage**: report every finding, with `severity` and `confidence` as
fields. Do **not** self-filter — surface low-confidence findings too. Each finding
ties back to a checklist rule or a concrete bug.

Write every finding into `comments` the same way whatever its severity. You do
**not** decide which become inline threads: `review_lib.py` routes them by
`docs/REVIEW_WORKFLOW.md` § Finding routing, and the preview shows you what went
where. Below `medium` a documentation finding — and, from round 2 on, any finding
— becomes a bullet on the summary comment instead of a thread.

❗️ **What the routing depends on is the severity you assign**, so assign it to the
defect and not to the effort of the finding. Two rules, both from § Finding routing:

* **Documentation.** `medium` or above **only** when the document says something
  **false about the code, and checkable against it** — a wrong status code, a
  parsing rule that does not hold, a repair procedure the API does not offer.
  Verify it against the code before rating it `medium`; if you have not checked,
  it is not `medium`. Style, wording, rationale written twice, section ownership,
  an unargued deferral: below `medium`, and never promoted to clear the floor.
* **KDoc and comments are documentation**, wherever they live. A finding about a
  comment in a `.kt` file is rated by the rule above, not by its file extension —
  the script cannot see this distinction and will not correct you.

On a re-review round the floor applies to everything, so a `low` finding opens no
round. Rate on what the defect costs after merge: a small diff does not make a
real defect `low`.

## 5. Produce `findings.json`

Write the review as a JSON file (the script injects markers; you write content only):

```json
{
  "reviewed_sha": "<headRefOid you reviewed>",
  "round": 1,
  "summary": "Optional one-paragraph review summary (also the SHA anchor).",
  "comments": [
    {
      "path": "core/src/main/kotlin/.../Foo.kt",
      "line": 42,
      "side": "RIGHT",
      "severity": "high",
      "confidence": "medium",
      "body": "The finding text, tied to a rule."
    }
  ],
  "thread_replies": [
    {
      "in_reply_to": 123456789,
      "verdict": "resolved",
      "body": "Verified: the fix addresses the Port/Adapter violation."
    }
  ]
}
```

`comments` = new inline findings. `thread_replies` = replies to existing reviewer
threads (use the comment `id` from `get_pr_context`). Omit either if empty.

## 6. STRICT human gate (mandatory)

❗️ **The gate is step 2, and it is yours to honour.** Both scripts are allow-listed:
nothing in the permission layer will stop you at step 3. Stopping is the gate.

1. **Preview — read-only:**
   `uv run .claude/skills/pr-post-review/scripts/preview_review.py --input findings.json`
   This renders every comment with its injected markers and prints exactly what
   would be posted. It only issues GET requests; nothing is sent.
2. **Present** the rendered plan to the user in the terminal and **stop**. Ask for
   explicit confirmation. Approval of a *previous* round is not approval of this one.
3. **Only after explicit approval**, post:
   `uv run .claude/skills/pr-post-review/scripts/post_review.py --input findings.json`

Never post without step 2 approval. Never hand-write markers — the scripts inject
them from `.claude/lib/markers.py`, the schema's single definition
(`docs/REVIEW_WORKFLOW.md` § Marker Schema documents it).

**If the post is denied or fails:** report it and hand the user the command to run
themselves (`! uv run …post_review.py --input <file>`). Do **not** retry the call
unchanged, and do **not** edit the permission configuration to unblock yourself —
an agent lifting its own restrictions is exactly the move a human must authorise
out-of-band. The rationale for the current permission shape is in
`docs/REVIEW_WORKFLOW.md` § Tooling.

## Model note

Run this review at high effort (`xhigh`) for best bug-finding recall. Project-context
grounding matters more than model choice — Opus 4.8 is the recommended default.
