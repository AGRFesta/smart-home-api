# /// script
# requires-python = ">=3.11"
# ///
"""Assertions on the marker schema. Run by hand: `uv run .claude/lib/test_markers.py`.

The reviewer strings below are the ones the tooling posted before the schema moved
into a shared module and `role` became an argument. They are frozen on purpose:
they are what makes "the reviewer's output is unchanged" a claim that can be run
rather than one that has to be read.
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ghcli import force_utf8  # noqa: E402
from markers import (AUTHOR_HEADER, REVIEWER_HEADER, build_marker,  # noqa: E402
                     classify_role, header_for, parse_markers, strip_markers)

SHA = "0123456789abcdef0123456789abcdef01234567"


def test_reviewer_output_is_unchanged():
    assert build_marker("reviewer", {"round": 1}) == \
        "<!-- smarthome:role=reviewer round=1 -->"
    assert build_marker("reviewer", {"verdict": "resolved"}) == \
        "<!-- smarthome:role=reviewer verdict=resolved -->"
    assert build_marker("reviewer", {"reviewed-sha": SHA, "round": 2}) == \
        f"<!-- smarthome:role=reviewer reviewed-sha={SHA} round=2 -->"


def test_author_marker():
    assert build_marker("author", {"decision": "accepted"}) == \
        "<!-- smarthome:role=author decision=accepted -->"
    assert build_marker("author", {"decision": "rejected"}) == \
        "<!-- smarthome:role=author decision=rejected -->"


def test_role_is_rendered_first():
    marker = build_marker("author", {"decision": "accepted", "round": 3})
    assert marker.index("role=") < marker.index("decision=")


def test_unknown_role_is_refused():
    for role in ("autor", "Reviewer", "human", "", None):
        try:
            build_marker(role, {"round": 1})
        except ValueError:
            continue
        raise AssertionError(f"build_marker accepted role {role!r}")


def test_empty_values_are_dropped():
    assert build_marker("reviewer", {"verdict": None, "round": ""}) == \
        "<!-- smarthome:role=reviewer -->"


def test_headers():
    assert header_for("reviewer") == REVIEWER_HEADER
    assert header_for("author") == AUTHOR_HEADER


def test_round_trip():
    for role, pairs in (("reviewer", {"round": 2}), ("author", {"decision": "rejected"})):
        markers = parse_markers(build_marker(role, pairs))
        assert classify_role(markers) == role
        assert all(markers[k] == str(v) for k, v in pairs.items())


def test_unrecognised_role_reads_as_human():
    # The failure mode the write-side validation exists to prevent: on the read
    # side a typo is indistinguishable from no marker at all.
    assert classify_role(parse_markers("<!-- smarthome:role=autor -->")) == "human"
    assert classify_role(parse_markers("no marker here")) == "human"


def test_strip_markers():
    body = f"The finding text.\n\n{build_marker('reviewer', {'round': 1})}"
    assert strip_markers(body) == "The finding text."


if __name__ == "__main__":
    force_utf8()
    tests = [v for k, v in sorted(globals().items()) if k.startswith("test_")]
    for test in tests:
        test()
    print(f"OK — {len(tests)} marker assertions passed")
