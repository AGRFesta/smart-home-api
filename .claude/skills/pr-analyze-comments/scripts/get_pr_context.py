# /// script
# requires-python = ">=3.11"
# ///

import subprocess
import json
import sys

# List of NOISE bot accounts (CI/CD, linters, updaters).
# AI reviewers (like gemini-code-assist) are NOT here, so they will be analyzed.
NOISE_BOTS = {
    "dependabot",
    "dependabot-preview",
    "github-actions",
    "sonarcloud",
    "codecov",
    "vercel",
    "renovate",
    "snyk-bot"
}

def is_noise_bot(author_login: str) -> bool:
    """Checks if the author is a noise/CI bot that we want to ignore."""
    if not author_login:
        return False

    author_lower = author_login.lower()

    if author_lower in NOISE_BOTS:
        return True

    if "[bot]" in author_lower and "gemini" not in author_lower and "copilot" not in author_lower:
        return True

    return False

def fetch_and_filter_pr_comments():
    try:
        # 1. Fetch general PR data and get the PR number
        # FIXED: Added encoding="utf-8" to handle emojis and special characters on Windows
        pr_result = subprocess.run(
            ["gh", "pr", "view", "--json", "title,url,comments,reviews,number"],
            capture_output=True,
            text=True,
            check=True,
            encoding="utf-8"
        )
        pr_data = json.loads(pr_result.stdout)
        pr_number = pr_data.get("number")

        # 2. Fetch the actual inline code comments using GitHub REST API
        api_endpoint = f"repos/:owner/:repo/pulls/{pr_number}/comments"
        # FIXED: Added encoding="utf-8" here as well
        inline_result = subprocess.run(
            ["gh", "api", api_endpoint],
            capture_output=True,
            text=True,
            check=True,
            encoding="utf-8"
        )
        inline_comments = json.loads(inline_result.stdout)

        pr_context = {
            "pr_title": pr_data.get("title"),
            "url": pr_data.get("url"),
            "comments_to_analyze": []
        }

        # --- A. General PR comments ---
        for comment in pr_data.get("comments", []):
            author = comment.get("author", {}).get("login", "")
            body = comment.get("body", "").strip()

            if not is_noise_bot(author) and body:
                pr_context["comments_to_analyze"].append({
                    "type": "general_comment",
                    "author": author,
                    "body": body
                })

        # --- B. High-level reviews ---
        for review in pr_data.get("reviews", []):
            author = review.get("author", {}).get("login", "")
            body = review.get("body", "").strip()

            if not is_noise_bot(author) and body:
                pr_context["comments_to_analyze"].append({
                    "type": "code_review_summary",
                    "author": author,
                    "state": review.get("state"),
                    "body": body
                })

        # --- C. INLINE CODE COMMENTS (The real Gemini/Human feedback) ---
        for comment in inline_comments:
            author = comment.get("user", {}).get("login", "")
            body = comment.get("body", "").strip()
            file_path = comment.get("path", "")
            line = comment.get("line") or comment.get("original_line")

            if not is_noise_bot(author) and body:
                pr_context["comments_to_analyze"].append({
                    "type": "inline_code_comment",
                    "author": author,
                    "file": file_path,
                    "line": line,
                    "body": body
                })

        print(json.dumps(pr_context, indent=2))

    except subprocess.CalledProcessError as e:
        error_msg = {"error": "Unable to fetch PR data via CLI.", "details": e.stderr.strip()}
        print(json.dumps(error_msg))
        sys.exit(1)
    except Exception as e:
        error_msg = {"error": f"Unexpected error: {str(e)}"}
        print(json.dumps(error_msg))
        sys.exit(1)

if __name__ == "__main__":
    fetch_and_filter_pr_comments()
