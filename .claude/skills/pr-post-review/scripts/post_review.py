# /// script
# requires-python = ">=3.11"
# ///
"""Post reviewer-role comments to a Pull Request — THIS WRITES TO GITHUB.

Human gate: preview with the read-only `preview_review.py`, present the plan, and
post only once the human has approved it. The script is allow-listed, so nothing
in the permission layer stops the write — the separate entry point is what keeps a
preview from posting (docs/REVIEW_WORKFLOW.md § Tooling). All shared logic lives in
`review_lib.py`, and the marker schema in `.claude/lib/markers.py`.

Usage:
    uv run post_review.py --input findings.json
"""

import argparse
import json
import os
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import review_lib as lib  # noqa: E402


def main():
    lib.force_utf8()

    parser = argparse.ArgumentParser(description="Post reviewer-role PR comments (writes).")
    parser.add_argument("--input", required=True, help="Path to findings.json")
    args = parser.parse_args()

    try:
        plan = lib.plan_for_input(args.input)

        # Echo exactly what is about to be posted, then post.
        print("=== POSTING to GitHub ===\n")
        lib.dump_plan(plan)
        print(f"\nPosting: {lib.summarize(plan)}\n")

        results = [{"action": a["kind"], "result": lib.post_action(a, plan["pr_number"])}
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
