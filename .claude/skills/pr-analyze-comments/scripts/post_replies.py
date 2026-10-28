# /// script
# requires-python = ">=3.11"
# ///
"""Post author-role replies to a Pull Request — THIS WRITES TO GITHUB.

Human gate: preview with the read-only `preview_replies.py`, present the plan, and
post only once the human has approved it. The separate entry point is what keeps a
preview from posting; the permission layer will not stop the write.

A thread that already carries an author reply after the last reviewer message is
skipped, so re-running the same input after a partial failure sends only what is
missing. Threads are left open — closure is the reviewer's authority.

Usage:
    uv run post_replies.py --input replies.json
"""

import argparse
import json
import os
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import reply_lib as lib  # noqa: E402


def main():
    lib.force_utf8()

    parser = argparse.ArgumentParser(description="Post author-role PR replies (writes).")
    parser.add_argument("--input", required=True, help="Path to replies.json")
    args = parser.parse_args()

    try:
        plan = lib.plan_for_input(args.input)

        print("=== POSTING to GitHub ===\n")
        lib.dump_plan(plan)
        print(f"\nPosting: {lib.summarize(plan)}\n")

        results = [{"comment_id": a.get("comment_id"),
                    "result": lib.post_action(a, plan["pr_number"])}
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
