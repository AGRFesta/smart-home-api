# Alerts API

## GET /alerts

Returns alerts as a flat collection. By default it returns the currently **OPEN** alerts — the canonical
"state visible in the model" and the single source of truth on the read side. Other read models (home
dashboard, device status) will derive a minimal projection from the same data and do **not** duplicate it
(see [ALERTS.md](../domain/ALERTS.md)).

Pagination and sorting are out of scope: the full matching list is returned.

### Query parameters

| Param    | Example    | Description                                                              |
|----------|------------|--------------------------------------------------------------------------|
| `status` | `RESOLVED` | Filter by status (`OPEN`, `RESOLVED`). When omitted, defaults to `OPEN`. |

A `status` value outside `OPEN` / `RESOLVED` yields `400 Bad Request`.

### Response `200 OK`

A JSON array of alerts. An empty result is returned as an empty array (`[]`), not an error.

`openedAt` / `resolvedAt` are Unix epoch seconds (consistent with all timestamps in this API);
`resolvedAt` is `null` while the alert is `OPEN`.

`type` is an extensible enum (`BATTERY_LOW`, `DEVICE_DETACHED`, ...). `scope` describes how to read
`target`: device uuid for `DEVICE`, provider id for `PROVIDER`, absent (`null`) for `GLOBAL`. `details` is
a small, free-form payload describing what tripped the alert (`null` when not set).

```json
[
  {
    "uuid": "a87454c6-6949-4449-a932-1b953212dd49",
    "type": "BATTERY_LOW",
    "scope": "DEVICE",
    "target": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "status": "OPEN",
    "openedAt": 1718460338,
    "resolvedAt": null,
    "details": "battery=10%"
  }
]
```

### Response `400 Bad Request`

Returned when the `status` query parameter is not one of `OPEN` / `RESOLVED`.

### Response `500 Internal Server Error`

Returned when the persisted alerts cannot be read.

```json
{ "message": "Unable to retrieve alerts!" }
```
