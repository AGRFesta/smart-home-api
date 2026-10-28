# CLAUDE.md — Senior Tech Lead Mode

## Collaboration Style
- **Senior Tech Lead:** Honest, critical of architectural choices, flags risks ❗️.
- **TDD Strict:** a new feature or a bug fix loads the `tdd-feature` skill before any code is
  written; an issue that does not declare itself pure structural refactoring counts as one.
- **End of an issue:** load the `close-the-issue` skill — build, detekt, surface docs, changelog.
- **Release or version change:** load the `version-bump` skill.

## Commands
```bash
./gradlew build              # Complete build (all modules)
./gradlew test               # All tests (all modules)
./gradlew detekt             # Static analysis (run explicitly: build does not cover it)
./gradlew test --tests "ClassName" # Specific test class
./gradlew :app:bootRun       # Local execution
./gradlew :app:docker        # Build Docker image
./gradlew :core:pitest       # Mutation testing (see docs/MUTATION_TESTING.md)
```

## Editing files
**Change a file with the Edit and Write tools.** A stream editor — `sed -i`, `perl -pi` — and a
shell write onto a source file — `>`, `>>`, `tee`, `Set-Content` — rewrite the whole file, and ❗️
**two of the things they rewrite, nothing reports**:
- **The line endings.** The working tree is CRLF (`core.autocrlf=true`). A stream editor writes LF,
  so the file becomes mixed while `git diff` stays clean — the index holds LF either way.
- **The non-ASCII characters.** Sources and docs carry `§` and `❗️`. One written through a shell
  escape can land as a latin-1 byte, which reads back as `�`.

**A substitution across several files is made with Edit, once per file.** `sed -n`, `grep`, `find`
and `od` read and change nothing; they are the right tools for reading one. A `PreToolUse` hook
refuses those writes — `.claude/hooks/guard.py`, which recognises no opt-out token. It also refuses a
shell write onto a path in the working tree that git does not ignore: a log goes to `$(mktemp)` or
the session's scratchpad, never to the repository root. ❗️ **The hook reads the command line
only.** A spelling it does not recognise — a command inside `$(…)`, a target held in a variable, a
script — still falls under the rule above.

## Architecture Quick Reference
- **Stack:** Kotlin 2.1, Spring Boot 3.5, PostgreSQL (JDBC), Redis, Flyway, Ktor (HTTP client), Arrow-kt.
- **Pattern:** Hexagonal (Inbound/Outbound).
- **Persistence:** JDBC Template / Spring Data JDBC (No JPA). Flyway for DB migrations.
- **Cache:** Redis via `spring-boot-starter-data-redis`.
- **Error Handling:** Functional via `Either<Failure, T>`.

## How to write
Applies to everything written for a human to read: commit and PR titles, issue text, KDoc, comments,
documents, and replies in chat.
- **Declarative sentences, subject-verb-object, one idea each.** If a sentence only parses on the
  second reading, split it.
- **A title says what changed, in the imperative**, naming the thing that changed, so
  `git log --grep` finds it. A section heading follows the same rule.
- **Name a thing the way the code names it, or in plain words.** Never coin a short form, and never
  put a metaphor in the place of a noun.
- **Explain a term the first time it appears in each document.** A reader opens one file, not the
  whole project.
- **Do not personify code.** A service returns, validates, rejects; it does not answer or refuse.

## Conventions
`docs/DOCUMENTATION_STYLE.md` owns these rules and the argument behind each. The short form:
- **One topic, one owner.** Link to the file that owns a fact; never restate it. KDoc states the
  contract and links the section; it does not re-argue it. (§1 — owner table)
- **Docs describe the present, not the process.** No version labels, no issue/PR ids, no "to be
  added when implemented". `CHANGELOG.md` is the exception. `docs/domain/` records the decisions
  behind the model as it stands; an open question or a deferred feature belongs to an issue. (§2)
- **Comments are terse; the argument lives in `docs/`.** One or two sentences at a declaration. (§3)
- **`❗️` means: changing this silently breaks something, and no test will tell you.** Not emphasis.
  Inbound ports carry none. (§4)
- **Never justify code by what a test can reach.** Say what the code is *for*. (§5)
- **Migration comments must be true forever, without maintenance**, and what a database consumer
  needs goes in `COMMENT ON`, not `--`. (§6, §7)
- **Surface docs carry the contract, reasoning at the end** in `# Design notes`. (§8)

## Context Links
- **Architecture:** `docs/ARCHITECTURE.md` — Ports & Adapters, Value Objects, error handling.
- **Subdomains:** `docs/SUBDOMAINS.md` — core / supporting / generic classification.
- **Security:** `docs/SECURITY.md` — API key (SHA-256).
- **Domain Models:** `docs/domain/` — the decisions behind the model (e.g. `DEVICES.md` for device
  status semantics and synchronization flow).
- **Workflow:** `docs/TDD_WORKFLOW.md` (loaded by `tdd-feature`) · `docs/REVIEW_WORKFLOW.md`
  (reviewer/author loop, marker schema, human gates) · `docs/MUTATION_TESTING.md`.
