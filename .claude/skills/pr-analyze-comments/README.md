# PR Comments Analyzer Skill

A deterministic, locally-run Claude Code skill designed to fetch, filter, and analyze GitHub Pull Request comments.

This skill follows the "Thick Scripts, Thin Instructions" architecture. It relies on a Python script (`uv` managed) to safely interface with the GitHub CLI, filtering out bot noise and providing clean JSON context to the LLM.

## Prerequisites
To use this skill, the host machine must have:
1.  **`uv`**: For fast, hermetic Python script execution without global environment pollution.
2.  **GitHub CLI (`gh`)**: Installed and authenticated (`gh auth login`).

## Architecture
* `SKILL.md`: The semantic router and prompt instructions for Claude. Defines least-privilege constraints (read-only + specific bash execution).
* `scripts/get_pr_context.py`: The deterministic data pipeline. It fetches the current branch's PR data, strips out known CI/CD bots, and returns a JSON payload.

## Usage
Simply trigger the skill in a Claude Code terminal session while on a branch with an active PR:
> "Check the comments on my PR and tell me what I should fix."
