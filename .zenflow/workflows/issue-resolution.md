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

### [ ] Step: PR Review Loop
Handle user-provided PR comments from GitHub.
1. Wait for the user to manually provide a PR review comment in the chat.
2. Read the comment and evaluate it carefully against the original GitHub Issue and `{@artifacts_path}/plan.md`.
3. IN-SCOPE & CORRECT: If the comment is correct, improves the code, and is strictly related to the original issue: implement the fix, run tests, commit, push, and instruct the user to reply "Change implemented as requested".
4. OUT-OF-SCOPE BUT CORRECT: If the comment is technically valid but falls outside the scope of the current issue: DO NOT make code changes.
    - Generate a comprehensive title and description for a new GitHub Issue (including context and the suggested improvement) and save it to `{@artifacts_path}/new_issue_proposal.md`.
    - Generate a polite PR response explaining that the suggestion is valuable but out of scope, and mention that a separate issue is being created to track it.
5. INCORRECT OR REGRESSION: If the comment introduces regressions, violates architectural patterns, or breaks the plan: DO NOT make code changes. Generate a technical response and save it to `{@artifacts_path}/pr_response.md` so the user can copy-paste it into GitHub.
6. CIRCUIT BREAKER: If the same discussion loops more than 3 times, pause and ask the user to resolve it offline.
7. CONTINUOUS LOOP: After processing the comment, if the PR is not merged yet, recreate this `### [ ] Step: PR Review Loop` to remain ready for the next comment.
