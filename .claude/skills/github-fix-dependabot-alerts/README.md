# Dependabot Alerts Resolver Skill

A deterministic, locally-run Claude Code skill designed to fetch, filter, and analyze GitHub Dependabot security alerts.

This skill follows the "Thick Scripts, Thin Instructions" architecture. It relies on a Python script (`uv` managed) to safely interface with the GitHub REST API, extracting only actionable open vulnerabilities and providing clean JSON context to the LLM.

## Prerequisites
To use this skill, the host machine must have:
1.  **`uv`**: For fast, hermetic Python script execution without global environment pollution.
2.  **GitHub CLI (`gh`)**: Installed and authenticated (`gh auth login`). Ensure your GitHub token has access to read security events.

## Architecture
* `SKILL.md`: The semantic router and prompt instructions for Claude. Defines least-privilege constraints (read-only + specific bash execution).
* `scripts/get_dependabot_alerts.py`: The deterministic data pipeline. It fetches open Dependabot alerts via the GitHub API and returns a structured JSON payload.

## Usage
Simply trigger the skill in a Claude Code terminal session:
> "Check the Dependabot alerts for this repository and help me fix them."