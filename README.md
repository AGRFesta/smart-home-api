# Smart Home API

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

