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
