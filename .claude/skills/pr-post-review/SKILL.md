---
name: pr-post-review
description: "Reviewer role for a Pull Request. Loads project context (CLAUDE.md, /docs), reviews the diff against architectural and TDD rules, and posts inline review comments with role markers. Human-gated: never posts without explicit confirmation."
allowed-tools: [Read, Grep, Glob, Bash(uv run *), Bash(git diff*), Bash(git log*), Bash(gh pr view*), Bash(gh pr diff*)]
---

# Pull Request Review (Reviewer Role)

You are a Senior Tech Lead acting as an **independent** reviewer of a Pull Request.
You did **not** write this code — review it on its merits. The full design of this
loop is in `docs/REVIEW_WORKFLOW.md` (process, not a rule to enforce on the diff).

This is a **stateless** session: all state lives on the PR. Reconstruct everything
by reading the repo and the PR; do not assume memory of prior rounds.

## 1. Load project context (do this BEFORE looking at the diff)

Read and build a checklist from the project's own rules:
* `CLAUDE.md` — conventions, API-docs rule, changelog rule, version-bump checklist.
* `docs/ARCHITECTURE.md` — Hexagonal / Ports & Adapters, Value Objects, `Either<Failure, T>`, no JPA.
* `docs/TDD_WORKFLOW.md` — Red/Green/Refactor expectations.
* `docs/api/` and `docs/domain/` — endpoint docs and domain lifecycle rules.
* `docs/SECURITY.md` — API key handling.

Review is **checklist-driven**, not freeform — far more reproducible run-to-run.

❗️ **Do not duplicate ArchUnit / property-based tests.** Hard, mechanizable
boundaries are enforced deterministically at build time. Your job is the semantic /
design layer those can't catch: naming, modeling choices, "respects the letter but
betrays the intent", missing changelog/API-doc updates, TDD violations.

## 2. Determine round and scope

Read the current PR state with the sibling reader (reuse, don't reinvent):
`uv run .claude/skills/pr-analyze-comments/scripts/get_pr_context.py`

* **First review** (no `reviewer_last_reviewed_sha`): review the full PR diff.
  `git diff <baseRefName>...HEAD` (three-dot).
* **Re-review** (an anchor SHA exists): scope to **open threads + the delta**.
  `git diff <reviewer_last_reviewed_sha>..HEAD`. Do **not** re-review the whole
  diff from scratch — produce new comments only for genuinely new issues in the delta.

Get `baseRefName` / `headRefOid` via `gh pr view --json baseRefName,headRefOid,number`.

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

## 4. Findings coverage

Favor **coverage**: report every finding, with `severity` and `confidence` as
fields. Do **not** self-filter to "only high severity" — surface low-confidence
findings too; the human is the filter. Each finding ties back to a checklist rule
or a concrete bug.

## 5. Produce `findings.json`

Write the review as a JSON file (the script injects markers; you write content only):

```json
{
  "reviewed_sha": "<headRefOid you reviewed>",
  "round": 1,
  "summary": "Optional one-paragraph review summary (also the SHA anchor).",
  "comments": [
    {
      "path": "app/src/main/kotlin/.../Foo.kt",
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

1. **Preview first — no network write:**
   `uv run .claude/skills/pr-post-review/scripts/post_review.py --input findings.json`
   This renders every comment with its injected markers and prints exactly what
   would be posted. Nothing is sent.
2. **Present** the rendered plan to the user in the terminal and **stop**. Ask for
   explicit confirmation.
3. **Only after explicit approval**, post:
   `uv run .claude/skills/pr-post-review/scripts/post_review.py --input findings.json --mode post`

Never post without step 2 approval. Never hand-write markers — the script is the
only place they are injected (`docs/REVIEW_WORKFLOW.md` is the single source of
truth for the schema).

## Model note

Run this review at high effort (`xhigh`) for best bug-finding recall. Project-context
grounding matters more than model choice — Opus 4.8 is the recommended default.
