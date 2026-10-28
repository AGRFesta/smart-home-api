# /// script
# requires-python = ">=3.11"
# ///
"""Preview reviewer-role PR comments — READ-ONLY, never writes to GitHub.

Renders every comment with its injected markers and prints exactly what would be
posted. It only issues GET requests (reading existing PR comments for dedup).
Posting is a separate script, `post_review.py`, so this one cannot send whatever
the session does with it.

Usage:
    uv run preview_review.py --input findings.json
"""

import argparse
import os
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import review_lib as lib  # noqa: E402


def main():
    lib.force_utf8()

    parser = argparse.ArgumentParser(description="Preview reviewer-role PR comments (read-only).")
    parser.add_argument("--input", required=True, help="Path to findings.json")
    args = parser.parse_args()

    try:
        plan = lib.plan_for_input(args.input)

        print("=== PREVIEW — nothing has been posted ===\n")
        lib.dump_plan(plan)
        print(f"\nWould post: {lib.summarize(plan)}.")
        print("After human approval, run post_review.py --input <same file> to send.")

    except subprocess.CalledProcessError as e:
        print(f"ERROR: gh call failed: {(e.stderr or '').strip()}", file=sys.stderr)
        sys.exit(1)
    except Exception as e:  # noqa: BLE001
        print(f"ERROR: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
