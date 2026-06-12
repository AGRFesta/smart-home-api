# /// script
# requires-python = ">=3.11"
# ///
"""Build a structured view of a Pull Request's conversation for analysis.

Role attribution is **marker-based**, not identity-based: the reviewer and the
author both post via the same GitHub account, so `user.login` cannot tell them
apart. Instead, every automated comment carries an HTML-comment marker in its
body, e.g.:

    <!-- smarthome:role=reviewer -->
    <!-- smarthome:role=author decision=rejected -->
    <!-- smarthome:reviewed-sha=abc1234 round=2 verdict=resolved -->

Classification trichotomy:
    role=reviewer  -> reviewer comment (the author session must act on it)
    role=author    -> the author's own prior reply (context, not an action)
    no marker      -> a genuine human comment (surface as human input)

The single source of truth for the marker schema is docs/REVIEW_WORKFLOW.md.
"""

import json
import re
import subprocess
import sys

# Matches one marker block and captures its inner "key=value key=value" payload.
MARKER_RE = re.compile(r"<!--\s*smarthome:(.*?)-->", re.DOTALL)
# Matches a single key=value token inside a marker payload.
PAIR_RE = re.compile(r"(\w[\w-]*)=([^\s]+)")


def parse_markers(body: str) -> dict:
    """Extract and merge all smarthome markers found in a comment body."""
    markers: dict = {}
    if not body:
        return markers
    for payload in MARKER_RE.findall(body):
        for key, value in PAIR_RE.findall(payload):
            markers[key] = value
    return markers


def strip_markers(body: str) -> str:
    """Return the human-readable body with smarthome markers removed."""
    if not body:
        return ""
    return MARKER_RE.sub("", body).strip()


def classify_role(markers: dict) -> str:
    """Map a marker set to a role. Unmarked comments are genuine humans."""
    role = markers.get("role")
    return role if role in ("reviewer", "author") else "human"


def make_comment(*, comment_type, author_login, body, created_at,
                 comment_id=None, in_reply_to_id=None, file=None, line=None):
    markers = parse_markers(body)
    return {
        "type": comment_type,
        "author_login": author_login,
        "role": classify_role(markers),
        "markers": markers,
        "body": strip_markers(body),
        "created_at": created_at,
        "id": comment_id,
        "in_reply_to_id": in_reply_to_id,
        "file": file,
        "line": line,
    }


def root_id(comment: dict, by_id: dict) -> int:
    """Resolve an inline comment to the id of the thread it belongs to."""
    current = comment
    seen = set()
    while current.get("in_reply_to_id") and current["in_reply_to_id"] in by_id:
        if current["id"] in seen:  # defensive against cycles
            break
        seen.add(current["id"])
        current = by_id[current["in_reply_to_id"]]
    return current["id"]


def build_inline_threads(inline_comments: list) -> list:
    """Group inline comments into threads and compute per-thread state."""
    by_id = {c["id"]: c for c in inline_comments}
    threads: dict = {}

    for comment in inline_comments:
        rid = root_id(comment, by_id)
        threads.setdefault(rid, []).append(comment)

    result = []
    for rid, messages in threads.items():
        messages.sort(key=lambda c: (c.get("created_at") or "", c.get("id") or 0))
        root = by_id.get(rid, messages[0])

        # Latest verdict set by a reviewer message in the thread.
        verdict = None
        for msg in messages:
            if msg["role"] == "reviewer" and msg["markers"].get("verdict"):
                verdict = msg["markers"]["verdict"]

        last = messages[-1]
        needs_author_action = last["role"] == "reviewer" and verdict != "resolved"

        result.append({
            "thread_id": rid,
            "file": root.get("file"),
            "line": root.get("line"),
            "verdict": verdict,
            "needs_author_action": needs_author_action,
            "messages": [
                {k: msg[k] for k in
                 ("role", "author_login", "body", "markers", "created_at", "id")}
                for msg in messages
            ],
        })

    # Threads needing action first, then by file/line for stable output.
    result.sort(key=lambda t: (not t["needs_author_action"],
                               t.get("file") or "", t.get("line") or 0))
    return result


def gh_json(args: list):
    proc = subprocess.run(
        ["gh", *args],
        capture_output=True, text=True, check=True, encoding="utf-8",
    )
    return json.loads(proc.stdout)


def fetch_and_build_pr_context():
    try:
        # 1. PR metadata + flat comments + review summaries.
        pr_data = gh_json([
            "pr", "view", "--json",
            "title,url,comments,reviews,number,headRefOid",
        ])
        pr_number = pr_data.get("number")

        # 2. Inline review comments (the threaded, line-anchored feedback).
        inline_raw = gh_json(["api", f"repos/:owner/:repo/pulls/{pr_number}/comments"])

        # --- A. Flat PR-level comments ---
        items = []
        for c in pr_data.get("comments", []):
            body = (c.get("body") or "").strip()
            if not body:
                continue
            items.append(make_comment(
                comment_type="general_comment",
                author_login=(c.get("author") or {}).get("login", ""),
                body=body,
                created_at=c.get("createdAt"),
            ))

        # --- B. Review summaries ---
        for r in pr_data.get("reviews", []):
            body = (r.get("body") or "").strip()
            if not body:
                continue
            item = make_comment(
                comment_type="code_review_summary",
                author_login=(r.get("author") or {}).get("login", ""),
                body=body,
                created_at=r.get("submittedAt"),
            )
            item["state"] = r.get("state")
            items.append(item)

        # --- C. Inline code comments (grouped into threads) ---
        inline_comments = [
            make_comment(
                comment_type="inline_code_comment",
                author_login=(c.get("user") or {}).get("login", ""),
                body=(c.get("body") or "").strip(),
                created_at=c.get("created_at"),
                comment_id=c.get("id"),
                in_reply_to_id=c.get("in_reply_to_id"),
                file=c.get("path", ""),
                line=c.get("line") or c.get("original_line"),
            )
            for c in inline_raw
            if (c.get("body") or "").strip()
        ]
        inline_threads = build_inline_threads(inline_comments)

        # Most recent SHA the reviewer claims to have reviewed (delta anchor).
        # Pick by max created_at, not iteration order — items/threads are not
        # guaranteed to be in chronological order. ISO-8601 UTC sorts lexically.
        sha_candidates = []
        for item in items:
            if item["markers"].get("reviewed-sha"):
                sha_candidates.append((item.get("created_at") or "",
                                       item["markers"]["reviewed-sha"]))
        for thread in inline_threads:
            for msg in thread["messages"]:
                if msg["markers"].get("reviewed-sha"):
                    sha_candidates.append((msg.get("created_at") or "",
                                           msg["markers"]["reviewed-sha"]))
        reviewer_last_reviewed_sha = max(sha_candidates)[1] if sha_candidates else None

        pr_context = {
            "pr_title": pr_data.get("title"),
            "pr_number": pr_number,
            "url": pr_data.get("url"),
            "head_sha": pr_data.get("headRefOid"),
            "reviewer_last_reviewed_sha": reviewer_last_reviewed_sha,
            "items": items,
            "inline_threads": inline_threads,
        }

        print(json.dumps(pr_context, indent=2))

    except subprocess.CalledProcessError as e:
        print(json.dumps({
            "error": "Unable to fetch PR data via CLI.",
            "details": (e.stderr or "").strip(),
        }))
        sys.exit(1)
    except Exception as e:  # noqa: BLE001 - surface any failure as JSON
        print(json.dumps({"error": f"Unexpected error: {e}"}))
        sys.exit(1)


if __name__ == "__main__":
    fetch_and_build_pr_context()
