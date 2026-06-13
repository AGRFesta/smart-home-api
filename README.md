# Smart Home API

[![Version](https://img.shields.io/badge/version-1.1.0-blue.svg)](https://semver.org)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.21-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.15-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Arrow](https://img.shields.io/badge/Arrow-1.2.4-E91E63)](https://arrow-kt.io)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-lightblue?logo=postgresql&logoColor=white)](https://www.postgresql.org)
[![Redis](https://img.shields.io/badge/Redis-red?logo=redis&logoColor=white)](https://redis.io)
[![Ktor](https://img.shields.io/badge/Ktor-2.3.13-087CFA?logo=ktor&logoColor=white)](https://ktor.io)
[![Docker](https://img.shields.io/badge/Docker-blue?logo=docker&logoColor=white)](https://www.docker.com)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)

A Kotlin / Spring Boot backend for managing and automating smart home devices, with a focus on **climate monitoring** and **heating control**. It collects temperature and humidity readings from physical sensors, persists their history, and drives thermostats through configurable heating strategies. It integrates natively with **SwitchBot** and **Netatmo** hardware.

The project doubles as a reference implementation: it is built with a strict **Pragmatic Hexagonal Architecture**, functional error handling, and architectural boundaries that are *enforced by tests* rather than left to convention.

---

## Architecture Highlights

These are the engineering decisions that shape the codebase — the *why*, not just the *what*:

- **Hexagonal, multi-module by design.** The domain model in `:core` is framework-free; its only concession to Spring is `spring-context` for dependency injection (`@Service` stereotypes on use cases). It knows nothing of Spring Boot, web, JDBC, or Ktor. Infrastructure lives in dedicated modules (`:persistence`, `:providers`) and the runtime is assembled in `:app`. The compiler enforces the dependency direction.
- **Boundaries enforced, not documented.** [ArchUnit](https://www.archunit.org) tests fail the build if a layer reaches where it shouldn't — so the architecture cannot silently erode over time.
- **Functional error handling.** Every failable operation returns `Either<DomainError, T>` (Arrow). Exceptions never escape the DAO layer, and there are no `@Transactional` services — transactional consistency is handled explicitly via a Unit of Work pattern.
- **Domain logic proven, not just sampled.** Pure core logic is covered with [property-based testing](docs/MUTATION_TESTING.md) (Kotest Property), and mutation testing (PITest) measures whether the suite actually catches regressions.
- **Real-time push.** `GET /home/stream` streams the full home dashboard to clients over Server-Sent Events on every polling cycle or heating configuration change — replacing client-side polling.

For the full picture, see **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)** (layer rules, Value Objects, Unit of Work) and **[docs/SECURITY.md](docs/SECURITY.md)** (API-key authentication model and filter flow).

---

## Features

- **Device Management** — track and manage smart home devices and their lifecycle (see [docs/domain/DEVICES.md](docs/domain/DEVICES.md)).
- **Area & Room Organization** — group devices into areas (rooms) for structured control.
- **Climate Monitoring** — fetch and persist temperature and humidity data from sensors.
- **Heating Control** — automated strategies (Comfort, Economy, Dynamic) driven by sensor data and schedules.
- **Provider Integration** — native support for:
  - **SwitchBot** (Meter, Hub Mini)
  - **Netatmo** (Smarther thermostats)
- **Real-time Dashboard** — SSE endpoint pushing live home state to connected clients.
- **Historical Data** — persistent storage of sensor readings for analysis.
- **Security** — API-key-based endpoint protection (SHA-256).
- **Caching** — Redis-backed and database-persistent caching for performance and resilience.

## Tech Stack

- **Language:** Kotlin 2.1 (JVM 21)
- **Framework:** Spring Boot 3.5
- **Functional core:** Arrow (`Either`-based error handling)
- **Database:** PostgreSQL (JDBC / Spring Data JDBC — no JPA)
- **Migrations:** Flyway
- **Cache:** Redis
- **HTTP Client:** Ktor (provider integrations)
- **Build:** Gradle (multi-module, version catalog)
- **Containerization:** Docker
- **Testing:** JUnit 5, Kotest (+ Property), RestAssured, MockK, Testcontainers, ArchUnit, PITest

## Module Structure

This is a multi-module Gradle build that mirrors the hexagonal layers:

```
smart-home-api
├── core         # Pure domain: models, ports, business logic (no framework deps)
├── persistence  # Outbound adapters: JDBC/PostgreSQL DAOs, Flyway migrations
├── providers    # Outbound adapters: SwitchBot & Netatmo integrations (Ktor)
└── app          # Spring Boot runtime: controllers, schedulers, wiring, security
```

The dependency rule is one-directional: `app` depends on everything, the infrastructure modules depend on `core`, and `core` depends only on lightweight libraries (Arrow, `spring-context` for DI) — never on the web, persistence, or provider stacks.

## Getting Started

### Prerequisites

- **Java 21** — required to build and run (the build targets the JVM 21 toolchain).
- **A reachable PostgreSQL and Redis instance** — required at runtime; the app will not start without them. You can install them natively or run them via Docker.

### Configuration

The application is configured via `app/src/main/resources/application.properties` or environment variables. Key areas:

- Database connection
- Redis configuration
- Provider API credentials (SwitchBot, Netatmo)
- Security API keys

### Building and Running

```bash
./gradlew build              # Build all modules
./gradlew :app:bootRun       # Run the application locally
./gradlew :app:docker        # Build the Docker image
```

The repository's `docker-compose.yml` describes the **production deployment** stack — the published app image behind a Caddy reverse proxy, with TimescaleDB and Redis — and expects deployment environment variables and host volume mounts. It is not a local development setup: for local work, provide your own PostgreSQL and Redis instances.

### Running Tests

The project has a comprehensive suite of 480+ unit and integration tests (the latter backed by Testcontainers), plus architectural and property-based tests.

```bash
./gradlew test                       # All tests, all modules
./gradlew test --tests "ClassName"   # A specific test class
```

## Documentation

- **[Architecture & Domain](docs/ARCHITECTURE.md)** — ports, adapters, Value Objects, error handling, Unit of Work.
- **[Security](docs/SECURITY.md)** — authentication model, filter flow, configuration.
- **[Mutation & Property Testing](docs/MUTATION_TESTING.md)** — testing strategy for the core domain.
- **[TDD Workflow](docs/TDD_WORKFLOW.md)** — the Red/Green/Refactor cycle followed in this repo.
- **[API Reference](docs/api/API_INDEX.md)** — endpoint documentation per resource.
- **[Domain Models](docs/domain/)** — lifecycle and design decisions (e.g. device status semantics).

## License

Released under the [MIT License](LICENSE).
