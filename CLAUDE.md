# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Collaboration Style

Act as a Senior Tech Lead. Be brutally honest about technical choices—no sycophancy. Push back on unclear, illogical, or architecturally unsound instructions. Flag risks, edge cases, and unclear requirements **before** writing code. If something is ambiguous and a critical structural decision is needed, stop and ask rather than guessing. When flagging a potential error, missed edge case, or pushing back on an instruction, start the response with ❗️.

## Commands

```bash
# Build
./gradlew build

# Run
./gradlew bootRun

# All tests
./gradlew test

# Single test class
./gradlew test --tests "org.agrfesta.sh.api.controllers.AreasControllerUnitTest"

# Single test method
./gradlew test --tests "org.agrfesta.sh.api.controllers.AreasControllerUnitTest.someTestMethod"
```

Integration tests use Testcontainers and require Docker.

## Architecture Overview

**Kotlin 2.1 / Spring Boot 3.4 / PostgreSQL + Redis / Ktor (HTTP client) / Arrow-kt (functional)**

The codebase follows a layered DDD approach:

```
controllers → services → persistence (DAO) → JDBC
                      → providers (SwitchBot, Netatmo via Ktor)
schedulers → services
```

### Typed Error Handling (Arrow `Either`)

All operations return `Either<Failure, T>`. Failures are sealed interfaces organized by domain concern (e.g., `AreaCreationFailure`, `PersistenceFailure`, `ProviderFailure`). Controllers pattern-match on the result:

```kotlin
when (val result = service.doSomething()) {
    is Right -> ResponseEntity.ok(result.value)
    is Left -> result.value.toResponse()
}
```

DAOs map exceptions to `Left<PersistenceFailure>` at the boundary—never let raw exceptions propagate into the service layer.

### Domain Model Composition

Domain entities use Kotlin delegation (`by`) rather than deep inheritance:

```kotlin
class HeatableAreaImpl(mcArea: MonitoredClimateArea, ...) 
    : HeatableArea, MonitoredClimateArea by mcArea
```

`Area` → `MonitoredClimateArea` → `HeatableArea` is the composition chain. Factories (`AreasFactory`) assemble domain objects from DTOs.

### Value Objects

`Temperature`, `Percentage`, `RelativeHumidity` are inline value classes with operator overloads (`+`, `-`, `*`, `/`) using `BigDecimal` with `HALF_UP` rounding. Always use these types—never raw `Double`/`Float` for domain values.

### Persistence Pattern

- Interfaces: `AreasDao`, `DevicesDao`, etc.
- JDBC implementations in `persistence/jdbc/dao/`
- Repositories (Spring Data JDBC) used internally by DAO implementations
- All DAO methods return `Either<PersistenceFailure, T>`

### Provider Integration

External device APIs (SwitchBot, Netatmo) are integrated via `providers/`. Each provider has:
- A service class for API communication (Ktor HTTP client)
- A factory for mapping provider responses to domain `Device` types
- `Either`-wrapped error handling using `ProviderFailure`

Suspend functions are used for provider calls; schedulers integrate via `runBlocking { }`.

### Scheduling & Caching

- `@Scheduled` + `@Async` for background tasks (e.g., `DevicesDataFetchScheduler` runs every minute)
- `SmartCache` utility provides Redis-backed caching with database fallback

### Security

`SimpleApiKeyFilter` validates Bearer tokens via SHA-256 hash, granting `ROLE_API` authority. All endpoints require authentication unless explicitly excluded.

### Testing Patterns

- **Unit/MVC slice:** `@WebMvcTest` + MockK (e.g., `AreasControllerUnitTest`)
- **Integration:** extend `AbstractIntegrationTest` which spins up PostgreSQL + Redis via Testcontainers
- Assertions use Kotest matchers; HTTP assertions use RestAssured
