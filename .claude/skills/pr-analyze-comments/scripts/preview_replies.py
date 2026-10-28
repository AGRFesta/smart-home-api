# /// script
# requires-python = ">=3.11"
# ///
"""Preview author-role PR replies — READ-ONLY, never writes to GitHub.

Renders every reply with its injected marker and prints exactly what would be
posted, including which threads are skipped because they already carry an author
reply. It only issues GET requests. Posting is a separate script,
`post_replies.py`, so this one cannot send.

Usage:
    uv run preview_replies.py --input replies.json
"""

import argparse
import os
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import reply_lib as lib  # noqa: E402


def main():
    lib.force_utf8()

    parser = argparse.ArgumentParser(description="Preview author-role PR replies (read-only).")
    parser.add_argument("--input", required=True, help="Path to replies.json")
    args = parser.parse_args()

    try:
        plan = lib.plan_for_input(args.input)

        print("=== PREVIEW — nothing has been posted ===\n")
        lib.dump_plan(plan)
        print(f"\nWould post: {lib.summarize(plan)}.")
        print("After human approval, run post_replies.py --input <same file> to send.")

    except subprocess.CalledProcessError as e:
        print(f"ERROR: gh call failed: {(e.stderr or '').strip()}", file=sys.stderr)
        sys.exit(1)
    except Exception as e:  # noqa: BLE001
        print(f"ERROR: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
