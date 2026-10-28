# /// script
# requires-python = ">=3.11"
# ///
"""Assertions on the guard. Run by hand: `uv run .claude/hooks/test_guard.py`.

Every acceptance criterion of the issue that added the hook has an assertion
here. The two halves matter equally: a command the guard fails to refuse is the
damage the hook exists to stop, and a legitimate command it refuses teaches
whoever hits it to route around the guard.
"""

import io
import json
import os
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from guard import main, refusal  # noqa: E402

# Derived, not written out: the assertion is about the session's scratchpad
# wherever it is, not about one machine's layout.
SCRATCHPAD = os.path.join(
    tempfile.gettempdir(), "claude", "session", "scratchpad", "notes.md"
)


def refuses(command: str, *naming: str, tool: str = "Bash", cwd: str = None):
    reason = refusal(command, tool, cwd)
    assert reason, f"allowed: {command}"
    for word in naming:
        assert word in reason, f"reason for {command!r} does not name {word!r}"


def allows(command: str, tool: str = "Bash", cwd: str = None):
    assert not refusal(command, tool, cwd), f"refused: {command}"


def run_hook(tool_name: str, command: str, cwd: str = None):
    """The hook end to end: payload on stdin, exit code and stderr out."""
    stdin, stderr = sys.stdin, sys.stderr
    payload = {"tool_name": tool_name, "tool_input": {"command": command}}
    if cwd:
        payload["cwd"] = cwd
    sys.stdin = io.StringIO(json.dumps(payload))
    sys.stderr = io.StringIO()
    try:
        return main(), sys.stderr.getvalue()
    finally:
        sys.stdin, sys.stderr = stdin, stderr


def test_a_stream_editor_is_refused():
    for command in (
        "sed -i 's/a/b/' core/src/main/kotlin/Money.kt",
        "sed --in-place 's/a/b/' CLAUDE.md",
        "sed -i.bak 's/a/b/' docs/RUNTIME.md",
        "perl -pi -e 's/a/b/' build.gradle.kts",
        "perl -i -e 's/a/b/' build.gradle.kts",
        "/usr/bin/sed -i 's/a/b/' CLAUDE.md",
    ):
        refuses(command, "Edit")


def test_the_reason_names_both_tools():
    refuses("sed -i 's/a/b/' CLAUDE.md", "Edit", "Write")


def test_a_substitution_across_several_files_is_refused_like_any_other():
    # The exception CLAUDE.md used to carry: one substitution, many files,
    # matching \r\n rather than \n. The hook cannot see which files the harness
    # holds open, so it refuses this the way it refuses everything else.
    refuses(
        r"sed -i 's/\r\n/\n/' core/src/main/kotlin/A.kt docs/B.md app/C.yml",
        "Edit",
    )


def test_a_stream_editor_reached_by_its_path_is_refused():
    # `shlex` in POSIX mode eats the backslashes and leaves a token that no
    # longer looks like a path to `sed`. This repository is developed on Windows.
    refuses(r"C:\tools\sed.exe -i 's/a/b/' CLAUDE.md", "Edit")
    refuses(r"C:\Strawberry\perl\bin\perl.exe -pi -e 's/a/b/' CLAUDE.md", "Edit")
    refuses(r'"C:\Program Files\git\usr\bin\sed.exe" -i 1d CLAUDE.md', "Edit")


def test_a_stream_editor_run_by_another_command_is_refused():
    refuses("find . -name '*.kt' -exec sed -i 's/a/b/' {} ;", "Edit")
    refuses("git ls-files '*.md' | xargs sed -i 's/a/b/'", "Edit")
    refuses("sudo perl -pi -e 's/a/b/' CLAUDE.md", "Edit")
    refuses("LC_ALL=C sed -i 's/a/b/' CLAUDE.md", "Edit")


def test_a_stream_editor_after_a_shell_keyword_is_refused():
    # A loop over files is the usual way to write a substitution across several
    # of them, and the segment it runs in starts with `do`.
    refuses("for f in core/*.kt; do sed -i 's/a/b/' \"$f\"; done", "Edit")
    refuses("if true; then sed -i 's/a/b/' CLAUDE.md; fi", "Edit")
    refuses("(cd docs && sed -i 's/a/b/' RUNTIME.md)", "Edit")


def test_an_introducer_is_read_past_its_own_options():
    refuses("git ls-files -z '*.kt' | xargs -0 sed -i 's/a/b/'", "Edit")
    refuses("git ls-files '*.md' | xargs -r sed -i 's/a/b/'", "Edit")
    refuses("git ls-files '*.md' | xargs -I {} sed -i 's/a/b/' {}", "Edit")
    refuses("sudo -u root sed -i 's/a/b/' CLAUDE.md", "Edit")
    refuses("env -u LANG sed -i 's/a/b/' CLAUDE.md", "Edit")
    refuses("nice -n 5 sed -i 's/a/b/' CLAUDE.md", "Edit")
    # `timeout` takes a duration before the command word.
    refuses("timeout 10 sed -i 's/a/b/' CLAUDE.md", "Edit")
    refuses("timeout -s KILL 10 sed -i 's/a/b/' CLAUDE.md", "Edit")


def test_an_in_place_flag_with_a_record_separator_or_quotes_is_refused():
    refuses("perl -0pi -e 's/a/b/' CLAUDE.md", "Edit")
    refuses("perl -0777 -pi -e 's/a/b/' CLAUDE.md", "Edit")
    refuses("sed -i'' 's/a/b/' CLAUDE.md", "Edit")


def test_quoted_text_and_heredoc_bodies_are_not_commands():
    # Commit messages, PR bodies and issue text in this repository discuss the
    # refused commands by name.
    allows('git commit -m "Refuse a redirect > CLAUDE.md from the shell"')
    allows("git commit -F - <<'EOF'\nsed -i on a source file is now refused.\nEOF")
    allows(
        "gh pr create --body \"$(cat <<'EOF'\n"
        "docker compose start is refused\nEOF\n)\""
    )
    allows("git commit -m @'\ndocker compose start is refused; sed -i too\n'@")
    # The line that opens a heredoc is a command, and its redirect is a write.
    refuses("cat > CLAUDE.md <<'EOF'\nx\nEOF", "Edit")


def test_a_comment_or_an_escaped_quote_opens_no_quote():
    # An apostrophe in a comment or after an escape is a character, not the start
    # of a quote that would hide every later separator.
    refuses("# don't touch the tests\nsed -i 's/a/b/' CLAUDE.md", "Edit")
    refuses("echo don\\'t; sed -i 's/a/b/' CLAUDE.md", "Edit")
    allows("# sed -i CLAUDE.md")
    allows("git log --grep 'issue#171'")
    # PowerShell escapes with a backtick, so a backslash closes nothing.
    refuses("Get-ChildItem \"C:\\temp\\\"; Set-Content CLAUDE.md 'x'", "Edit", tool="PowerShell")
    refuses("echo \"it`\"s\"; Set-Content CLAUDE.md 'x'", "Edit", tool="PowerShell")


def test_a_continued_line_is_one_command():
    refuses("sed -i \\\n  's/a/b/' CLAUDE.md", "Edit")


def test_the_word_sed_is_not_the_command_sed():
    # A search for the word, not a run of the program. Refusing this is the false
    # positive that teaches whoever hits it to route around the guard.
    allows("grep -rn sed -i docs/")
    allows("grep -c 'perl -pi' CLAUDE.md")
    allows("echo 'never run sed -i here' >> /dev/null")


def test_a_perl_cluster_that_carries_a_value_is_not_an_in_place_flag():
    allows("perl -MList::Util -e 'print 1'")
    allows("perl -Mstrict -e 'print 1'")
    allows("perl -Ilib -e 'print 1'")
    allows("perl -e 'print 1'")


def test_a_bsd_style_in_place_flag_is_refused():
    refuses("sed -I '' 's/a/b/' CLAUDE.md", "Edit")


def test_tee_is_a_write_like_any_other():
    refuses("echo x | tee core/src/main/kotlin/Money.kt", "Edit")
    refuses("echo x | tee -a CLAUDE.md", "Edit")
    allows("./gradlew build | tee /tmp/build.log")
    refuses("./gradlew build | tee build.log", "mktemp")


def test_a_redirect_onto_a_source_file_is_refused():
    for command in (
        "echo 'package x' > core/src/main/kotlin/Money.kt",
        "cat template.txt >> docs/RUNTIME.md",
        "printf 'a' > persistence/src/main/resources/db/migration/V2.sql",
        "echo x > docker-compose.yml",
        "echo x > gradle.properties",
        "echo x > bruno/payslips/create.bru",
    ):
        refuses(command, "Edit")


def test_every_file_kind_the_repository_tracks_is_covered():
    # CLAUDE.md promises the hook refuses a redirect onto a source file. The set
    # is the extensions `git ls-files` reports, plus the files tracked without
    # one. `gradlew` is the sharpest: `.gitattributes` pins its end-of-line, so a
    # redirect onto it is exactly the corruption the hook exists to refuse.
    for command in (
        "echo x > gradlew",
        "echo x > gradle/libs.versions.toml",
        "echo x > .claude/settings.json",
        "echo x > .gitignore",
        "echo x > .gitattributes",
        "echo x > .env.example",
        "echo x > app/src/main/resources/logback.xml",
        "echo x > .claude/hooks/guard.py",
    ):
        refuses(command, "Edit")


def test_a_redirect_that_leaves_the_working_tree_alone_is_allowed():
    allows("./gradlew build 2>/dev/null")
    allows("docker compose --env-file .env.dev logs > /dev/null")
    allows("git status 2>$null")
    allows(f"git diff > {SCRATCHPAD}")
    allows("git log --oneline > /tmp/history.md")
    # Git Bash exports both variables.
    allows("cat > $TMP/probe.py <<'X'\nprint(1)\nX")
    allows("git diff > ${TEMP}/changes.md")


def test_a_file_left_in_the_working_tree_is_refused():
    # Not ignored, it can land in the user's next commit. The Definition of
    # Done agent once left these two at the root.
    refuses("./gradlew build --console=plain > build_output.log 2>&1", "mktemp", "build_output.log")
    refuses("./gradlew detekt > detekt_output.log", "mktemp")
    refuses("git diff > changes.patch", "mktemp")
    refuses("./gradlew build | tee -a logs/gradle.txt", "mktemp")
    refuses("Out-File -FilePath findings.txt", "mktemp", tool="PowerShell")
    root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    refuses(f"echo x > {os.path.join(root, 'notes.txt')}", "mktemp")
    # The same root as Git Bash spells it.
    drive, rest = os.path.splitdrive(root)
    if drive:
        refuses(f"echo x > /{drive[0].lower()}{rest.replace(os.sep, '/')}/notes.txt", "mktemp")


def test_a_file_written_where_git_ignores_it_is_allowed():
    allows("./gradlew build > build/build.log 2>&1")
    allows("./gradlew build > core/build/build.log")
    allows("echo x > .gradle/probe.txt")


REPOSITORY = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


def test_a_relative_target_is_read_from_the_working_directory_of_the_shell():
    # From `docs/`, `../notes.txt` lands at the root of the repository.
    refuses("echo x > ../notes.txt", "mktemp", "notes.txt", cwd=os.path.join(REPOSITORY, "docs"))
    refuses("echo x > notes.txt", "docs/notes.txt", cwd=os.path.join(REPOSITORY, "docs"))
    # From a directory outside the repository, the file lands outside it, a
    # source extension included.
    outside = os.path.dirname(SCRATCHPAD)
    allows("uv run preview_review.py --input x > findings.json", cwd=outside)
    allows("echo x > notes.txt", cwd=outside)
    # Without a working directory in the payload, the root of the repository.
    refuses("echo x > notes.txt", "mktemp")


def test_the_hook_reads_the_working_directory_from_the_payload():
    code, _ = run_hook("Bash", "echo x > findings.json", cwd=os.path.dirname(SCRATCHPAD))
    assert code == 0, f"exit {code}, expected 0"
    code, reason = run_hook("Bash", "echo x > ../notes.txt", cwd=os.path.join(REPOSITORY, "docs"))
    assert code == 2 and "notes.txt" in reason, f"exit {code}, stderr {reason!r}"


def test_the_refusal_does_not_call_a_tracked_file_untracked():
    # The tracked wrapper jar has no source extension, and it is not new.
    reason = refusal("echo x > gradle/wrapper/gradle-wrapper.jar", "Bash")
    assert reason and "untracked" not in reason, reason


def test_a_target_the_guard_cannot_place_is_allowed():
    # `mktemp` chose the path: the hook sees the variable, not the file.
    allows('log=$(mktemp); ./gradlew build --console=plain > "$log" 2>&1; echo "exit=$?"')
    allows("echo x > ~/notes.txt")
    allows("echo x > ../elsewhere.txt")
    allows("./gradlew build 2>&1 | grep FAILED")


def test_the_read_only_tools_are_allowed():
    allows("sed -n '1,40p' CLAUDE.md")
    allows("grep -n 'Money' core/src/main/kotlin/Money.kt")
    allows("grep -i money docs/ARCHITECTURE.md")
    allows("find . -name '*.kt'")
    allows("od -c CLAUDE.md | head -3")
    allows("head -20 docs/RUNTIME.md")
    allows("tail -5 CHANGELOG.md")


def test_powershell_commands_are_refused_too():
    for command in (
        "Set-Content -Path CLAUDE.md -Value 'x'",
        "'x' | Out-File docs/RUNTIME.md",
        "Add-Content gradle.properties 'version=1'",
        "echo x > core/src/main/kotlin/Money.kt",
    ):
        refuses(command)


def test_a_powershell_writer_is_judged_on_what_it_writes():
    # It writes `notes.txt` and reads `CLAUDE.md`. Taking every token as a target
    # refuses a command that changes nothing in the working tree.
    allows("Set-Content -Path $env:TEMP/notes.txt -Value (Get-Content CLAUDE.md -Raw)")
    allows("Get-Content CLAUDE.md | Out-File $env:TEMP/claude/copy.md")
    refuses("Set-Content -LiteralPath docs/RUNTIME.md -Value 'x'", "Edit")


def test_a_powershell_writer_is_read_past_its_value_parameters():
    # After `-Encoding`, the next token is the encoding, not the file.
    refuses("$c | Set-Content -Encoding utf8 CLAUDE.md", "Edit")
    refuses("Set-Content -NoNewline -Encoding utf8 docs/RUNTIME.md -Value $c", "Edit")
    refuses("'x' | Out-File -Encoding utf8 docs/RUNTIME.md", "Edit")
    refuses("Set-Content -Value 'x' CLAUDE.md", "Edit")
    refuses("Set-Content -Enc utf8 CLAUDE.md -Value 'x'", "Edit")


def test_the_hook_blocks_on_both_tools():
    for tool in ("Bash", "PowerShell"):
        code, reason = run_hook(tool, "sed -i 's/a/b/' CLAUDE.md")
        assert code == 2, f"{tool}: exit {code}, expected 2"
        assert "Edit" in reason, f"{tool}: reason does not name the tool to use"


def test_the_hook_lets_an_ordinary_command_through():
    for tool in ("Bash", "PowerShell"):
        code, reason = run_hook(tool, "./gradlew test")
        assert code == 0 and reason == "", f"{tool}: exit {code}, stderr {reason!r}"


def test_no_token_opens_the_guard():
    # Whatever a command line carries, it is read as a command line. Nothing in
    # it is a key.
    for marker in (
        "# hook-allow",
        "# noqa",
        "--force",
        "# guard: skip",
        "# the user asked for this",
        "# CLAUDE_HOOK_BYPASS=1",
    ):
        refuses(f"sed -i 's/a/b/' CLAUDE.md {marker}")
        refuses(f"echo x > notes.txt {marker}")


def test_a_compound_command_is_read_segment_by_segment():
    refuses("./gradlew build && sed -i 's/a/b/' CLAUDE.md")
    refuses("git status; echo x > CLAUDE.md")
    allows("./gradlew build && git status")


def test_a_guard_that_cannot_judge_a_command_refuses_it():
    # A command was read and the guard failed on it. Letting it through would
    # run a command nothing checked.
    stdin, stderr = sys.stdin, sys.stderr
    sys.stdin = io.StringIO(json.dumps({"tool_input": {"command": {"not": "a string"}}}))
    sys.stderr = io.StringIO()
    try:
        code, reason = main(), sys.stderr.getvalue()
    finally:
        sys.stdin, sys.stderr = stdin, stderr
    assert code == 2, f"exit {code}, expected 2"
    assert "guard.py" in reason, "the reason does not name the file to fix"


def test_an_unreadable_payload_does_not_block():
    stdin = sys.stdin
    sys.stdin = io.StringIO("not json")
    try:
        assert main() == 0
    finally:
        sys.stdin = stdin


if __name__ == "__main__":
    sys.stderr.reconfigure(encoding="utf-8")
    sys.stdout.reconfigure(encoding="utf-8")
    tests = [v for k, v in sorted(globals().items()) if k.startswith("test_")]
    for test in tests:
        test()
    print(f"OK - {len(tests)} guard assertions passed")
