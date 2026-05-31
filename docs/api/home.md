# Resource: Home

## GET /home

Returns the full dashboard for the BFF frontend. Aggregates global heating state and per-area
measurements. Individual fields that cannot be fetched are reported as **field failures** rather
than failing the entire request (see [FieldResult](#fieldresult)).

### Authentication

Bearer token required. Returns `401 Unauthorized` if the token is missing or invalid.

### Response: `200 OK`

```json
{
  "globalState": {
    "heatingActive": { "type": "success", "value": true },
    "strategy":      { "type": "success", "value": "COMFORT" }
  },
  "areas": [
    {
      "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "name": "Living Room",
      "measurements": {
        "heating": {
          "currentTemperature": { "type": "success", "value": 21.5 },
          "targetTemperature":  { "type": "success", "value": 22.0 }
        },
        "humidity": {
          "relative": { "type": "success", "value": 45.5 }
        }
      }
    }
  ]
}
```

#### Schema

| Field | Type | Notes |
|---|---|---|
| `globalState.heatingActive` | `FieldResult<boolean>` | Whether the shared heater is currently active |
| `globalState.strategy` | `FieldResult<"ECONOMY" \| "COMFORT" \| null>` | Active heating strategy; `null` if none configured |
| `areas[].id` | `UUID` | Area identifier |
| `areas[].name` | `string` | Area display name |
| `areas[].measurements.heating` | `HeatingMeasurements \| null` | `null` if no heating device assigned to the area |
| `areas[].measurements.heating.currentTemperature` | `FieldResult<number \| null>` | Celsius, 2 decimal places; `null` if no reading available |
| `areas[].measurements.heating.targetTemperature` | `FieldResult<number \| null>` | Celsius, 2 decimal places; `null` if no target configured |
| `areas[].measurements.humidity` | `HumidityMeasurements \| null` | `null` if no humidity device assigned to the area |
| `areas[].measurements.humidity.relative` | `FieldResult<number \| null>` | Relative humidity %; `null` if no reading available |

### Response: `500 Internal Server Error`

Returned only for catastrophic failures where the dashboard cannot be assembled at all
(e.g. the area list is unavailable).

```json
{ "message": "Unable to fetch home dashboard!" }
```

### HTTP Status Codes

| Status | Condition |
|--------|-----------|
| `200`  | Dashboard assembled (field failures may be present inside the payload) |
| `401`  | Missing, empty, or invalid Bearer token |
| `500`  | Catastrophic failure — dashboard could not be assembled |

---

## GET /home/stream

Opens a long-lived [Server-Sent Events](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events)
connection that pushes the **same `HomeDashboardResponse` payload** as `GET /home` whenever the home state
changes, so the client never has to poll. See [HOME_STREAM.md](../domain/HOME_STREAM.md) for the data flow and
design decisions.

`GET /home` is **not** replaced — it remains the one-shot endpoint for clients where a persistent connection is
not suitable (widgets, Wear OS, one-off queries).

### Authentication

Bearer token required, same as `GET /home`. Returns `401 Unauthorized` if the token is missing or invalid.

### Response: `200 OK`

`Content-Type: text/event-stream`. The connection stays open. Each message is a standard SSE `data:` event whose
body is a `HomeDashboardResponse` (identical schema to `GET /home`).

1. **Initial event** — sent immediately on connection, carrying the current dashboard. The client does not wait
   for the first change.
2. **Update events** — one full payload is pushed after every completed device-polling cycle and immediately
   after any heating configuration change (target temperature, heating schedule, shared strategy, heating
   enabled flag).
3. **Keep-alive** — a comment-only line (`: ping`) is emitted periodically so idle connections are not closed by
   proxies or load balancers. Clients ignore comment lines automatically.

```
data:{"globalState":{...},"areas":[...]}

: ping

data:{"globalState":{...},"areas":[...]}

```

The payload is always the **full** dashboard — there are no delta/patch events. Clients replace their current
state on each event (updates are idempotent).

### HTTP Status Codes

| Status | Condition |
|--------|-----------|
| `200`  | Stream opened; initial event delivered (`text/event-stream`) |
| `401`  | Missing, empty, or invalid Bearer token |

> If a polling cycle fails, **no event is published** and connected clients simply retain their last received
> state. Emitters whose client has disconnected are dropped from the server registry automatically.

---

## FieldResult

Every failable field uses a **discriminated union** with a `type` property.

**Success**
```json
{ "type": "success", "value": <T> }
```

**Failure** — the field could not be fetched; the frontend should display a degraded state
(e.g. a warning icon) rather than treating the whole page as broken.
```json
{ "type": "failure", "error": "<reason>" }
```

Nullable values (`T | null`) inside a `FieldSuccess` carry the usual meaning:
`null` means "not configured" or "no reading yet", not a fetch error.
