# Resource: Areas

## POST /areas

Creates a new area.

### Authentication

Bearer token required. Returns `401 Unauthorized` if the token is missing or invalid.

### Request Body

```json
{ "name": "Living Room", "isIndoor": true }
```

| Field      | Type      | Required | Notes                              |
|------------|-----------|----------|------------------------------------|
| `name`     | `string`  | Yes      | Must be unique                     |
| `isIndoor` | `boolean` | No       | Defaults to `true` when omitted    |

### Response: `201 Created`

```json
{ "message": "Area 'Living Room' successfully created!", "resourceId": "<uuid>" }
```

### Response: `400 Bad Request` — name already exists

```json
{ "message": "An Area 'Living Room' already exists!" }
```

### Response: `500 Internal Server Error`

```json
{ "message": "Unable to create Area 'Living Room'!" }
```

### HTTP Status Codes

| Status | Condition                               |
|--------|-----------------------------------------|
| `201`  | Area created; body contains the new id |
| `400`  | An area with the same name exists       |
| `401`  | Missing, empty, or invalid Bearer token |
| `500`  | Persistence failure                     |

---

## GET /areas

Returns all persisted areas.

### Authentication

Bearer token required. Returns `401 Unauthorized` if the token is missing or invalid.

### Response: `200 OK`

```json
[
  { "uuid": "<uuid>", "name": "Living Room", "isIndoor": true },
  { "uuid": "<uuid>", "name": "Garden",      "isIndoor": false }
]
```

### Response: `500 Internal Server Error`

```json
{ "message": "Unable to retrieve areas!" }
```

### HTTP Status Codes

| Status | Condition                               |
|--------|-----------------------------------------|
| `200`  | List returned (may be empty)            |
| `401`  | Missing, empty, or invalid Bearer token |
| `500`  | Persistence failure                     |

---

## GET /areas/{id}

Returns a single area by its unique identifier.

### Authentication

Bearer token required. Returns `401 Unauthorized` if the token is missing or invalid.

### Path Parameters

| Parameter | Type   | Description        |
|-----------|--------|--------------------|
| `id`      | `UUID` | The area's id      |

### Response: `200 OK`

```json
{ "uuid": "<uuid>", "name": "Living Room", "isIndoor": true }
```

### Response: `404 Not Found` — area does not exist

No response body.

### Response: `500 Internal Server Error`

```json
{ "message": "Unable to retrieve area '<id>'!" }
```

### HTTP Status Codes

| Status | Condition                               |
|--------|-----------------------------------------|
| `200`  | Area returned                           |
| `401`  | Missing, empty, or invalid Bearer token |
| `404`  | Area not found                          |
| `500`  | Persistence failure                     |

---

## PUT /areas/{id}

Updates an existing area's name and indoor flag.

### Authentication

Bearer token required. Returns `401 Unauthorized` if the token is missing or invalid.

### Path Parameters

| Parameter | Type   | Description        |
|-----------|--------|--------------------|
| `id`      | `UUID` | The area's id      |

### Request Body

```json
{ "name": "Kitchen", "isIndoor": true }
```

| Field      | Type      | Required | Notes          |
|------------|-----------|----------|----------------|
| `name`     | `string`  | Yes      | Must be unique |
| `isIndoor` | `boolean` | Yes      |                |

### Response: `200 OK`

```json
{ "uuid": "<uuid>", "name": "Kitchen", "isIndoor": true }
```

### Response: `400 Bad Request` — name already taken

```json
{ "message": "An Area 'Kitchen' already exists!" }
```

### Response: `404 Not Found` — area does not exist

No response body.

### Response: `500 Internal Server Error`

```json
{ "message": "Unable to update area '<id>'!" }
```

### HTTP Status Codes

| Status | Condition                               |
|--------|-----------------------------------------|
| `200`  | Area updated; body contains updated resource |
| `400`  | Another area with the same name exists  |
| `401`  | Missing, empty, or invalid Bearer token |
| `404`  | Area not found                          |
| `500`  | Persistence failure                     |

---

## DELETE /areas/{id}

Deletes an area by its unique identifier.

### Authentication

Bearer token required. Returns `401 Unauthorized` if the token is missing or invalid.

### Path Parameters

| Parameter | Type   | Description        |
|-----------|--------|--------------------|
| `id`      | `UUID` | The area's id      |

### Response: `204 No Content`

Area deleted successfully. No response body.

### Response: `404 Not Found` — area does not exist

No response body.

### Response: `500 Internal Server Error`

```json
{ "message": "Unable to delete area '<id>'!" }
```

### HTTP Status Codes

| Status | Condition                               |
|--------|-----------------------------------------|
| `204`  | Area deleted successfully               |
| `401`  | Missing, empty, or invalid Bearer token |
| `404`  | Area not found                          |
| `500`  | Persistence failure                     |

---

## PUT /areas/{areaId}/sensors/{deviceId}

Assigns a device as a sensor to the given area.

### Authentication

Bearer token required. Returns `401 Unauthorized` if the token is missing or invalid.

### Path Parameters

| Parameter  | Type   | Description              |
|------------|--------|--------------------------|
| `areaId`   | `UUID` | The target area's id     |
| `deviceId` | `UUID` | The device to assign     |

### Response: `204 No Content`

Sensor assigned successfully (or already assigned to the same area — idempotent). No response body.

### Response: `400 Bad Request` — device is not a sensor

```json
{ "message": "Device with id '<deviceId>' is not a sensor!" }
```

### Response: `400 Bad Request` — sensor already assigned to another area

```json
{ "message": "Device with id '<deviceId>' is already assigned to another area!" }
```

### Response: `404 Not Found` — area does not exist

```json
{ "message": "Area with id '<areaId>' is missing!" }
```

### Response: `404 Not Found` — device does not exist

```json
{ "message": "Device with id '<deviceId>' is missing!" }
```

### HTTP Status Codes

| Status | Condition                                                              |
|--------|------------------------------------------------------------------------|
| `204`  | Sensor assigned (or already assigned to the same area — idempotent)   |
| `400`  | Device is not a sensor, or already assigned to a different area        |
| `401`  | Missing, empty, or invalid Bearer token                                |
| `404`  | Area or device not found                                               |
| `500`  | Persistence failure                                                    |

---

## PUT /areas/{areaId}/actuators/{deviceId}

Assigns a device as an actuator to the given area.

### Authentication

Bearer token required. Returns `401 Unauthorized` if the token is missing or invalid.

### Path Parameters

| Parameter  | Type   | Description              |
|------------|--------|--------------------------|
| `areaId`   | `UUID` | The target area's id     |
| `deviceId` | `UUID` | The device to assign     |

### Response: `204 No Content`

Actuator assigned successfully (or already assigned to the same area — idempotent). No response body.

### Response: `400 Bad Request` — device is not an actuator

```json
{ "message": "Device with id '<deviceId>' is not an actuator!" }
```

### Response: `404 Not Found` — area does not exist

```json
{ "message": "Area with id '<areaId>' is missing!" }
```

### Response: `404 Not Found` — device does not exist

```json
{ "message": "Device with id '<deviceId>' is missing!" }
```

### HTTP Status Codes

| Status | Condition                                                                 |
|--------|---------------------------------------------------------------------------|
| `204`  | Actuator assigned (or already assigned to the same area — idempotent)    |
| `400`  | Device is not an actuator                                                 |
| `401`  | Missing, empty, or invalid Bearer token                                   |
| `404`  | Area or device not found                                                  |
| `500`  | Persistence failure                                                       |

---

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
