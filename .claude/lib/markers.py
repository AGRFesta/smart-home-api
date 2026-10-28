"""The smarthome marker schema — one definition, read side and write side.

Markers carry role attribution on every automated PR comment. The schema itself
is documented in docs/REVIEW_WORKFLOW.md § Marker Schema; this module is where it
is rendered and parsed.

❗️ Both skills import this module and neither owns it — that is why it lives
outside `.claude/skills/`. A constant copied back into a skill re-creates the two
definitions § Identity & attribution names as the cost of the whole scheme, and
they diverge without anything failing.
"""

import re

ROLES = ("reviewer", "author")
DECISIONS = ("accepted", "rejected")

REVIEWER_HEADER = "🤖 **Reviewer**"
AUTHOR_HEADER = "🤖 **Author**"
HEADERS = {"reviewer": REVIEWER_HEADER, "author": AUTHOR_HEADER}

# One marker block; captures its "key=value key=value" payload.
MARKER_RE = re.compile(r"<!--\s*smarthome:(.*?)-->", re.DOTALL)
# A single key=value token inside a payload.
PAIR_RE = re.compile(r"(\w[\w-]*)=([^\s]+)")


def build_marker(role: str, pairs: dict) -> str:
    """Render a smarthome marker; `role` is required and always rendered first."""
    if role not in ROLES:
        raise ValueError(f"unknown role {role!r}; expected one of {ROLES}")
    ordered = {"role": role}
    for key, value in pairs.items():
        if value is not None and value != "":
            ordered[key] = value
    body = " ".join(f"{k}={v}" for k, v in ordered.items())
    return f"<!-- smarthome:{body} -->"


def header_for(role: str) -> str:
    """The visible header that goes with a role."""
    if role not in ROLES:
        raise ValueError(f"unknown role {role!r}; expected one of {ROLES}")
    return HEADERS[role]


def parse_markers(body: str) -> dict:
    """Extract and merge every smarthome marker found in a comment body."""
    markers: dict = {}
    if not body:
        return markers
    for payload in MARKER_RE.findall(body):
        for key, value in PAIR_RE.findall(payload):
            markers[key] = value
    return markers


def strip_markers(body: str) -> str:
    """The human-readable body, with the markers removed."""
    if not body:
        return ""
    return MARKER_RE.sub("", body).strip()


def classify_role(markers: dict) -> str:
    """Map a marker set to a role; anything unrecognised is a human comment.

    ❗️ A misspelt role reads exactly like an absent one, and the fallback turns
    both into `human` — an automated post promoted into the human's column, which
    the next reviewer round then treats as instruction. This is why the write side
    validates instead of trusting a string: nothing downstream can tell the two
    apart.
    """
    role = markers.get("role")
    return role if role in ROLES else "human"
