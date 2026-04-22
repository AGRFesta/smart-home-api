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
