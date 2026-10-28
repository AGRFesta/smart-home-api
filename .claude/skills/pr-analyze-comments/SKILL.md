---
name: pr-analyze-comments
description: "Author role for a Pull Request. Analyzes unresolved comments (reviewer role markers and human comments), decides accept or reject, and posts the replies with role markers. Human-gated: never posts and never edits files without explicit confirmation."
allowed-tools: [Read, Bash(uv run .claude/skills/pr-analyze-comments/scripts/get_pr_context.py*), Bash(uv run .claude/skills/pr-analyze-comments/scripts/preview_replies.py*), Bash(uv run .claude/skills/pr-analyze-comments/scripts/post_replies.py*)]
---

# Pull Request Comments Analysis

You are a Senior Software Engineer acting as a critical filter for a Pull Request.

## 1. Context Acquisition
Execute `uv run .claude/skills/pr-analyze-comments/scripts/get_pr_context.py` to get a structured JSON output of the PR context.

*Role attribution is marker-based.* The reviewer and the author post via the same GitHub account, so roles are read from `<!-- smarthome:role=... -->` markers in the comment body, **not** from `author.login`:
* `role=reviewer` — a reviewer comment you (the author session) must act on.
* `role=author` — your own prior reply (context, not an action).
* **no marker** — a genuine human comment; surface it as human input.

Inline comments are grouped into threads (`inline_threads`); a thread with `needs_author_action: true` is an open reviewer comment with no author reply yet (and not `verdict=resolved`). The marker schema is documented in `docs/REVIEW_WORKFLOW.md`.

## 2. Limits and Anti-Definitions (STRICT CONSTRAINTS)
* **DO NOT apply changes autonomously:** You must ask for explicit confirmation before modifying local files. Do not use git commit or push.
* **DO NOT post without the gate:** replies go out through the preview-then-post sequence in § 6, after the human has approved the preview. Never post a reply by any other route.
* **Never hand-write a marker.** `post_replies.py` injects it from the shared schema (`.claude/lib/markers.py`); a marker you compose yourself is the one that can be wrong, and a wrong one reads as a *human* comment on the next round.
* **Never write a `verdict`.** Closing a thread is the reviewer's authority (`docs/REVIEW_WORKFLOW.md` § Thread closure authority). Say "fixed in `<sha>`" and leave the thread open.
* **Critical AI Evaluation:** Reviewer comments (`role=reviewer`) are AI-generated — treat their suggestions with high scrutiny. Do not blindly accept a suggestion if it introduces technical debt, violates the project architecture, or is out of scope. Unmarked (human) comments still get evaluated on their technical merits.

## 3. Decision Tree
Focus on threads with `needs_author_action: true` (and any unmarked human comments). For each, evaluate the technical validity based on the source code:

**CASE A: The comment is ACCEPTED (Valid suggestion)**
* Explain why the suggestion (human or AI) is technically correct.
* Write a code proposal (diff or snippet).
* Ask the user if you should apply the modification to the local files.
* The reply is `decision: "accepted"`.

**CASE B: The comment is REJECTED (Incorrect, out of scope, or hallucinated)**
* Explain internally why the change should not be made (especially if it's an AI hallucination).
* Write a polite, technical justification as the reply body.
* The reply is `decision: "rejected"`.

## 4. Non-blocking observations

The reviewer's summary comment may carry a `### Non-blocking observations`
section. These are findings the reviewer deliberately did not open a thread for
(`docs/REVIEW_WORKFLOW.md` § Finding routing) — they are below the severity floor,
and no later round will raise them again.

List them for the user with a one-line assessment each, and offer to fold the ones
worth taking into the current commit. **Draft no reply for them**: there is no
thread to answer, and answering one turns a finding that costs nothing into a
round-trip. If one turns out to be more serious than its rating, say so — that is
a rating to challenge with the user, not a reply to post.

## 5. Produce `replies.json`

Write the replies as a JSON file (the script injects the marker; you write content only):

```json
{
  "replies": [
    {
      "comment_id": 123456789,
      "decision": "accepted",
      "body": "Fixed in a1b2c3d: the mapper now returns the Failure instead of throwing."
    }
  ]
}
```

`comment_id` is any message `id` in the thread you are answering — the script
resolves it to the thread and replies there. `decision` is `accepted` or
`rejected`; nothing else is accepted, and it is the only marker key you influence.

## 6. STRICT human gate (mandatory)

❗️ **The gate is step 2, and it is yours to honour.** All three scripts are
allow-listed: nothing in the permission layer will stop you at step 3. Stopping is
the gate.

1. **Preview — read-only:**
   `uv run .claude/skills/pr-analyze-comments/scripts/preview_replies.py --input replies.json`
   This renders every reply with its injected marker and prints exactly what would
   be posted, including the threads it will skip. It only issues GET requests.
2. **Present** the rendered plan to the user in the terminal and **stop**. Ask for
   explicit confirmation. Approval of a *previous* round is not approval of this one.
3. **Only after explicit approval**, post:
   `uv run .claude/skills/pr-analyze-comments/scripts/post_replies.py --input replies.json`

A thread that already carries an author reply after the last reviewer message is
skipped, so re-running the same file after a partial failure sends only what is
missing — but a re-run is not a substitute for a fresh approval.

**If the post is denied or fails:** report it and hand the user the command to run
themselves (`! uv run …post_replies.py --input <file>`). Do **not** retry the call
unchanged, and do **not** edit the permission configuration to unblock yourself.
The rationale for the current permission shape is in `docs/REVIEW_WORKFLOW.md`
§ Tooling.
