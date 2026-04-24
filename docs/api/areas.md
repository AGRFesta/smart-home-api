# Resource: Areas

## GET /areas/{areaId}/heating-schedule

Returns the heating schedule for the given area. If the area has no schedule configured, returns a default empty structure.

### Authentication

Bearer token required. Returns `401 Unauthorized` if the token is missing or invalid.

### Path Parameters

| Parameter | Type   | Description          |
|-----------|--------|----------------------|
| `areaId`  | `UUID` | The target area's id |

### Response: `200 OK` — schedule exists

```json
{
  "defaultTemperature": 21.5,
  "intervals": [
    {
      "temperature": 18.0,
      "startTime": "23:00",
      "endTime": "06:30"
    }
  ]
}
```

### Response: `200 OK` — no schedule configured

```json
{
  "defaultTemperature": 20.0,
  "intervals": []
}
```

### Response: `404 Not Found` — area does not exist

```json
{ "message": "Area with id '<areaId>' is missing!" }
```

### Response: `500 Internal Server Error`

```json
{ "message": "Internal server error while retrieving heating schedule." }
```

### HTTP Status Codes

| Status | Condition                                                       |
|--------|-----------------------------------------------------------------|
| `200`  | Schedule returned (or default empty structure if none exists)   |
| `401`  | Missing, empty, or invalid Bearer token                         |
| `404`  | Area not found                                                  |
| `500`  | Persistence failure                                             |

---

## PUT /areas/{areaId}/heating-schedule

Creates or entirely replaces the heating schedule for the given area.

### Authentication

Bearer token required. Returns `401 Unauthorized` if the token is missing or invalid.

### Path Parameters

| Parameter | Type   | Description          |
|-----------|--------|----------------------|
| `areaId`  | `UUID` | The target area's id |

### Request Body

```json
{
  "defaultTemperature": 21.5,
  "intervals": [
    {
      "temperature": 18.0,
      "startTime": "23:00",
      "endTime": "06:30"
    }
  ]
}
```

| Field                       | Type     | Notes                                        |
|-----------------------------|----------|----------------------------------------------|
| `defaultTemperature`        | `number` | Celsius; used when no interval is active     |
| `intervals`                 | `array`  | May be empty                                 |
| `intervals[].temperature`   | `number` | Celsius                                      |
| `intervals[].startTime`     | `string` | `HH:mm` format                              |
| `intervals[].endTime`       | `string` | `HH:mm` format; may cross midnight           |

### Response: `200 OK`

Returns the saved schedule.

```json
{
  "defaultTemperature": 21.5,
  "intervals": [
    {
      "temperature": 18.0,
      "startTime": "23:00",
      "endTime": "06:30"
    }
  ]
}
```

| Field                       | Type     | Notes                              |
|-----------------------------|----------|------------------------------------|
| `defaultTemperature`        | `number` | Celsius                            |
| `intervals[].temperature`   | `number` | Celsius                            |
| `intervals[].startTime`     | `string` | `HH:mm`                           |
| `intervals[].endTime`       | `string` | `HH:mm`                           |

### Response: `422 Unprocessable Entity` — overlapping intervals

```json
{
  "type": "about:blank",
  "title": "Overlapping Intervals",
  "status": 422,
  "detail": "The provided heating intervals overlap."
}
```

### Response: `404 Not Found` — area does not exist

```json
{ "message": "Area with id '<areaId>' is missing!" }
```

### Response: `500 Internal Server Error`

```json
{ "message": "Internal server error while saving heating schedule." }
```

### HTTP Status Codes

| Status | Condition                                         |
|--------|---------------------------------------------------|
| `200`  | Schedule saved; body contains the updated resource |
| `401`  | Missing, empty, or invalid Bearer token           |
| `404`  | Area not found                                    |
| `422`  | One or more intervals overlap                     |
| `500`  | Persistence failure                               |

---

## DELETE /areas/{areaId}/heating-schedule

Deletes the heating schedule for the given area.

### Authentication

Bearer token required. Returns `401 Unauthorized` if the token is missing or invalid.

### Path Parameters

| Parameter | Type   | Description          |
|-----------|--------|----------------------|
| `areaId`  | `UUID` | The target area's id |

### Response: `204 No Content`

Schedule deleted successfully. No response body.

### Response: `404 Not Found` — area does not exist

```json
{ "message": "Area with id '<areaId>' is missing!" }
```

### Response: `500 Internal Server Error`

```json
{ "message": "Internal server error while deleting heating schedule." }
```

### HTTP Status Codes

| Status | Condition                               |
|--------|-----------------------------------------|
| `204`  | Schedule deleted successfully           |
| `401`  | Missing, empty, or invalid Bearer token |
| `404`  | Area not found                          |
| `500`  | Persistence failure                     |
