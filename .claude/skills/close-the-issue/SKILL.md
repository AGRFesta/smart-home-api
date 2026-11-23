---
name: close-the-issue
description: "Use when the user says that the code review of an issue has passed, or asks to close the issue. Never use it at the end of development: development ends with the Definition of Done in docs/TDD_WORKFLOW.md, before the review. Carries the closing steps: the check that build and detekt pass on the reviewed code, and the translation of the issue draft into the GitHub issue text."
---

# Close the issue

This file owns what happens to an issue after its code review has passed. `docs/TDD_WORKFLOW.md`
§ Definition of Done owns what happens before the review: the build, detekt, the surface docs, the
changelog and the code review reminder.

## 1. When it applies

**Only the user starts the closing.** Git and the pull request are theirs, so no session can observe
that the review has passed. A session that finished development stops at the Definition of Done and
does not load this skill.

Read the header of `issues/ISSUE-<number>-*.md`. A status other than `review` means the Definition
of Done has not run on this issue: report it to the user and stop.

## 2. Build and detekt on the reviewed code

A finding fixed after the review changed the code, and the verdict of the Definition of Done covers
the code as it stood when it ran.

Run `./gradlew build` and `./gradlew detekt` through the `definition-of-done` agent, as
`docs/TDD_WORKFLOW.md` § Build and detekt through the agent says. Skip the run only when this
session ran both after the last change to a file.

A failure ends the closing. The fix is development: report the failure to the user and leave the
draft at `review`.

## 3. The issue draft

Set the status of the draft to `closing` and turn the draft into the GitHub issue text, as
`docs/ISSUE_FORMAT.md` § Lifecycle says.

## 4. Report

Tell the user the build and detekt results and the path of the translated draft.
