# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.4.5] - 2026-07-18

### Added
- Add hOn provider to the device sync: appliances are discovered as devices; the known AC models are registered in the catalog (roles arrive with the readings/commands issues). (#249)

## [1.4.4] - 2026-07-18

### Added
- Add SwitchBot (`devices`) and Netatmo (`home-status`) probes to `GET /providers/{provider}/diagnostics`. (#254)

## [1.4.3] - 2026-07-17

### Fixed
- Follow the href on the Salesforce ProgressiveLogin interstitial to reach the hOn token page;
  the flow re-fetched the interstitial itself and failed the login with "incomplete OAuth
  tokens (missing: access_token, refresh_token, id_token)". (#248)

## [1.4.2] - 2026-07-17

### Fixed
- Capture the last field of the hOn OAuth redirect fragment: the parser required a trailing
  `&`, so a done-URL ending with `id_token` failed the login with "incomplete OAuth tokens";
  the failure reason now also pinpoints which tokens are missing. (#248)

## [1.4.1] - 2026-07-17

### Fixed
- Replay Salesforce cookies whose value Ktor cannot re-encode in RAW form (spaces, commas,
  quotes) using the RFC 6265 quoted form or URL-encoding, instead of breaking the hOn login
  with an unhandled `IllegalArgumentException` (surfaced as a misleading 403). (#248)
- Return the standard `500` error body for unhandled exceptions: the container ERROR dispatch
  to `/error` was blocked by security and every unhandled failure surfaced as an empty `403`. (#248)

## [1.4.0] - 2026-07-17

### Added
- Add hOn (Haier) provider transport: Salesforce OAuth login + authenticated API client,
  behind `providers.hon.enabled`. (#247)
- Add `GET /providers/{provider}/diagnostics` endpoint running named read-only probes against the
  provider's cloud with raw passthrough (diagnostic contract, no schema guarantee); hOn probes:
  `appliance-list`, `context`, `commands`, `appliance-model`. (#248)

## [1.3.4] - 2026-07-11

### Added
- Emit `OPENED`/`REMINDER`/`RESOLVED` notifications on alert transitions: one on open, one on resolve,
  and periodic reminders while the alert stays open, at a configurable cadence
  (`alerts.notifications.reminder-interval`, default daily). Notifications are persisted and pruned
  after a configurable retention window (`alerts.notifications.retention`, default 90 days). (#194)
- Add `GET /notifications` paginated endpoint to inspect the emitted notifications. (#194)
- Open and resolve `BATTERY_LOW` alerts automatically on the polling cycle, driven by global hysteresis
  thresholds (`alerts.battery-low.trigger`/`clear`); low-battery devices now surface in `GET /alerts`
  without manual intervention. (#193)

### Changed
- Return `intervals` sorted by `startTime` ascending on `GET /areas/{areaId}/heating-schedule`,
  matching the PUT response; ordering was previously unspecified. (#232)
- Return a typed failure or a per-heater outcome report from the heating evaluation instead of
  swallowing every error in logs; the heating scheduler now logs an error when the evaluation fails. (#201)
- Surface per-area active alert types in the home dashboard as an additive `activeAlerts` field
  (`FieldResult<AlertType[]>`), on both `GET /home` and the SSE stream. (#196)
- Expose the device's open alert types as an additive `activeAlerts` field in `GET /devices/{uuid}`;
  `null` means the alert lookup failed, `[]` means no open alerts. (#196)

### Fixed
- Persist the `isIndoor` flag on `POST /areas`; the INSERT omitted the column, so every created area
  was stored as indoor regardless of the request value. (#233)

## [1.3.3] - 2026-07-03

### Changed
- Derive the `features` array on `GET /devices*` from the device `model` via the catalog; an unknown
  model yields empty `features`. Wire shape unchanged. (#222)

## [1.3.2] - 2026-07-02

## [1.3.1] - 2026-07-01

## [1.3.0] - 2026-06-30

### Added
- Add `GET /alerts` endpoint listing alerts, defaulting to the currently `OPEN` ones and filterable by
  `status`. Alerts are a stateful, idempotent, DB-persisted aggregate (at most one `OPEN` per
  type+target); no automatic evaluation or concrete rules yet. (#192)

### Changed
- ECONOMY heating now computes the demand ratio over decidable areas only (those with both a current and a
  target temperature); areas with a missing reading or target no longer dilute the ratio, and the heater
  stays OFF when no area is decidable. (#211)
- Expose `batteryLevel` (latest known battery percentage, from cache) in the `GET /devices/{uuid}` response;
  `null` when not battery-powered, not yet collected, or expired. (#191)

### Fixed
- Compare the ECONOMY demand ratio against the threshold exactly, removing the lossy scale-2 `HALF_UP`
  rounding that could turn the shared heater ON just below the threshold (e.g. 5/8 areas with
  `threshold = 0.63`). (#210)
- Report SwitchBot hub devices with the `SWITCHBOT` provider instead of `NETATMO`. (#203)

## [1.2.0] - 2026-06-14

### Added
- Add `GET /devices/{uuid}/diagnostics` endpoint returning the provider's realtime raw payload for a device
  (passthrough, no persistence): `404` unknown uuid, `502` provider failure (message surfaced), `501` when the
  device's provider has no diagnostics implementation. (#187)
- Add `GET /devices/{uuid}` endpoint returning the per-device aggregate: base fields, `createdOn`/`updatedOn`
  and current area `assignments` (role-scoped `SENSOR`/`ACTUATOR`); `404` when the device is unknown. (#186)
- Add `GET /devices` endpoint to list/search managed devices, filterable by `provider`, `status` and
  `feature` (combinable, AND semantics). (#185)
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
