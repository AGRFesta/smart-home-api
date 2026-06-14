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

Returns the per-device **aggregate** — *our truth*, the persisted view of a single device: its base
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
  "batteryLevel": 88
}
```

| Field          | Description                                                                       |
|----------------|-----------------------------------------------------------------------------------|
| `assignments`  | Current area assignments; each has `areaUuid`, `areaName` and `role` (`SENSOR`/`ACTUATOR`). |
| `batteryLevel` | Latest known battery percentage (0–100) from cache; `null` when not battery-powered, not yet collected, or expired. |

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
aggregate).

It is a rarely-used **diagnostic passthrough**: the backend resolves the device, knows which provider to
call (and does the signed/encrypted/token work the provider requires), and writes the provider response
through **verbatim**. Nothing is persisted or cached — the response reflects exactly what the provider
says at call time, **including failures** (the diagnostic value lives in not masking them).

The response shape is **provider-specific** and intentionally not described by a fixed schema (e.g.
SwitchBot returns the device status; Netatmo returns the device's room status). Fields our domain does not
model (e.g. `hubDeviceId`, `enableCloudService`) are included as-is.

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
