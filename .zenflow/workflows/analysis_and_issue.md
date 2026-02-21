# Pre-Development Analysis and Issue Creation

## Configuration
- **Artifacts Path**: {@artifacts_path} → `.zenflow/tasks/{task_id}`

---

## Workflow Steps

### [ ] Step: Analysis and Discussion
Explore the codebase and analyze the user's request without making any modifications to the application's source files.
1. Examine the architecture, logic flows, or potential bugs related to the requested module.
2. Document your findings in detail and save the report in `{@artifacts_path}/analysis.md`.
3. Present a summary to the user in the chat and wait for their feedback or questions. Do not proceed to the next step until the discussion is fully resolved.

### [ ] Step: GitHub Issue Generation
Once the user confirms that an implementation is required, transform the analysis results into a ready-to-use GitHub Issue.
1. Structure the document with a clear Title, Context/Description, Expected vs. Actual Behavior (if it is a bug), and Acceptance Criteria.
2. Generate the formatted Markdown text and save it in `{@artifacts_path}/github_issue.md`.
3. Notify the user in the chat that the file is ready to be copied and pasted into GitHub, then mark the task as complete.
