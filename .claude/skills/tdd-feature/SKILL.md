---
name: tdd-feature
description: "Use before writing any code for a new feature or a bug fix — including a request that only names an issue, such as \"implement #166\". Read the issue first: an issue that declares itself pure structural refactoring (no new behaviour, no bug fix) is not a case for this skill, and an issue that declares nothing about its nature is a feature or a bug fix, so this skill applies. Loads the project's Red/Green/Refactor workflow, whose Phase 0 test list is a human gate before any code is written."
---

# TDD for a new feature or a bug fix

Read `docs/TDD_WORKFLOW.md` in full and follow it. It owns the workflow; this file only decides when
the workflow applies.

## When it applies

The work applies this skill when it adds a behaviour or fixes a bug. The issue file decides, not the
wording of the request: read the issue before choosing.

- **The issue declares pure structural refactoring** — no new behaviour, no bug fix. The workflow does
  not apply: do not read `docs/TDD_WORKFLOW.md`, and make the change directly.
- **The issue declares nothing about its nature.** The workflow applies. Only the exception is
  declared, so silence means a feature or a bug fix.
- **No issue, and the request adds or changes behaviour.** The workflow applies.

❗️ A missing declaration defaults to the workflow. A needless workflow shows at the Phase 0 gate,
where the user can stop it; a feature written without the workflow shows nowhere.
