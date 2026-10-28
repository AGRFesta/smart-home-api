# PR Comments Analyzer Skill

A deterministic, locally-run Claude Code skill designed to fetch, filter, and analyze GitHub Pull Request comments, and to post the author's replies back to them.

This skill follows the "Thick Scripts, Thin Instructions" architecture. It relies on Python scripts (`uv` managed) to safely interface with the GitHub CLI, filtering out bot noise, providing clean JSON context to the LLM, and injecting the role markers the model never composes itself.

## Prerequisites
To use this skill, the host machine must have:
1.  **`uv`**: For fast, hermetic Python script execution without global environment pollution.
2.  **GitHub CLI (`gh`)**: Installed and authenticated (`gh auth login`).

## Architecture
* `SKILL.md`: The semantic router and prompt instructions for Claude. Defines least-privilege constraints (the three scripts below, and nothing else).
* `scripts/get_pr_context.py`: The deterministic data pipeline. It fetches the current branch's PR data, strips out known CI/CD bots, and returns a JSON payload.
* `scripts/preview_replies.py`: Renders the replies with their injected markers. Read-only.
* `scripts/post_replies.py`: Posts them, after the human has approved the preview. Writes.
* `scripts/reply_lib.py`: Shared logic for the two entry points above.

The marker schema itself lives in `.claude/lib/markers.py`, shared with the reviewer skill so there is one definition of it; `.claude/lib/test_markers.py` asserts it (`uv run .claude/lib/test_markers.py`). The workflow both skills implement is `docs/REVIEW_WORKFLOW.md`.

## Usage
Simply trigger the skill in a Claude Code terminal session while on a branch with an active PR:
> "Check the comments on my PR and tell me what I should fix."
