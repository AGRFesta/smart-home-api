# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Add public Actuator health and readiness/liveness probes (status only; component details require
  authentication) and an authenticated `GET /actuator/info` exposing the build version. (#176)

### Security
- Bump Spring Boot 3.5.14 → 3.5.15 (Tomcat, Netty, PostgreSQL driver CVEs). (#dependabot)

## [1.1.0] - 2026-06-01

### Added
- Add `GET /home/stream` SSE endpoint that pushes the full home dashboard to connected clients on every
  polling cycle or heating configuration change, replacing client-side polling on the Android home screen. (#173)

## [1.0.0] - 2026-04-16

### Added
- Introduce `docker-compose.yml` for local development. (#19)

### Security
- Bump Spring Boot 3.5.13 → 3.5.14 (Tomcat, Security, Netty, PostgreSQL driver CVEs). (#cb0f612)
