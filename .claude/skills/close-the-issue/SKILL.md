---
name: close-the-issue
description: "Use at the end of every issue, before handing the work back to the user — after the last TDD cycle, after a structural refactoring, after a documentation or tooling change. Carries the Definition of Done that every issue shares: the full build, detekt, and the checklists for a REST endpoint (docs/api, API_INDEX.md, the Bruno collection) and the CHANGELOG.md line."
---

# Close the issue

This file owns what every issue updates before it is handed back. `docs/TDD_WORKFLOW.md`
§ Definition of Done links it as its step 3.

## 1. Build and static analysis

A session that ran the TDD workflow has done this as steps 1 and 2 of its Definition of Done. Any
other session does it here.

Run both commands through the `definition-of-done` agent (`.claude/agents/definition-of-done.md`),
not in this session:

- `./gradlew build` — every module, every test.
- `./gradlew detekt` — run explicitly, because `build` does not cover it. Leave it clean.

The agent keeps the Gradle output out of this session and returns the verdict: two lines on success,
the failing tests with their assertion output verbatim on failure, the failure class when it is
infrastructural. It changes no file, so every fix is made here. An infrastructural failure is
escalated to the user, as `docs/TDD_WORKFLOW.md` § Escalation conditions says.

❗️ **The test runs of a TDD cycle stay in this session.** Each one is the evidence for a pre-declared
RED, and only the Definition of Done has no declaration to judge: it passes or it fails.
`docs/TDD_WORKFLOW.md` § Per-cycle test execution owns the rule.

## 2. The checklists

Apply each one whose condition the change meets.

- **A REST endpoint was added or changed.** Update `docs/api/<resource>.md`, the table in
  `docs/api/API_INDEX.md`, and the Bruno collection: `bruno/<resource>/`, one `.bru` file per
  endpoint, mirroring the existing folder structure. A request inherits the collection's bearer
  auth (`auth: inherit`); `docs/SECURITY.md` owns the API key.
- **A domain decision was added or changed.** Re-read the owning file in `docs/domain/` and update
  it so that it is still true.
- **The changelog.** One line under `## [Unreleased]` in `CHANGELOG.md`, in the imperative mood, with
  the issue number in parentheses. Sections: `Added` (endpoints, observable behaviours), `Changed`
  (contracts, behaviours), `Fixed` (bugs with observable impact), `Security` (CVE bumps). **No**
  line for tests, internal refactoring, style cleanup or developer tooling. Example:
  `- Add \`GET /home/stream\` SSE endpoint for real-time dashboard. (#173)`

## 3. Report

A session that ran the TDD workflow reports as step 4 of its Definition of Done. Any other session
tells the user what changed, which checklists applied, and the build and detekt results. The user
takes it from here: git and the pull request are theirs.
