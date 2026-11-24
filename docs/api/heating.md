# Heating API

## PUT /heating/enabled

Switches heating on or off by saving the `heating.enabled` flag. The `heating.enabled` flag decides
whether the heating evaluation drives the heaters. While the flag is `false`, the evaluation commands
no heater.

- **Enabling** commands no heater: the next evaluation drives them.
- **Disabling** saves the flag, then sends OFF once to every registered heater, assigned to an area or
  not. A registered heater is a persisted device whose driver is a heater. A failed OFF does not stop
  the others, and nothing retries it: the response lists it in `failedHeaters`, and the caller
  repeats the request. A repeated disable sends OFF again to every heater.
- **A disable during an evaluation** waits for the evaluation in progress to end, then sends its OFF
  commands. The heaters therefore end up off. The wait has no limit of its own: it ends when the
  provider calls of the evaluation end, each one bounded by its own timeout.

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

The flag is saved and, on a disable, every OFF is sent. The body repeats the saved value.

```json
{ "enabled": false }
```

When the OFF of some heaters fails, `failedHeaters` lists their uuids. The field is present only when
it is not empty.

```json
{ "enabled": false, "failedHeaters": ["3f1c9a52-6a1e-4c0b-9d7e-2b8f4e1a7c60"] }
```

| Field           | Description                                                         |
|-----------------|---------------------------------------------------------------------|
| `enabled`       | The saved value of the flag.                                        |
| `failedHeaters` | The uuids of the heaters whose OFF failed. Absent when none failed. |

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

Returned when the flag cannot be saved. The flag keeps its previous value, no heater receives a
command, and the stream clients receive no update.

```json
{ "message": "Unable to save the heating.enabled flag" }
```

Returned on a disable when the flag is saved but the registered heaters cannot be read. The flag stays
`false`, the stream clients receive the update, and no heater receives OFF. Repeating the request is
safe.

```json
{ "message": "Heating disabled, but the heaters were not turned off: repeat the request" }
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

**A disable turns the heaters off at once.** The evaluation commands no heater while heating is
disabled. Without the OFF commands, a heater that was on would stay on until heating is enabled again.

**A disable waits for an evaluation in progress.** The evaluation and the disable take one lock. Without
it, an evaluation that read `enabled = true` could send ON after the OFF of the disable, and the heater
would stay on. The lock lives in memory, so it holds for one application instance only. When a provider
does not respond during an evaluation, the response to a disable can arrive after some minutes.

**A failed OFF is reported, not retried.** The caller sees `failedHeaters` and repeats the request.
Every request sends OFF to every heater, whatever its status, so a repeated request is safe.

**A Netatmo Smarther follows its own schedule an hour after the OFF.** Its OFF sets the room setpoint
for one hour. After that hour the Smarther returns to the schedule configured in Netatmo, outside this
system, and follows it while heating stays disabled. If that schedule heats, the Smarther heats.

**The strategy name ignores case and surrounding spaces.** The endpoint, the heating evaluation and
`GET /home` read a strategy name through one function, `SharedHeatingStrategy.fromName`. The endpoint
therefore accepts exactly the spellings that the evaluation applies and that `GET /home` reports. The
endpoint saves the canonical name.
