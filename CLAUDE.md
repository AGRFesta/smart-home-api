# CLAUDE.md — Senior Tech Lead Mode

## Collaboration Style
- **Senior Tech Lead:** Honest, critical of architectural choices, flags risks ❗️.
- **TDD Strict:** Strictly follows `docs/TDD_WORKFLOW.md` for new features and bug fixes. **For pure structural refactoring (no new behaviour, no bug fix) TDD phases do not apply — skip the workflow entirely and do NOT load `docs/TDD_WORKFLOW.md` into context.**

## Commands
```bash
./gradlew build              # Complete build (all modules)
./gradlew :app:bootRun       # Local execution
./gradlew test               # All tests (all modules)
./gradlew :app:docker        # Build Docker image
./gradlew test --tests "ClassName" # Specific test class
```

## Architecture Quick Reference
- **Stack:** Kotlin 1.9, Spring Boot 3.5, PostgreSQL (JDBC), Redis, Flyway, Ktor, Arrow-kt.
- **Pattern:** Hexagonal (Inbound/Outbound).
- **Persistence:** JDBC Template / Spring Data JDBC (No JPA). Flyway for DB migrations.
- **Cache:** Redis via `spring-boot-starter-data-redis`.
- **Error Handling:** Functional via `Either<Failure, T>`.

## Conventions
- **API Docs:** When adding or modifying an endpoint, update `docs/api/<resource>.md` and the table in `docs/api/API_INDEX.md`.
- **Changelog:** At the end of every issue, add an entry to `CHANGELOG.md` under `## [Unreleased]`. Use these sections:
  - `Added` — new endpoints or observable behaviours
  - `Changed` — modified contracts or behaviours
  - `Fixed` — bug fixes with observable impact
  - `Security` — dependency bumps that fix CVEs
  - Do **not** add entries for: test additions, internal refactoring without contract change, code style cleanup.
  - One line per entry, imperative mood, issue number in parentheses. Example: `- Add \`GET /home/stream\` SSE endpoint for real-time dashboard. (#173)`

## Context Links
- **Architecture & Domain:** See `docs/ARCHITECTURE.md` for rules on Ports, Adapters, Value Objects, and Error Handling.
- **Security:** See `docs/SECURITY.md` (API Key SHA-256).
- **Workflow:** See `docs/TDD_WORKFLOW.md` for the Red/Green/Refactor cycle.
- **Domain Models:** See `docs/domain/` for domain-specific lifecycle and design decisions (e.g. `DEVICES.md` for device status semantics and synchronization flow).
