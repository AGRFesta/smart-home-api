"""`gh` invocation shared by both review skills (imported, never run directly).

Kept beside markers.py for the same reason: the reviewer and the author tooling
would otherwise reach into each other's directory for it.
"""

import json
import subprocess
import sys


def force_utf8():
    """The tooling emits emoji/em-dash; force UTF-8 so Windows consoles defaulting
    to cp1252 (charmap codec) don't crash."""
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")


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


def fetch_all(endpoint: str) -> list:
    """GET every page of a list endpoint.

    `gh api` caps at 30 items/page by default and does NOT paginate unless asked;
    without this, dedup and anchor detection silently miss anything past the first
    page on a PR with a long review history.
    """
    return gh_json(["api", "--paginate", f"{endpoint}?per_page=100"])
