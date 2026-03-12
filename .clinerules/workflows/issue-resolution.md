# Generic Issue Resolution with PR Loop

## Configuration
- Save all artifacts (plans, proposals, responses) in the directory: `.cline_artifacts/` (create it if it does not exist).

## Workflow Steps

### 1. Issue Analysis & Planning
Analyze the provided GitHub Issue and the existing codebase.
1. Read the Issue details and context.
2. Create a concise implementation plan and save it to `.cline_artifacts/plan.md`.
3. IMPORTANT: Do not write the final code yet. Only define necessary architectural changes, logic updates, and edge cases to keep token usage low. Stop and wait for user approval.

### 2. Implementation & Internal Verification
Implement the changes based strictly on the approved plan.
1. Write the code to resolve the Issue.
2. Run relevant tests and linters in the terminal.
3. If tests fail, attempt to fix the errors and re-test. **CRITICAL LIMIT: Do not exceed 3 test attempts.** If tests still fail after 3 attempts, STOP and ask the user for manual guidance. Do not loop indefinitely.

### 3. PR Review Loop
Handle user-provided PR comments iteratively.
1. Evaluate the user's comment against the original Issue and `.cline_artifacts/plan.md`.
2. IN-SCOPE & CORRECT: Implement the fix, run tests (max 2 attempts), ask the user to commit, and reply with strictly: "Change implemented as requested".
3. OUT-OF-SCOPE BUT CORRECT: DO NOT make code changes. Save a new issue proposal to `.cline_artifacts/new_issue_proposal.md` and notify the user in the chat.
4. INCORRECT OR REGRESSION: DO NOT make code changes. Save a brief technical response to `.cline_artifacts/pr_response.md`.
5. CIRCUIT BREAKER: If the discussion on the exact same topic/code block loops 3 times, immediately pause and request offline resolution.
