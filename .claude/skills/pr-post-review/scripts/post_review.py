# /// script
# requires-python = ">=3.11"
# ///
"""Post reviewer-role comments to a Pull Request with deterministic markers.

The reviewer model writes content only (a findings.json file); this script is the
**single place** where smarthome markers are injected, so a marker can never be
forgotten or malformed. The marker schema is documented in docs/REVIEW_WORKFLOW.md.

Modes:
    preview (default) -- render every comment with its injected markers and print
                         exactly what would be posted. No network writes.
    post              -- actually post to GitHub. Run only after human approval.

Input (findings.json):
    {
      "reviewed_sha": "<sha>",        # optional; defaults to the PR head
      "round": 1,                      # optional; defaults to 1
      "summary": "text",              # optional; posted as the SHA-anchor comment
      "comments": [                    # new inline findings
        {"path","line","side","severity","confidence","body"}
      ],
      "thread_replies": [              # replies to existing reviewer threads
        {"in_reply_to","verdict","body"}
      ]
    }
"""

import argparse
import json
import re
import subprocess
import sys

REVIEWER_HEADER = "🤖 **Reviewer**"
MARKER_RE = re.compile(r"<!--\s*smarthome:(.*?)-->", re.DOTALL)
SHA_RE = re.compile(r"[0-9a-f]{40}", re.IGNORECASE)  # full SHA-1 commit id


def build_marker(pairs: dict) -> str:
    """Render a smarthome marker; role=reviewer is always first."""
    ordered = {"role": "reviewer"}
    for key, value in pairs.items():
        if value is not None and value != "":
            ordered[key] = value
    body = " ".join(f"{k}={v}" for k, v in ordered.items())
    return f"<!-- smarthome:{body} -->"


def render_inline_body(comment: dict, round_n: int) -> str:
    meta = []
    if comment.get("severity"):
        meta.append(f"severity: {comment['severity']}")
    if comment.get("confidence"):
        meta.append(f"confidence: {comment['confidence']}")
    header = REVIEWER_HEADER + (f" — _{' · '.join(meta)}_" if meta else "")
    marker = build_marker({"round": round_n})
    return f"{header}\n\n{comment['body'].strip()}\n\n{marker}"


def render_reply_body(reply: dict) -> str:
    marker = build_marker({"verdict": reply.get("verdict")})
    return f"{REVIEWER_HEADER}\n\n{reply['body'].strip()}\n\n{marker}"


def render_summary_body(text: str, sha: str, round_n: int) -> str:
    marker = build_marker({"reviewed-sha": sha, "round": round_n})
    body = (text or "").strip()
    intro = f"{REVIEWER_HEADER} — review summary (round {round_n}, sha `{sha}`)"
    return f"{intro}\n\n{body}\n\n{marker}" if body else f"{intro}\n\n{marker}"


def normalize(body: str) -> str:
    """Strip markers/header/whitespace so duplicate detection is content-based."""
    text = MARKER_RE.sub("", (body or "").replace("\r\n", "\n")).strip()
    if text.startswith(REVIEWER_HEADER):
        parts = text.split("\n\n", 1)
        if len(parts) > 1:
            text = parts[1]
    return re.sub(r"\s+", " ", text).strip().lower()


def gh_json(args: list):
    proc = subprocess.run(
        ["gh", *args], capture_output=True, text=True, check=True, encoding="utf-8"
    )
    return json.loads(proc.stdout)


def gh_write(args: list):
    proc = subprocess.run(
        ["gh", *args], capture_output=True, text=True, encoding="utf-8"
    )
    if proc.returncode != 0:
        raise RuntimeError((proc.stderr or proc.stdout or "").strip())
    return proc.stdout


def existing_inline_keys(pr_number: int) -> set:
    """(path, line, normalized-body) of inline comments already on the PR."""
    keys = set()
    for c in gh_json(["api", f"repos/:owner/:repo/pulls/{pr_number}/comments"]):
        line = c.get("line") or c.get("original_line")
        keys.add((c.get("path", ""), line, normalize(c.get("body", ""))))
    return keys


def build_plan(findings: dict, pr_number: int, head_sha: str) -> dict:
    # Reject a missing/placeholder reviewed_sha — GitHub needs a real full commit
    # id; fall back to the PR head.
    reviewed_sha = findings.get("reviewed_sha") or head_sha
    if not SHA_RE.fullmatch(str(reviewed_sha)):
        reviewed_sha = head_sha
    round_n = int(findings.get("round", 1))
    existing = existing_inline_keys(pr_number)

    actions = []

    for c in findings.get("comments", []):
        body_text = c.get("body")
        if not isinstance(body_text, str) or not body_text.strip():
            actions.append({"kind": "inline", "error": "missing or invalid body",
                            "path": c.get("path")})
            continue
        body = render_inline_body(c, round_n)
        path = c.get("path")
        line = c.get("line")
        if not path:  # required by the GitHub API
            actions.append({"kind": "inline", "error": "missing path", "body": body})
            continue
        if line is None:  # an inline comment without a line is invalid for GitHub
            actions.append({"kind": "inline", "error": "missing line",
                            "path": path, "body": body})
            continue
        key = (path, line, normalize(body))
        if key in existing:
            actions.append({"kind": "inline", "duplicate_skipped": True,
                            "path": path, "line": line, "body": body})
            continue
        actions.append({
            "kind": "inline", "duplicate_skipped": False,
            "path": path, "line": line,
            "side": c.get("side", "RIGHT"), "commit_id": reviewed_sha, "body": body,
        })

    for r in findings.get("thread_replies", []):
        body_text = r.get("body")
        if not isinstance(body_text, str) or not body_text.strip():
            actions.append({"kind": "reply", "error": "missing or invalid body"})
            continue
        body = render_reply_body(r)
        in_reply_to = r.get("in_reply_to")
        if in_reply_to is None:  # required to target the thread
            actions.append({"kind": "reply", "error": "missing in_reply_to", "body": body})
            continue
        actions.append({"kind": "reply", "in_reply_to": in_reply_to, "body": body})

    # The summary doubles as the reviewed-sha anchor for the next round's delta.
    if findings.get("summary") or findings.get("comments") or findings.get("thread_replies"):
        actions.append({
            "kind": "summary",
            "body": render_summary_body(findings.get("summary", ""), reviewed_sha, round_n),
        })

    return {"pr_number": pr_number, "reviewed_sha": reviewed_sha,
            "round": round_n, "actions": actions}


def post_action(action: dict, pr_number: int):
    if action.get("error"):
        return f"skipped (error: {action['error']})"
    kind = action["kind"]
    if kind == "inline":
        if action.get("duplicate_skipped"):
            return "skipped (duplicate)"
        gh_write([
            "api", f"repos/:owner/:repo/pulls/{pr_number}/comments",
            "-f", f"body={action['body']}",
            "-f", f"commit_id={action['commit_id']}",
            "-f", f"path={action['path']}",
            "-F", f"line={action['line']}",
            "-f", f"side={action['side']}",
        ])
        return f"posted inline on {action['path']}:{action['line']}"
    if kind == "reply":
        gh_write([
            "api",
            f"repos/:owner/:repo/pulls/{pr_number}/comments/{action['in_reply_to']}/replies",
            "-f", f"body={action['body']}",
        ])
        return f"posted reply to #{action['in_reply_to']}"
    if kind == "summary":
        gh_write(["pr", "comment", str(pr_number), "--body", action["body"]])
        return "posted summary (sha anchor)"
    return f"unknown action: {kind}"


def main():
    parser = argparse.ArgumentParser(description="Post reviewer-role PR comments.")
    parser.add_argument("--input", required=True, help="Path to findings.json")
    parser.add_argument("--mode", choices=["preview", "post"], default="preview")
    args = parser.parse_args()

    try:
        with open(args.input, encoding="utf-8") as f:
            findings = json.load(f)

        meta = gh_json(["pr", "view", "--json", "number,headRefOid"])
        plan = build_plan(findings, meta["number"], meta["headRefOid"])

        if args.mode == "preview":
            print("=== PREVIEW — nothing has been posted ===\n")
            print(json.dumps(plan, indent=2, ensure_ascii=False))
            n_new = sum(1 for a in plan["actions"]
                        if a["kind"] == "inline" and not a.get("duplicate_skipped")
                        and not a.get("error"))
            n_dup = sum(1 for a in plan["actions"] if a.get("duplicate_skipped"))
            n_err = sum(1 for a in plan["actions"] if a.get("error"))
            n_reply = sum(1 for a in plan["actions"]
                          if a["kind"] == "reply" and not a.get("error"))
            print(f"\nWould post: {n_new} inline, {n_reply} replies, 1 summary "
                  f"({n_dup} duplicates skipped, {n_err} errors). "
                  f"Re-run with --mode post to send.")
            return

        results = [{"action": a["kind"], "result": post_action(a, plan["pr_number"])}
                   for a in plan["actions"]]
        print(json.dumps({"posted": True, "pr_number": plan["pr_number"],
                          "results": results}, indent=2, ensure_ascii=False))

    except subprocess.CalledProcessError as e:
        print(f"ERROR: gh call failed: {(e.stderr or '').strip()}", file=sys.stderr)
        sys.exit(1)
    except Exception as e:  # noqa: BLE001
        print(f"ERROR: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
