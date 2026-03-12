# Pre-Development Analysis and Issue Creation

## Configuration
- **Artifacts Path**: Save all generated documents in `.cline_artifacts/analysis/` (create the directory if it does not exist).

---

## Workflow Steps

### 1. Codebase Analysis & Discussion
Explore the codebase to analyze the user's request. **CRITICAL: Do not modify any application source code files during this phase. Read-only operations only.**
1. Examine the architecture, logic flows, and potential bugs related to the requested feature or issue.
2. Document your detailed technical findings and save the report to `.cline_artifacts/analysis/analysis_report.md`.
3. Present a concise, easy-to-read summary to the user in the chat, highlighting the root cause or architectural impact.
4. Explicitly ask the user: *"Do you agree with this analysis? Should I proceed to generate the GitHub Issue, or do we need to investigate further?"*
5. Wait passively for the user's feedback. Do not proceed to the next step until the discussion is resolved and approved.

### 2. GitHub Issue Generation
Once the user confirms the analysis is correct, transform the findings into a highly structured GitHub Issue.
1. Structure the document strictly with the following sections:
   - **Title**: Clear and highly descriptive.
   - **Context/Description**: Why this issue exists or is needed.
   - **Steps to Reproduce** (if it is a bug).
   - **Expected vs. Actual Behavior** (if it is a bug).
   - **Proposed Solution**: A high-level technical approach based on the approved analysis.
   - **Acceptance Criteria**: A checklist of exact conditions that must be met to close the issue.
2. Generate the formatted Markdown text and save it to `.cline_artifacts/analysis/github_issue.md`.
3. Notify the user in the chat that the file is ready. 
4. *Optional*: Ask the user if they want you to automatically publish this issue to the repository using the `gh issue create` command in the terminal.
5. Mark the task as complete and wait for new instructions.
