"""Shared logic for the author-role PR tooling (imported, never run directly).

The author model writes content only (a replies.json file); the marker is injected
here, from `.claude/lib/markers.py`, never composed by the model. The schema is
documented in docs/REVIEW_WORKFLOW.md.

Two thin entrypoints import this module, and the split — not the permission layer
— is what keeps a preview from posting:

    preview_replies.py -- read-only: builds and prints the plan. No network writes.
    post_replies.py    -- writes to GitHub, after the human has approved the preview.

Input (replies.json):
    {
      "replies": [
        {"comment_id": 123456789, "decision": "accepted", "body": "text"}
      ]
    }

`comment_id` is any message id in the thread being answered (`get_pr_context.py`
reports the thread root as `thread_id` and each message under `messages[].id`);
it is resolved to its thread before anything is decided.

❗️ Nothing here can emit a `verdict`. Closure is the reviewer's authority
(docs/REVIEW_WORKFLOW.md § Thread closure authority), and the guarantee is
structural rather than a check: the renderer builds its marker from the decision
alone, and every other key in the input is ignored.
"""

import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "lib"))
from ghcli import fetch_all, force_utf8, gh_json, gh_write  # noqa: E402,F401
from markers import AUTHOR_HEADER, DECISIONS, build_marker  # noqa: E402
from get_pr_context import build_inline_threads, inline_comments_from  # noqa: E402

ROLE = "author"


def render_body(decision: str, body: str) -> str:
    marker = build_marker(ROLE, {"decision": decision})
    return f"{AUTHOR_HEADER}\n\n{body.strip()}\n\n{marker}"


def already_answered(thread: dict) -> bool:
    """True when an author reply follows the last reviewer message in the thread.

    This, and not the text of the reply, is what makes a re-run safe: a body
    re-drafted between two runs is still an answer to a thread that already has
    one, and content-based dedup would post it twice.
    """
    messages = thread.get("messages", [])
    last_reviewer = -1
    for i, msg in enumerate(messages):
        if msg["role"] == "reviewer":
            last_reviewer = i
    return any(m["role"] == ROLE for m in messages[last_reviewer + 1:])


def threads_by_message_id(pr_number: int) -> dict:
    """Every message id on the PR mapped to the thread it belongs to."""
    inline_raw = fetch_all(f"repos/:owner/:repo/pulls/{pr_number}/comments")
    threads = build_inline_threads(inline_comments_from(inline_raw))
    index = {}
    for thread in threads:
        for msg in thread["messages"]:
            index[msg["id"]] = thread
    return index


def build_plan(spec: dict, pr_number: int) -> dict:
    index = threads_by_message_id(pr_number)
    actions = []

    for reply in spec.get("replies", []):
        comment_id = reply.get("comment_id")
        decision = str(reply.get("decision") or "").strip().lower()
        body_text = reply.get("body")
        action = {"comment_id": comment_id, "decision": decision}

        if decision not in DECISIONS:
            action["error"] = f"decision must be one of {' | '.join(DECISIONS)}"
            actions.append(action)
            continue
        if not isinstance(body_text, str) or not body_text.strip():
            action["error"] = "missing or invalid body"
            actions.append(action)
            continue

        thread = index.get(comment_id)
        if thread is None:
            action["error"] = "comment_id is not an inline comment on this PR"
            actions.append(action)
            continue

        action["thread_id"] = thread["thread_id"]
        action["file"] = thread.get("file")
        action["line"] = thread.get("line")
        action["body"] = render_body(decision, body_text)
        # The reply targets the thread root, so two ids from the same thread
        # cannot produce two GitHub threads.
        action["in_reply_to"] = thread["thread_id"]
        action["already_answered"] = already_answered(thread)
        actions.append(action)

    return {"pr_number": pr_number, "actions": actions}


def post_action(action: dict, pr_number: int) -> str:
    if action.get("error"):
        return f"skipped (error: {action['error']})"
    if action.get("already_answered"):
        return "skipped (thread already answered)"
    gh_write([
        "api",
        f"repos/:owner/:repo/pulls/{pr_number}/comments/{action['in_reply_to']}/replies",
        "-f", f"body={action['body']}",
    ])
    return f"posted {action['decision']} reply to #{action['in_reply_to']}"


def load_spec(path: str) -> dict:
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def plan_for_input(input_path: str) -> dict:
    """Read replies.json, fetch the PR's threads, and build the action plan."""
    spec = load_spec(input_path)
    meta = gh_json(["pr", "view", "--json", "number"])
    return build_plan(spec, meta["number"])


def summarize(plan: dict) -> str:
    actions = plan["actions"]
    n_post = sum(1 for a in actions
                 if not a.get("error") and not a.get("already_answered"))
    n_skip = sum(1 for a in actions if a.get("already_answered"))
    n_err = sum(1 for a in actions if a.get("error"))
    return f"{n_post} replies ({n_skip} already answered, {n_err} errors)"


def dump_plan(plan: dict):
    """Print the full plan JSON — the exact content that would be / is being posted."""
    print(json.dumps(plan, indent=2, ensure_ascii=False))
