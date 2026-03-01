# Generic Issue Resolution with PR Loop

## Configuration
- *Artifacts Path*: {@artifacts_path} -> `.zenflow/tasks/{task_id}`

## Workflow Steps

### [ ] Step: Issue Analysis & Planning
Analyze the provided GitHub Issue and the existing codebase.
1. Read the Issue details and context.
2. Create a concise implementation plan and save it to `{@artifacts_path}/plan.md`.
3. Do not write the final code yet. Only define necessary architectural changes, logic updates, and edge cases to keep token usage low.

### [ ] Step: Implementation & Internal Verification
Implement the changes based on the generated plan.
1. Write the code to resolve the Issue strictly following `{@artifacts_path}/plan.md`.
2. Run relevant tests and linters in the sandbox.
3. If tests fail, attempt to fix the errors and re-test. **CRITICAL LIMIT: Do not exceed 3 test attempts.** If tests still fail after 3 attempts, STOP and ask the user for manual guidance. Do not loop indefinitely.

### [ ] Step: PR Review Loop
Handle user-provided PR comments iteratively without rewriting these instructions.
1. Wait passively for the user to provide a PR review comment.
2. Evaluate the comment against the original Issue and `{@artifacts_path}/plan.md`.
3. IN-SCOPE & CORRECT: Implement the fix, run tests (max 2 attempts), commit, push, and reply with strictly: "Change implemented as requested".
4. OUT-OF-SCOPE BUT CORRECT: DO NOT make code changes.
    - Save a new issue proposal to `{@artifacts_path}/new_issue_proposal.md`.
    - Provide a short, polite response in the chat stating it is out of scope and a new issue has been drafted.
5. INCORRECT OR REGRESSION: DO NOT make code changes. Save a brief technical response to `{@artifacts_path}/pr_response.md`.
6. CIRCUIT BREAKER: If the discussion on the exact same topic loops 3 times, immediately pause and request offline resolution.
7. STAY IDLE: After processing, simply wait for the next user input. Do not recreate or restate this step in the output.
