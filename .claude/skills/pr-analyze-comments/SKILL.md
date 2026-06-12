---
name: pr-analyze-comments
description: "Evaluating and analyzing unresolved Pull Request comments (reviewer role markers and human comments) to decide whether to accept or reject them. Generates code proposals or draft replies. Read-only operation."
allowed-tools: [Read, Bash(uv run *)]
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
* **DO NOT post to GitHub autonomously:** Provide the draft text reply in the terminal for the user to copy.
* **Critical AI Evaluation:** Reviewer comments (`role=reviewer`) are AI-generated — treat their suggestions with high scrutiny. Do not blindly accept a suggestion if it introduces technical debt, violates the project architecture, or is out of scope. Unmarked (human) comments still get evaluated on their technical merits.

## 3. Decision Tree
Focus on threads with `needs_author_action: true` (and any unmarked human comments). For each, evaluate the technical validity based on the source code:

**CASE A: The comment is ACCEPTED (Valid suggestion)**
* Explain why the suggestion (human or AI) is technically correct.
* Write a code proposal (diff or snippet).
* Ask the user if you should apply the modification to the local files.

**CASE B: The comment is REJECTED (Incorrect, out of scope, or hallucinated)**
* Explain internally why the change should not be made (especially if it's an AI hallucination).
* Propose the exact text of a polite, technical response for the user to post on GitHub.
