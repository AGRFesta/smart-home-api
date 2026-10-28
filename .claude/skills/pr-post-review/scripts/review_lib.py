"""Shared logic for the reviewer-role PR tooling (imported, never run directly).

The reviewer model writes content only (a findings.json file); markers are
injected here, never composed by the model, so one can never be forgotten or
malformed. The schema itself lives in `.claude/lib/markers.py` and is documented
in docs/REVIEW_WORKFLOW.md.

Two thin entrypoints import this module, and the split — not the permission layer
— is what makes a preview unable to post:

    preview_review.py -- read-only: builds and prints the plan. No network writes.
    post_review.py    -- writes to GitHub, after the human has approved the preview.

Both are allow-listed; the gate is the reviewer session stopping at the preview
(docs/REVIEW_WORKFLOW.md § Tooling).

Input (findings.json):
    {
      "reviewed_sha": "<sha>",        # optional; defaults to the PR head (warns)
      "round": 1,                      # optional; defaults to 1
      "summary": "text",              # optional; posted as the SHA-anchor comment
      "comments": [                    # new inline findings
        {"path","line","side","severity","confidence","body"}
      ],
      "thread_replies": [              # replies to existing reviewer threads
        {"in_reply_to","verdict","body"}
      ]
    }

Findings are written the same way whatever their severity; this module decides
which become inline threads and which become non-blocking bullets on the summary
comment, so the floor cannot be forgotten (docs/REVIEW_WORKFLOW.md § Finding
routing).
"""

import json
import os
import re
import sys

sys.path.insert(0, os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "lib"))
from ghcli import fetch_all, force_utf8, gh_json, gh_write  # noqa: E402,F401
from markers import MARKER_RE, REVIEWER_HEADER, build_marker  # noqa: E402

ROLE = "reviewer"

SHA_RE = re.compile(r"[0-9a-f]{40}", re.IGNORECASE)  # full SHA-1 commit id
ANCHOR_RE = re.compile(r"reviewed-sha=([0-9a-f]{40})", re.IGNORECASE)  # anchor marker

SEVERITY_RANK = {"critical": 4, "high": 3, "medium": 2, "low": 1, "info": 0}
BLOCKING_FLOOR = SEVERITY_RANK["medium"]


def severity_rank(value) -> int:
    """Rank a severity string; anything unrecognised (or absent) is the floor."""
    return SEVERITY_RANK.get(str(value or "").strip().lower(), 0)


def nonblocking_reason(comment: dict, round_n: int):
    """Why this finding is non-blocking, or None if it belongs inline.

    The rule is owned by docs/REVIEW_WORKFLOW.md § Finding routing. Only the two
    parts a script can decide are applied here — the file's extension and the
    round. Whether a finding in a source file is about prose is a judgement the
    reviewer makes when it assigns the severity.
    """
    if severity_rank(comment.get("severity")) >= BLOCKING_FLOOR:
        return None
    if round_n >= 2:
        return "below medium on a re-review round"
    if str(comment.get("path") or "").lower().endswith(".md"):
        return "documentation finding below medium"
    return None


def render_nonblocking_section(items: list) -> str:
    """Render the non-blocking findings as a section of the summary comment."""
    if not items:
        return ""
    parts = [
        "### Non-blocking observations",
        "Reported for the record, and deliberately not threads: act on them in the "
        "current commit or leave them, there is nothing to answer and no later round "
        "will raise them again (`docs/REVIEW_WORKFLOW.md` § Finding routing).",
    ]
    for item in items:
        where = item.get("path") or "(no path)"
        if item.get("line") is not None:
            where += f":{item['line']}"
        meta = [item.get("severity") or "low"]
        if item.get("confidence"):
            meta.append(f"confidence: {item['confidence']}")
        parts.append(f"#### `{where}` — _{' · '.join(meta)}_")
        parts.append((item.get("body") or "").strip())
    return "\n\n".join(parts)


def render_inline_body(comment: dict, round_n: int) -> str:
    meta = []
    if comment.get("severity"):
        meta.append(f"severity: {comment['severity']}")
    if comment.get("confidence"):
        meta.append(f"confidence: {comment['confidence']}")
    header = REVIEWER_HEADER + (f" — _{' · '.join(meta)}_" if meta else "")
    marker = build_marker(ROLE, {"round": round_n})
    return f"{header}\n\n{comment['body'].strip()}\n\n{marker}"


def render_reply_body(reply: dict) -> str:
    marker = build_marker(ROLE, {"verdict": reply.get("verdict")})
    return f"{REVIEWER_HEADER}\n\n{reply['body'].strip()}\n\n{marker}"


def render_summary_body(text: str, sha: str, round_n: int, nonblocking: list = None) -> str:
    marker = build_marker(ROLE, {"reviewed-sha": sha, "round": round_n})
    intro = f"{REVIEWER_HEADER} — review summary (round {round_n}, sha `{sha}`)"
    parts = [intro]
    body = (text or "").strip()
    if body:
        parts.append(body)
    section = render_nonblocking_section(nonblocking or [])
    if section:
        parts.append(section)
    parts.append(marker)
    return "\n\n".join(parts)


def normalize(body: str) -> str:
    """Strip markers/header/whitespace so duplicate detection is content-based."""
    text = MARKER_RE.sub("", (body or "").replace("\r\n", "\n")).strip()
    if text.startswith(REVIEWER_HEADER):
        parts = text.split("\n\n", 1)
        if len(parts) > 1:
            text = parts[1]
    return re.sub(r"\s+", " ", text).strip().lower()


def existing_inline_keys(comments: list) -> set:
    """(path, line, normalized-body) of inline comments already on the PR."""
    keys = set()
    for c in comments:
        line = c.get("line") or c.get("original_line")
        keys.add((c.get("path", ""), line, normalize(c.get("body", ""))))
    return keys


def existing_reply_keys(comments: list) -> set:
    """(in_reply_to_id, normalized-body) of replies already on the PR."""
    keys = set()
    for c in comments:
        parent = c.get("in_reply_to_id")
        if parent is not None:
            keys.add((parent, normalize(c.get("body", ""))))
    return keys


def existing_anchor_shas(issue_comments: list) -> set:
    """Full SHAs already recorded by a reviewed-sha anchor comment."""
    shas = set()
    for c in issue_comments:
        for sha in ANCHOR_RE.findall(c.get("body", "") or ""):
            shas.add(sha.lower())
    return shas


def build_plan(findings: dict, pr_number: int, head_sha: str) -> dict:
    # Reject a missing/placeholder reviewed_sha — GitHub needs a real full commit
    # id; fall back to the PR head.
    reviewed_sha = findings.get("reviewed_sha") or head_sha
    sha_warning = None
    if not SHA_RE.fullmatch(str(reviewed_sha)):
        reviewed_sha = head_sha
    # The anchor drives the next round's `git diff <anchor>..HEAD`. If the model
    # did not pin the SHA it actually reviewed, we fall back to the *current* head
    # — which may be ahead of what was reviewed if the branch advanced meanwhile,
    # silently skipping unreviewed commits next round. Surface this loudly.
    if not findings.get("reviewed_sha"):
        sha_warning = (
            "reviewed_sha was not provided; defaulting to the current PR head "
            f"({head_sha[:7]}). If the branch advanced since you reviewed it, the "
            "delta anchor will over-advance and the next round will skip the "
            "unreviewed commits. Set reviewed_sha to the headRefOid you reviewed."
        )
    round_n = int(findings.get("round", 1))

    pr_comments = fetch_all(f"repos/:owner/:repo/pulls/{pr_number}/comments")
    issue_comments = fetch_all(f"repos/:owner/:repo/issues/{pr_number}/comments")
    existing = existing_inline_keys(pr_comments)
    existing_replies = existing_reply_keys(pr_comments)
    anchored = existing_anchor_shas(issue_comments)

    actions = []
    nonblocking = []

    for c in findings.get("comments", []):
        body_text = c.get("body")
        if not isinstance(body_text, str) or not body_text.strip():
            actions.append({"kind": "inline", "error": "missing or invalid body",
                            "path": c.get("path")})
            continue
        reason = nonblocking_reason(c, round_n)
        if reason:
            nonblocking.append({**c, "routed_because": reason})
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
        if (in_reply_to, normalize(body)) in existing_replies:
            actions.append({"kind": "reply", "duplicate_skipped": True,
                            "in_reply_to": in_reply_to, "body": body})
            continue
        actions.append({"kind": "reply", "duplicate_skipped": False,
                        "in_reply_to": in_reply_to, "body": body})

    # The summary doubles as the reviewed-sha anchor for the next round's delta.
    # Skip it if this SHA is already anchored (idempotent re-run / partial-failure
    # recovery) — otherwise every re-post spams a fresh anchor for the same commit.
    # ❗️ The non-blocking findings ride on this comment, so a skipped summary drops
    # them silently. On a recovery re-run of an already-anchored SHA, post them by
    # hand or re-run against a fresh anchor.
    if findings.get("summary") or findings.get("comments") or findings.get("thread_replies"):
        summary_action = {
            "kind": "summary",
            "body": render_summary_body(findings.get("summary", ""), reviewed_sha,
                                        round_n, nonblocking),
        }
        if reviewed_sha.lower() in anchored:
            summary_action["duplicate_skipped"] = True
        actions.append(summary_action)

    plan = {"pr_number": pr_number, "reviewed_sha": reviewed_sha,
            "round": round_n, "actions": actions,
            "nonblocking": [{"path": n.get("path"), "line": n.get("line"),
                             "severity": n.get("severity"),
                             "routed_because": n["routed_because"]}
                            for n in nonblocking]}
    if sha_warning:
        plan["warning"] = sha_warning
    return plan


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
        if action.get("duplicate_skipped"):
            return "skipped (duplicate)"
        gh_write([
            "api",
            f"repos/:owner/:repo/pulls/{pr_number}/comments/{action['in_reply_to']}/replies",
            "-f", f"body={action['body']}",
        ])
        return f"posted reply to #{action['in_reply_to']}"
    if kind == "summary":
        if action.get("duplicate_skipped"):
            return "skipped (duplicate anchor)"
        gh_write(["pr", "comment", str(pr_number), "--body", action["body"]])
        return "posted summary (sha anchor)"
    return f"unknown action: {kind}"


def load_findings(path: str) -> dict:
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def fetch_meta() -> dict:
    """PR number + current head SHA (the default anchor / inline commit_id)."""
    return gh_json(["pr", "view", "--json", "number,headRefOid"])


def plan_for_input(input_path: str) -> dict:
    """Read findings.json, fetch PR meta, and build the action plan."""
    findings = load_findings(input_path)
    meta = fetch_meta()
    return build_plan(findings, meta["number"], meta["headRefOid"])


def summarize(plan: dict) -> str:
    """One-line count of what the plan would post."""
    actions = plan["actions"]
    n_new = sum(1 for a in actions
                if a["kind"] == "inline" and not a.get("duplicate_skipped")
                and not a.get("error"))
    n_dup = sum(1 for a in actions if a.get("duplicate_skipped"))
    n_err = sum(1 for a in actions if a.get("error"))
    n_reply = sum(1 for a in actions
                  if a["kind"] == "reply" and not a.get("error")
                  and not a.get("duplicate_skipped"))
    n_summary = sum(1 for a in actions
                    if a["kind"] == "summary" and not a.get("duplicate_skipped"))
    n_nonblocking = len(plan.get("nonblocking", []))
    return (f"{n_new} inline, {n_nonblocking} non-blocking, {n_reply} replies, "
            f"{n_summary} summary ({n_dup} duplicates skipped, {n_err} errors)")


def dump_plan(plan: dict):
    """Print the warning (if any) and the full plan JSON — the exact content that
    would be / is being posted."""
    if plan.get("warning"):
        print(f"WARNING: {plan['warning']}\n")
    print(json.dumps(plan, indent=2, ensure_ascii=False))
