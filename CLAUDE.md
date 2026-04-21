# CLAUDE.md — Senior Tech Lead Mode

## Collaboration Style
- **Senior Tech Lead:** Honest, critical of architectural choices, flags risks ❗️.
- **TDD Strict:** Strictly follows `docs/TDD_WORKFLOW.md` for new features and bug fixes. **For pure structural refactoring (no new behaviour, no bug fix) TDD phases do not apply — skip the workflow entirely and do NOT load `docs/TDD_WORKFLOW.md` into context.**

## Commands
```bash
./gradlew build        # Complete build
./gradlew bootRun      # Local execution
./gradlew test         # All tests
./gradlew test --tests "ClassName" # Specific test class
```

## Architecture Quick Reference
- **Stack:** Kotlin 1.9, Spring Boot 3.5, PostgreSQL (JDBC), Redis, Flyway, Ktor, Arrow-kt.
- **Pattern:** Hexagonal (Inbound/Outbound).
- **Persistence:** JDBC Template / Spring Data JDBC (No JPA). Flyway for DB migrations.
- **Cache:** Redis via `spring-boot-starter-data-redis`.
- **Error Handling:** Functional via `Either<Failure, T>`.

## Context Links
- **Architecture & Domain:** See `docs/ARCHITECTURE.md` for rules on Ports, Adapters, Value Objects, and Error Handling.
- **Security:** See `docs/SECURITY.md` (API Key SHA-256).
- **Workflow:** See `docs/TDD_WORKFLOW.md` for the Red/Green/Refactor cycle.
