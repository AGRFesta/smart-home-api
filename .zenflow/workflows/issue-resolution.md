# Generic Issue Resolution with PR Loop

## Configuration
- *Artifacts Path*: {@artifacts_path} -> `.zenflow/tasks/{task_id}`

## Workflow Steps

### [ ] Step: Issue Analysis & Planning
Analyze the provided GitHub Issue and the existing codebase.
1. Read the Issue details and requirements automatically provided in the current context.
2. Create a detailed implementation plan and save it to `{@artifacts_path}/plan.md`.
3. Do not write the final code yet. Define the architectural changes, logic updates, edge cases, and testing strategies.

### [ ] Step: Implementation & Internal Verification
Implement the changes based on the generated plan.
1. Write the code to resolve the Issue following `{@artifacts_path}/plan.md`.
2. Run relevant tests and linters in the sandbox.
3. If tests fail, fix the errors and re-test until they pass.

### [ ] Step: PR Preparation
Prepare the repository for a Pull Request.
1. Commit the changes and push to the working branch.
2. Generate a comprehensive PR description (Title, Body, "Fixes #ISSUE_NUMBER") and save it to `{@artifacts_path}/pr_description.md`.
3. Stop and wait for the user to manually open the PR on GitHub using the generated description.

### [ ] Step: PR Review Loop
Handle user-provided PR comments from GitHub.
1. Wait for the user to manually provide a PR review comment in the chat.
2. Read the comment and evaluate it carefully against the original GitHub Issue and `{@artifacts_path}/plan.md`.
3. If the comment is correct and improves the code: implement the fix, run tests, commit, push, and instruct the user to reply "Change implemented as requested".
4. If the comment introduces regressions or violates the plan: generate a technical response and save it to `{@artifacts_path}/pr_response.md` so the user can copy-paste it into GitHub. Do not make code changes.
5. CIRCUIT BREAKER: If the same discussion loops more than 3 times, pause and ask the user to resolve it offline.
6. CONTINUOUS LOOP: After processing the comment, if the PR is not merged yet, recreate this `### [ ] Step: PR Review Loop` to remain ready for the next comment.
