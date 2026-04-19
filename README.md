# Smart Home API

[![Version](https://img.shields.io/badge/version-0.5.10-blue.svg)](https://semver.org)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.13-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Arrow](https://img.shields.io/badge/Arrow-1.2.4-E91E63)](https://arrow-kt.io)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-lightblue?logo=postgresql&logoColor=white)](https://www.postgresql.org)
[![Redis](https://img.shields.io/badge/Redis-red?logo=redis&logoColor=white)](https://redis.io)
[![Ktor](https://img.shields.io/badge/Ktor-2.3.13-087CFA?logo=ktor&logoColor=white)](https://ktor.io)
[![Docker](https://img.shields.io/badge/Docker-blue?logo=docker&logoColor=white)](https://www.docker.com)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)

---

## Architecture and Guidelines

This project follows a **Pragmatic Hexagonal Architecture** (Ports and Adapters). The core business logic is fully isolated from infrastructure concerns such as Spring, JDBC, and Ktor.

For a complete description of the architecture, layer rules, the Unit of Work pattern, and coding conventions, refer to:

**[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)**

Every new feature and every refactoring must comply with the principles defined in that document. In particular: services must never use `@Transactional`, all failable operations must return `Either<DomainError, T>`, and exceptions must never escape the DAO layer.

---

A Kotlin-based Spring Boot application for managing and controlling smart home devices, focusing on climate monitoring and heating control. It integrates with providers like SwitchBot and Netatmo.

## Features

- **Device Management**: Track and manage various smart home devices.
- **Area & Room Organization**: Group devices into areas (rooms) for better management.
- **Climate Monitoring**: Fetch and store temperature and humidity data from sensors.
- **Heating Control**: Automated heating strategies (Comfort, Economy, Dynamic) based on sensor data and schedules.
- **Provider Integration**: Native support for:
  - **SwitchBot** (Meter, Hub Mini)
  - **Netatmo** (Smarther thermostats)
- **Historical Data**: Persistent storage for sensor readings and historical analysis.
- **Security**: API key-based security for endpoint protection.
- **Caching**: Redis-backed and database-persistent caching mechanisms for performance and reliability.

## Tech Stack

- **Language**: Kotlin 2.1 (JVM 21)
- **Framework**: Spring Boot 3.4
- **Database**: PostgreSQL
- **Migration**: Flyway
- **Cache**: Redis
- **HTTP Client**: Ktor
- **Containerization**: Docker
- **Testing**: JUnit 5, Kotest, RestAssured, MockK, Testcontainers

## Getting Started

### Prerequisites

- Java 21
- Docker (optional, for running with containers)
- PostgreSQL & Redis

### Configuration

The application can be configured via `src/main/resources/application.properties` (or environment variables). Key configuration areas include:

- Database connection strings
- Redis configuration
- Provider API credentials (SwitchBot, Netatmo)
- Security API keys

### Building and Running

To build the project:
```bash
./gradlew build
```

To run the application:
```bash
./gradlew bootRun
```

### Running Tests

The project has a comprehensive test suite (270+ tests) including unit and integration tests using Testcontainers.
```bash
./gradlew test
```

## Project Structure

- `src/main/kotlin/.../api/controllers`: REST API endpoints.
- `src/main/kotlin/.../api/domain`: Core domain models and business logic.
- `src/main/kotlin/.../api/providers`: Integration with external device providers (SwitchBot, Netatmo).
- `src/main/kotlin/.../api/services`: Business services, including heating strategies.
- `src/main/kotlin/.../api/persistence`: Data access layer (JDBC/PostgreSQL).
- `src/main/kotlin/.../api/schedulers`: Background tasks for data fetching and climate control.
- `src/main/resources/db/migration`: Database schema evolution scripts.

