# Devices API

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
