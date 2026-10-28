r"""Refuse the shell commands that damage this repository without reporting it.

Claude Code runs this as a `PreToolUse` hook on `Bash` and `PowerShell`
(`.claude/settings.json`). It reads the hook payload on stdin, takes
`tool_input.command`, and exits 2 with a reason on stderr when the command
belongs to one of the two families below. Exit 2 is what stops the call; the
reason is what the agent reads, so every reason names the command to use
instead.

**A stream editor writing into the working tree** — `sed` or `perl` with an
in-place flag — and a write onto a working-tree file through a `>` or `>>`
redirect, through `tee`, or through PowerShell's `Set-Content`, `Out-File` and
`Add-Content`. `CLAUDE.md` § Editing files owns why: such a command rewrites the
line endings and the non-ASCII characters, and nothing reports either.

**A write onto a path in the working tree that git does not ignore** — the same
redirects and writers, such as a `build_output.log` left at the root. Git does
not ignore the file, and the user, who runs the commit, can include it without
seeing it. A log or a scratch file goes in a temporary directory, or under an
ignored directory such as `build/`. A relative path is read from the working
directory of the shell, which the payload carries as `cwd`, and from the root of
the repository when it carries none. A `cd` earlier on the same command line is
not followed.

❗️ **There is no opt-out token, and adding one restores the problem.** A bypass
is only as good as the judgement of whoever types it, and prose asking for that
judgement turn after turn is what this hook replaces.

❗️ **A command word is recognised by position, not by appearance anywhere on the
line.** `grep -rn sed -i docs/` searches for the word `sed`; it does not run one.
The command word is the first token of a segment past any `VAR=value` and any
shell keyword (`do`, `then`, `!`, `{`), and the first token past the options of
`xargs`, `sudo`, `env`, `nice`, `timeout` or `find -exec` when one of those is
the command word. Segments are split on `|`, `&&`, `||`, `;` and newlines
outside quotes and comments, and each is judged on its own. Text inside quotes,
a `#` comment and the body of a heredoc are never read as commands, so a commit
message that quotes a refused command is allowed. The escape character is a
backslash in Bash and a backtick in PowerShell.

What the hook does not refuse: the read-only tools — `sed -n`, `grep`, `find`,
`od`, `head`, `tail` — a redirect to `/dev/null`, to `$null` or into a temporary
directory, a write under a directory git ignores, a write outside the
repository. A refusal that fires on a
legitimate command teaches whoever hits it to route around the guard, so a
target the hook cannot place — a variable, `~` — is let through.

What the hook does not recognise: a command inside `"$(…)"` or backticks, a
command passed as a string to another shell (`bash -c`, `pwsh -Command`), a
target held in a variable, and a script file that runs the command. The rule in
`CLAUDE.md` § Editing files covers those.
"""

import json
import os
import re
import shlex
import subprocess
import sys

# The root of the repository: this file sits in `.claude/hooks/`.
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

# What a write must not land on: the extensions the repository tracks, plus the
# files it tracks without one. `gradlew` is the sharpest case — `.gitattributes`
# pins its end-of-line, so a redirect onto it is the corruption this hook exists
# to refuse.
SOURCE_EXTENSIONS = (
    ".kt", ".kts", ".java", ".py", ".sql",
    ".md", ".bru", ".properties", ".yml", ".yaml",
    ".json", ".toml", ".xml", ".bat", ".example",
    ".gitignore", ".gitattributes", ".gitkeep",
)
SOURCE_NAMES = ("gradlew", "dockerfile", "makefile")

# Write targets that are not the working tree. A temporary directory is named
# outright or through the variable that holds it.
NOWHERE = ("/dev/null", "$null", "nul", "nul:")
TEMPORARY = (
    "/tmp/", "/temp/", "/scratchpad/",
    "$env:temp", "$env:tmp", "%temp%", "%tmp%", "$tmpdir", "${tmpdir}",
    "$tmp/", "$temp/", "${tmp}", "${temp}",
)

# Shell words that come before a command word without being one.
KEYWORDS = ("do", "then", "else", "elif", "!", "{", "(")

# Commands that run the command word after their own options. The tuple holds
# the options that consume the token after them.
INTRODUCERS = {
    "xargs": (
        "-n", "-I", "-d", "-L", "-P", "-s", "-E", "-a", "--max-args",
        "--replace", "--delimiter", "--max-lines", "--max-procs",
        "--max-chars", "--eof", "--arg-file",
    ),
    "sudo": ("-u", "-g", "-C", "-D", "-h", "-p", "-U", "-r", "-t", "--user", "--group"),
    "env": ("-u", "-C", "-S", "--unset", "--chdir", "--split-string"),
    "nice": ("-n", "--adjustment"),
    "timeout": ("-s", "-k", "--signal", "--kill-after"),
    "time": (),
    "nohup": (),
    "command": (),
}
# The operands an introducer takes before the command word: `timeout 10 sed`
# runs `sed` for ten seconds.
INTRODUCER_OPERANDS = {"timeout": 1}

# The short flags sed and perl accept as a cluster. A cluster outside these
# carries a value: `perl -MList::Util` names a module, it does not ask for four
# flags one of which is in-place.
SED_SHORT = set("nefirsuzEIRZ")
PERL_SHORT = set("acdeilnpsuvwx")

POWERSHELL_WRITERS = ("set-content", "out-file", "add-content", "tee-object")
# PowerShell accepts any unambiguous prefix of a parameter name, so these match
# by prefix.
PATH_PARAMETERS = ("-path", "-filepath", "-literalpath", "-pspath")
VALUE_PARAMETERS = ("-encoding", "-value", "-delimiter", "-width", "-stream", "-inputobject")

HEREDOC = re.compile(
    r"(<<-?[ \t]*(['\"]?)([A-Za-z_][A-Za-z0-9_]*)\2[^\n]*)\n(?:.*?\n)??[ \t]*\3[ \t]*\r?(?=\n|$)",
    re.DOTALL,
)
HERE_STRING = re.compile(r"@(['\"])\r?\n.*?\r?\n\1@", re.DOTALL)
CONTINUATION = re.compile(r"[\\`]\r?\n")
SEPARATORS = re.compile(r"\|\||&&|[;|\n]")
REDIRECT = re.compile(r"\d?>>?\s*(?!&)(\"[^\"]+\"|'[^']+'|[^\s;|&<>]+)")
ASSIGNMENT = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*=")
# The characters after which a `#` starts a comment rather than sitting inside a
# word, as in `$#` or `issue#171`.
WORD_BOUNDARIES = " \t\r\n;&|("
SUBSHELL = re.compile(r"^\$?\(+")

EDIT_INSTEAD = (
    "Use the Edit or Write tool, which writes the line endings and the encoding "
    "the file already has. A substitution across several files is made with "
    "Edit, once per file."
)


def _command_line(command: str) -> str:
    """The command with heredoc bodies removed and continued lines joined.

    The line that opens a heredoc stays, because a `> CLAUDE.md` on it is a
    write. The body is data: a commit message that quotes `sed -i` runs nothing.
    """
    line = HEREDOC.sub(r"\1", command)
    line = HERE_STRING.sub(lambda match: "@" + match.group(1) * 2 + "@", line)
    return CONTINUATION.sub(" ", line)


def _mask(line: str, escape: str) -> str:
    """`line` with every character inside quotes or a comment replaced by `_`.

    The mask has the length of `line`, so a separator or a redirect found in it
    sits at the same position in `line`. A comment runs to the newline, and the
    newline stays: it still ends the segment. `escape` escapes the character
    after it everywhere except inside single quotes.
    """
    masked = []
    quote = None
    index = 0
    while index < len(line):
        char = line[index]
        if quote != "'" and char == escape and index + 1 < len(line):
            masked.append("__")
            index += 2
            continue
        if not quote and char == "#" and (index == 0 or line[index - 1] in WORD_BOUNDARIES):
            end = line.find("\n", index)
            end = len(line) if end < 0 else end
            masked.append("_" * (end - index))
            index = end
            continue
        if quote:
            if char == quote:
                quote = None
                masked.append(char)
            else:
                masked.append("_")
        else:
            if char in "'\"":
                quote = char
            masked.append(char)
        index += 1
    return "".join(masked)


def _segments(line: str, escape: str):
    """Each segment of `line`, with its mask."""
    mask = _mask(line, escape)
    start = 0
    for separator in SEPARATORS.finditer(mask):
        yield line[start:separator.start()], mask[start:separator.start()]
        start = separator.end()
    yield line[start:], mask[start:]


def _tokens(segment: str) -> list:
    """Split a segment into tokens, keeping backslashes.

    ❗️ `posix=True` reads a backslash as an escape, so `C:\\tools\\sed.exe` comes
    back as `C:toolssed.exe` and stops looking like a path to `sed`. This
    repository is developed on Windows, so the paths arrive with backslashes.
    """
    try:
        return shlex.split(segment, posix=False)
    except ValueError:
        return segment.split()


def _unquote(token: str) -> str:
    for quote in ("'", '"'):
        if len(token) > 1 and token.startswith(quote) and token.endswith(quote):
            return token[1:-1]
    return token


def _name(token: str) -> str:
    """The program a token names: `(cd` is `cd`, `C:\\tools\\sed.exe` is `sed.exe`."""
    return os.path.basename(_unquote(SUBSHELL.sub("", token)).replace("\\", "/")).lower()


def _skip_to_command(tokens: list, index: int, value_flags: tuple) -> int:
    """The position of the command word at or after `index`, or -1."""
    while index < len(tokens):
        token = tokens[index]
        if ASSIGNMENT.match(token) or token in KEYWORDS:
            index += 1
        elif token.startswith("-") and len(token) > 1:
            index += 2 if token in value_flags else 1
        else:
            return index
    return -1


def _command_indices(tokens: list) -> list:
    """The positions of `tokens` that hold a command word.

    The first is the segment's own. When that one is `xargs`, `sudo`, `env` or
    `find` with `-exec`, the command it runs is one too, and so on down.
    """
    indices = []
    index = _skip_to_command(tokens, 0, ())
    while index >= 0 and index not in indices:
        indices.append(index)
        name = _name(tokens[index])
        if name in INTRODUCERS:
            index = _skip_to_command(tokens, index + 1, INTRODUCERS[name])
            for _ in range(INTRODUCER_OPERANDS.get(name, 0)):
                if index >= 0:
                    index = _skip_to_command(tokens, index + 1, ())
        elif name in ("find", "find.exe"):
            execs = [p for p in range(index + 1, len(tokens)) if tokens[p] in ("-exec", "-execdir")]
            index = _skip_to_command(tokens, execs[0] + 1, ()) if execs else -1
        else:
            index = -1
    return indices


def _in_place(token: str, program: str) -> bool:
    """Whether a sed or perl argument asks for an in-place rewrite.

    `-i`, `-i.bak`, `-i''`, `-pi`, `-0pi` and `--in-place` all do. `-n`, `-e`
    and `perl -Ilib` do not, and neither does `perl -MList::Util`, whose cluster
    is a module name.
    """
    token = token.replace("'", "").replace('"', "")
    if token == "--in-place" or token.startswith("--in-place="):
        return True
    if token.startswith("--") or not token.startswith("-") or token == "-":
        return False
    cluster = token[1:].split(".", 1)[0]
    if program.startswith("perl"):
        # `-0` takes an octal record separator: `-0777pi` reads the whole file.
        cluster = re.sub(r"^0[0-7]*", "", cluster)
    if not cluster or not cluster.isascii() or not cluster.isalpha():
        return False
    if program.startswith("perl"):
        return set(cluster) <= PERL_SHORT and "i" in cluster
    return set(cluster) <= SED_SHORT and ("i" in cluster or "I" in cluster)


def _stream_editor(tokens: list) -> str:
    for index in _command_indices(tokens):
        program = _name(tokens[index])
        if program not in ("sed", "sed.exe", "perl", "perl.exe"):
            continue
        if any(_in_place(argument, program) for argument in tokens[index + 1:]):
            return (
                f"Refused: `{program}` rewriting a file in place. It rewrites the "
                f"line endings and the non-ASCII characters, and neither `git "
                f"diff` nor the build reports it. " + EDIT_INSTEAD
            )
    return ""


def _writes_source_file(target: str) -> bool:
    path = _unquote(target.rstrip(")")).replace("\\", "/").lower()
    if not path or path in NOWHERE:
        return False
    if any(marker in path for marker in TEMPORARY):
        return False
    return path.endswith(SOURCE_EXTENSIONS) or os.path.basename(path) in SOURCE_NAMES


def _is_parameter(token: str, names: tuple) -> bool:
    lowered = _unquote(token).lower()
    return len(lowered) > 2 and any(name.startswith(lowered) for name in names)


def _cmdlet_target(tokens: list, index: int) -> list:
    """The file a PowerShell writer writes: its `-Path`, else its first argument.

    ❗️ Only that one. `Set-Content -Path notes.txt -Value (Get-Content CLAUDE.md)`
    reads `CLAUDE.md` and writes `notes.txt`; taking every token as a target
    refuses it. The first argument is the first token that is neither a
    parameter nor the value of one: in `Set-Content -Encoding utf8 CLAUDE.md` it
    is `CLAUDE.md`.
    """
    rest = tokens[index + 1:]
    for position, token in enumerate(rest):
        if _is_parameter(token, PATH_PARAMETERS) and position + 1 < len(rest):
            return [rest[position + 1]]
    position = 0
    while position < len(rest):
        token = rest[position]
        if not token.startswith("-"):
            return [token]
        position += 2 if _is_parameter(token, VALUE_PARAMETERS) else 1
    return []


def _write_targets(segment: str, mask: str, tokens: list) -> list:
    # Found in the mask, read from the segment: a `>` inside quotes is text.
    targets = [
        segment[match.start(1):match.end(1)] for match in REDIRECT.finditer(mask)
    ]
    for index in _command_indices(tokens):
        name = _name(tokens[index])
        if name in ("tee", "tee.exe"):
            targets += [t for t in tokens[index + 1:] if not t.startswith("-")]
        elif name in POWERSHELL_WRITERS:
            targets += _cmdlet_target(tokens, index)
    return targets


def _write(segment: str, mask: str, tokens: list, cwd: str) -> str:
    for target in _write_targets(segment, mask, tokens):
        if _writes_source_file(target) and _working_tree_path(target, cwd) is not None:
            return (
                f"Refused: writing `{_unquote(target)}` from the shell. It rewrites "
                f"the whole file, including the line endings and the non-ASCII "
                f"characters, and nothing reports either. " + EDIT_INSTEAD
            )
    return ""


def _native(path: str) -> str:
    """`path` with Git Bash's `/c/x` spelt as `c:/x`."""
    drive = re.match(r"^/([a-zA-Z])(/|$)", path)
    return f"{drive.group(1)}:/{path[3:]}" if drive else path


def _working_tree_path(target: str, cwd: str = None):
    """`target` relative to the root of the repository, a relative one read from `cwd`.

    `None` when it lies outside the repository: a temporary directory, the null
    device, another drive. An empty string when the hook cannot place it: a
    variable, `~`.
    """
    path = _unquote(target.rstrip(")")).replace("\\", "/")
    lowered = path.lower()
    if lowered in NOWHERE or any(marker in lowered for marker in TEMPORARY):
        return None
    if not path or path.startswith(("$", "%", "~", "&")):
        return ""
    base = _native(cwd.replace("\\", "/")) if cwd else ROOT
    try:
        relative = os.path.relpath(os.path.normpath(os.path.join(base, _native(path))), ROOT)
    except ValueError:
        # Another drive than the repository's.
        return None
    if relative == os.curdir:
        return ""
    if relative.startswith(os.pardir) or os.path.isabs(relative):
        return None
    return relative.replace("\\", "/")


def _ignored(path: str) -> bool:
    """Whether git ignores `path`. A git that cannot answer counts as no."""
    try:
        result = subprocess.run(
            ["git", "-C", ROOT, "check-ignore", "-q", path],
            capture_output=True,
            timeout=5,
        )
    except (OSError, subprocess.SubprocessError):
        return False
    return result.returncode == 0


def _leaves_file(segment: str, mask: str, tokens: list, cwd: str) -> str:
    for target in _write_targets(segment, mask, tokens):
        path = _working_tree_path(target, cwd)
        if path and not _ignored(path):
            return (
                f"Refused: writing `{path}` into the working tree. Git does not "
                f"ignore it, so it can end up in the next commit. "
                f"Write a log or a scratch file to a temporary directory — "
                f"`log=$(mktemp)`, or the session's scratchpad — or under `build/`."
            )
    return ""


def refusal(command: str, tool: str = "Bash", cwd: str = None) -> str:
    """The reason to refuse `command`, run by `tool` in `cwd`, or an empty string to let it through.

    A relative write target is read from `cwd`, and from the root of the
    repository when the payload carries none.
    """
    if not command:
        return ""
    line = _command_line(command)
    escape = "`" if tool == "PowerShell" else "\\"
    for segment, mask in _segments(line, escape):
        tokens = _tokens(segment)
        if not tokens:
            continue
        for reason in (
            _stream_editor(tokens),
            _write(segment, mask, tokens, cwd),
            _leaves_file(segment, mask, tokens, cwd),
        ):
            if reason:
                return reason
    return ""


def main() -> int:
    try:
        payload = json.load(sys.stdin)
    except (ValueError, OSError):
        # There is no command to judge. The harness owns this input, so a payload
        # it does not render is a contract change rather than a dangerous call.
        return 0
    command = (payload.get("tool_input") or {}).get("command", "")
    try:
        reason = refusal(command, payload.get("tool_name") or "Bash", payload.get("cwd"))
    except Exception as error:  # noqa: BLE001
        # A command was read and the guard failed to judge it. Letting it through
        # would run a command nothing checked, so the guard refuses it and names
        # the file to fix.
        print(
            f"Refused: the guard could not read this command ({error}). Fix "
            f".claude/hooks/guard.py, or take the hook out of "
            f".claude/settings.json.",
            file=sys.stderr,
        )
        return 2
    if not reason:
        return 0
    print(reason, file=sys.stderr)
    return 2


if __name__ == "__main__":
    # A reason carries `§` and `❗️` the day one is added to it; a Windows console
    # defaulting to cp1252 would raise on the write and lose the refusal.
    sys.stderr.reconfigure(encoding="utf-8")
    sys.exit(main())
