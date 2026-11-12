# Heating API

## PUT /heating/enabled

Switches heating on or off by saving the `heating.enabled` flag. The `heating.enabled` flag decides
whether the heating evaluation drives the heaters. This endpoint commands no heater: the next
evaluation applies the new value.

A save sends one update to the clients of [`GET /home/stream`](home.md#get-homestream), and
[`GET /home`](home.md#get-home) reports the flag as `globalState.heatingActive`.

### Authentication

Bearer token required. Returns `401 Unauthorized` if the token is missing or invalid.

### Request

`Content-Type: application/json`

```json
{ "enabled": false }
```

| Field     | Description                                                     |
|-----------|-----------------------------------------------------------------|
| `enabled` | `true` enables heating, `false` disables it. A JSON boolean only. |

### Response `200 OK`

The flag is saved. The body repeats the saved value.

```json
{ "enabled": false }
```

### Response `400 Bad Request`

Returned when the body is not a JSON object whose `enabled` is a JSON boolean: `enabled` is missing,
`null`, a string (`"true"` included) or a number, or the body is missing, an array, or does not
begin with a JSON value. Only the first JSON value in the body is read; any text after it is
ignored. The flag keeps its previous value.

```json
{ "message": "The body must be a JSON object with a boolean 'enabled' field" }
```

### Response `415 Unsupported Media Type`

Returned when the `Content-Type` is not `application/json`.

### Response `500 Internal Server Error`

Returned when the flag cannot be saved. The flag keeps its previous value and the stream clients
receive no update.

```json
{ "message": "Unable to save the heating.enabled flag" }
```

## PUT /heating/strategy

Sets the shared heating strategy by saving the `heating.strategy` property. The shared heating
strategy decides how the heating evaluation drives the heaters shared by several areas: `ECONOMY` or
`COMFORT`. This endpoint commands no heater: the next evaluation applies the new value.

A save sends at least one update to the clients of [`GET /home/stream`](home.md#get-homestream), and
[`GET /home`](home.md#get-home) reports the strategy as `globalState.strategy`.

### Authentication

Bearer token required. Returns `401 Unauthorized` if the token is missing or invalid.

### Request

`Content-Type: application/json`

```json
{ "strategy": "COMFORT" }
```

| Field      | Description                                                                              |
|------------|------------------------------------------------------------------------------------------|
| `strategy` | `ECONOMY` or `COMFORT`. A JSON string only, matched ignoring case and surrounding spaces. |

### Response `200 OK`

The strategy is saved in its canonical form, in upper case. The body repeats the saved value.

```json
{ "strategy": "COMFORT" }
```

### Response `400 Bad Request`

Returned when the body is not a JSON object with a string `strategy` field that names `ECONOMY` or
`COMFORT`. The strategy keeps its previous value.

```json
{ "message": "The body must be a JSON object with a 'strategy' field: one of ECONOMY, COMFORT" }
```

### Response `415 Unsupported Media Type`

Returned when the `Content-Type` is not `application/json`.

### Response `500 Internal Server Error`

Returned when the strategy cannot be saved. The strategy keeps its previous value and the stream
clients receive no update.

```json
{ "message": "Unable to save the heating strategy" }
```

---

# Design notes

**Only a JSON boolean is accepted.** The shared JSON mapper converts `"true"` and `1` into a boolean.
A client that sends a string or a number has a bug, and converting its value would hide that bug.
The endpoint reads the raw JSON instead of relaxing the mapper, because the mapper serves every
other endpoint.

**The strategy name ignores case and surrounding spaces.** The endpoint, the heating evaluation and
`GET /home` read a strategy name through one function, `SharedHeatingStrategy.fromName`. The endpoint
therefore accepts exactly the spellings that the evaluation applies and that `GET /home` reports. The
endpoint saves the canonical name.
