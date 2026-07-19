# Devices API

## GET /devices

Returns the managed devices as a flat collection, with optional filters. This is a lean read query
over the persisted device list — per-device relationships (area assignments, raw provider data) are
intentionally **not** included.

Pagination and sorting are out of scope: the full matching list is returned.

### Query parameters

All parameters are optional and combinable (AND semantics).

| Param      | Example     | Description                                          |
|------------|-------------|------------------------------------------------------|
| `provider` | `SWITCHBOT` | Filter by provider (`SWITCHBOT`, `NETATMO`).         |
| `status`   | `PAIRED`    | Filter by device status (`PAIRED`, `DETACHED`).      |
| `feature`  | `SENSOR`    | Filter by device feature (`SENSOR`, `ACTUATOR`).     |

### Response `200 OK`

A JSON array of devices. An empty result is returned as an empty array (`[]`), not an error.

```json
[
  {
    "uuid": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "status": "PAIRED",
    "deviceProviderId": "abc-123",
    "provider": "SWITCHBOT",
    "name": "Living Room Sensor",
    "features": ["SENSOR"]
  }
]
```

### Response `500 Internal Server Error`

Returned when the persisted devices cannot be read.

```json
{ "message": "Unable to retrieve devices!" }
```

## GET /devices/{uuid}

Returns the per-device **view** — *our truth*, the persisted read-model of a single device: its base
fields plus the relationships our model holds. This is the sibling of the diagnostics endpoint
(*provider's truth*, realtime passthrough).

Unlike the lean [`GET /devices`](#get-devices) list, this item resource is designed to grow: today it
exposes area `assignments`; future relationships (heating schedules, actuator state, sensor history)
can be added here as new top-level keys without changing the list contract.

### Path parameters

| Param  | Example                                | Description                |
|--------|----------------------------------------|----------------------------|
| `uuid` | `3fa85f64-5717-4562-b3fc-2c963f66afa6` | The persisted device UUID. |

### Response `200 OK`

`createdOn` / `updatedOn` are Unix epoch seconds (consistent with all timestamps in this API);
`updatedOn` is `null` until the device is first updated.

`assignments` lists the device's **current** area assignments (empty array when unassigned). Each entry
is role-scoped: a device can be a `SENSOR` in one area and an `ACTUATOR` in another. Only current
assignments are returned — disconnected (historical) sensor assignments are not included.

`batteryLevel` is the latest known battery percentage (0–100) for battery-powered devices, read from
cache (refreshed each polling cycle). It is `null` when the device is not battery-powered, when no value
has been collected yet, or when the cached value has expired (a device offline beyond the cache TTL).

`activeAlerts` lists the types of the device's currently `OPEN` alerts (e.g. `BATTERY_LOW`), resolved at
read time from the same alert store as [`GET /alerts`](alerts.md) — a minimal projection; for the full
alert detail follow `GET /alerts`. An empty array means no open alerts; `null` means the alert lookup
failed ("unknown", never to be confused with "no alerts").

```json
{
  "uuid": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "name": "Living Room Sensor",
  "provider": "SWITCHBOT",
  "deviceProviderId": "abc-123",
  "status": "PAIRED",
  "features": ["SENSOR"],
  "createdOn": 1736499600,
  "updatedOn": 1749751440,
  "assignments": [
    { "areaUuid": "a1b2c3d4-0000-0000-0000-000000000000", "areaName": "Living Room", "role": "SENSOR" }
  ],
  "batteryLevel": 88,
  "activeAlerts": ["BATTERY_LOW"]
}
```

| Field          | Description                                                                       |
|----------------|-----------------------------------------------------------------------------------|
| `assignments`  | Current area assignments; each has `areaUuid`, `areaName` and `role` (`SENSOR`/`ACTUATOR`). |
| `batteryLevel` | Latest known battery percentage (0–100) from cache; `null` when not battery-powered, not yet collected, or expired. |
| `activeAlerts` | Types of the device's `OPEN` alerts, from the alert store; `[]` when none, `null` when the lookup failed. |

### Response `404 Not Found`

Returned when `uuid` does not match any persisted device.

### Response `500 Internal Server Error`

Returned when the persisted device cannot be read.

```json
{ "message": "Unable to retrieve device '<uuid>'!" }
```

## GET /devices/{uuid}/diagnostics

Returns the **provider's truth** — the realtime, unfiltered raw payload the device's provider holds about
it *right now*. This is the sibling of [`GET /devices/{uuid}`](#get-devicesuuid) (*our truth*, the persisted
view).

It is a rarely-used **diagnostic passthrough**: the backend resolves the device, knows which provider to
call (and does the signed/encrypted/token work the provider requires), and writes the provider response
through **verbatim**. Nothing is persisted or cached — the response reflects exactly what the provider
says at call time, **including failures** (the diagnostic value lives in not masking them).

The response shape is **provider-specific** and intentionally not described by a fixed schema (e.g.
SwitchBot returns the device status; Netatmo returns the device's room status; hOn returns the
appliance's raw command context). Fields our domain does not model (e.g. `hubDeviceId`,
`enableCloudService`) are included as-is.

### Path parameters

| Param  | Example                                | Description                |
|--------|----------------------------------------|----------------------------|
| `uuid` | `3fa85f64-5717-4562-b3fc-2c963f66afa6` | The persisted device UUID. |

### Response `200 OK`

`Content-Type: application/json` — the provider's raw body, written through unchanged (no re-modelling).

### Response `404 Not Found`

Returned when `uuid` does not match any persisted device.

### Response `501 Not Implemented`

Returned when the device's provider has no diagnostics implementation (e.g. a device type that cannot be
inspected).

```json
{ "message": "Diagnostics is not available for device '<uuid>'!" }
```

### Response `502 Bad Gateway`

Returned when the provider is reached but returns an error or is unreachable; the provider's message is
surfaced in the body.

```json
{ "message": "<provider failure message>" }
```

### Response `500 Internal Server Error`

Returned when the persisted device cannot be read.

```json
{ "message": "Unable to retrieve device '<uuid>'!" }
```

## GET /devices/{uuid}/air-conditioner

Returns the **control state** of an air conditioner — power, operating mode, target temperature and
fan speed — read in realtime from the device's provider (never persisted nor cached).

Every field except `power` is nullable: the device may not report a parameter, or report a value our
domain does not map. In that case the field is `null` (and `power` is `"UNDEFINED"`) — "unknown" is
surfaced as-is, never masked with a default.

### Path parameters

| Param  | Example                                | Description                |
|--------|----------------------------------------|----------------------------|
| `uuid` | `3fa85f64-5717-4562-b3fc-2c963f66afa6` | The persisted device UUID. |

### Response `200 OK`

```json
{
  "power": "OFF",
  "mode": "COOL",
  "targetTemperature": 26,
  "fanSpeed": "LOW"
}
```

| Field               | Description                                                                  |
|---------------------|------------------------------------------------------------------------------|
| `power`             | `ON`, `OFF`, or `UNDEFINED` when the device does not report it.              |
| `mode`              | `AUTO`, `COOL`, `HEAT`, `DRY`, `FAN_ONLY`; `null` when unknown.              |
| `targetTemperature` | Target temperature in °C (number); `null` when unknown.                      |
| `fanSpeed`          | `AUTO`, `LOW`, `MEDIUM`, `HIGH`; `null` when unknown.                        |

### Response `404 Not Found`

Returned when `uuid` does not match any persisted device.

### Response `409 Conflict`

Returned when the device exists but is not an air conditioner (or its provider has no driver).
The no-driver case is deliberately folded into this 409: from the client's perspective the
actionable fact is the same — this device cannot be driven as an air conditioner (unlike the
diagnostics endpoint's 501, which flags a provider-level diagnostics capability gap).

```json
{ "message": "Device '<uuid>' is not an air conditioner!" }
```

### Response `502 Bad Gateway`

Returned when the provider is reached but returns an error or is unreachable; the failure is
surfaced in the body. This includes the not-synced-yet case (after a restart, until the device
synchronisation runs).

```json
{ "message": "<provider failure>" }
```

### Response `500 Internal Server Error`

Returned when the persisted device cannot be read.

```json
{ "message": "Unable to retrieve device '<uuid>'!" }
```

## PATCH /devices/{uuid}/air-conditioner

Drives an air conditioner with a **partial update**: only the provided fields change, the device
keeps its current value for the rest (the driver composes the full command via read-modify-write —
one PATCH, one wire command, no races between fields).

### Path parameters

| Param  | Example                                | Description                |
|--------|----------------------------------------|----------------------------|
| `uuid` | `3fa85f64-5717-4562-b3fc-2c963f66afa6` | The persisted device UUID. |

### Request

All fields are optional, but **at least one** must be provided.

```json
{
  "power": "ON",
  "mode": "COOL",
  "targetTemperature": 22,
  "fanSpeed": "AUTO"
}
```

| Field               | Description                                                       |
|---------------------|-------------------------------------------------------------------|
| `power`             | `ON` or `OFF` (`UNDEFINED` is a reading, not a command).          |
| `mode`              | `AUTO`, `COOL`, `HEAT`, `DRY`, `FAN_ONLY`.                        |
| `targetTemperature` | Target temperature in °C (number).                                |
| `fanSpeed`          | `AUTO`, `LOW`, `MEDIUM`, `HIGH`.                                  |

### Response `204 No Content`

The device accepted the command. No body: read the fresh state via
[`GET /devices/{uuid}/air-conditioner`](#get-devicesuuidair-conditioner) if needed (note the
provider's shadow state may lag the command by a few seconds).

### Response `400 Bad Request`

Returned when the body is empty, a field value is not admitted, or the device's catalog rejects the
value (e.g. temperature out of the device's admitted range). The message explains the rejection.

```json
{ "message": "Invalid mode 'FROSTY', allowed: AUTO, COOL, HEAT, DRY, FAN_ONLY" }
```

```json
{ "message": "Value '35' for 'tempSel' is out of the admitted range" }
```

### Response `404 Not Found`

Returned when `uuid` does not match any persisted device.

### Response `409 Conflict`

Returned when the device exists but is not an air conditioner (or its provider has no driver).
The no-driver case is deliberately folded into this 409: from the client's perspective the
actionable fact is the same — this device cannot be driven as an air conditioner (unlike the
diagnostics endpoint's 501, which flags a provider-level diagnostics capability gap).

```json
{ "message": "Device '<uuid>' is not an air conditioner!" }
```

### Response `502 Bad Gateway`

Returned when the provider is reached but returns an error, is unreachable, or the cloud rejects
the command; the failure is surfaced in the body.

```json
{ "message": "<provider failure>" }
```

### Response `500 Internal Server Error`

Returned when the persisted device cannot be read.

```json
{ "message": "Unable to retrieve device '<uuid>'!" }
```

## POST /devices/synchronizations

Synchronises the persisted device list with the current snapshot from all registered providers.

For each provider, devices are fetched and compared against the persisted list. The operation is
best-effort: a failure on a single provider or a single device does not abort the synchronisation.

### Request

No request body required.

### Response `200 OK`

```json
{
  "newDevices": [
    {
      "uuid": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "status": "PAIRED",
      "deviceProviderId": "abc-123",
      "provider": "SWITCHBOT",
      "name": "Living Room Sensor",
      "features": ["SENSOR"]
    }
  ],
  "updatedDevices": [...],
  "detachedDevices": [...]
}
```

| Field            | Description                                                                 |
|------------------|-----------------------------------------------------------------------------|
| `newDevices`     | Devices present in the provider snapshot but not previously persisted.      |
| `updatedDevices` | Devices present in both the provider snapshot and the system (name/status refreshed). |
| `detachedDevices`| Devices persisted in the system but absent from all providers (marked `DETACHED`). |

### Response `500 Internal Server Error`

Returned when the initial read of persisted devices fails.

```json
{ "message": "Device synchronization failed!" }
```
