# Providers API

## GET /providers/{provider}/diagnostics

Runs a named **read-only probe** against the provider's cloud and returns the raw response. This
extends the device-level diagnostics (`GET /devices/{uuid}/diagnostics`, the *provider's truth* for
one synced device) to the **provider** itself: it works even when no device has been synced yet, and
it is the tool of choice to re-capture the real payload shapes of a reverse-engineered provider in
any environment, without deploying ad-hoc code.

### ❗ Guardrails

- **Diagnostic contract, not an API contract.** The payload is the provider's raw response,
  verbatim: it carries **no schema guarantee** and may change or disappear at any time, without
  notice and without a changelog entry. **No client must build on this endpoint.**
- **Read-only, forever.** Probes never send commands and never mutate provider state. This is part
  of the `InspectableProvider` port contract and is enforced in review.
- **Redaction.** Tokens, credentials and auth headers never appear in responses or logs. Provider
  identifiers (MAC addresses, serials) are acceptable — the API is key-protected.
- **Requires the provider to be enabled** (its conditional gate, e.g. `providers.hon.enabled`):
  inspection uses the real credentials and counts as normal account usage.

### Path parameters

| Param      | Example | Description                                           |
|------------|---------|-------------------------------------------------------|
| `provider` | `hon`   | The provider to probe (case-insensitive enum name).   |

### Query parameters

| Param   | Example          | Description                                                    |
|---------|------------------|----------------------------------------------------------------|
| `probe` | `appliance-list` | **Required.** The probe to run.                                |
| *(any)* | `macAddress=...` | Probe-specific parameters, passed through to the backing call. |

### Probes — hOn

| Probe             | Required params                                        | Backing call                                  |
|-------------------|--------------------------------------------------------|-----------------------------------------------|
| `appliance-list`  | —                                                      | `POST /unified-api/v1/view/appliance-list`    |
| `context`         | `macAddress`, `applianceType`                          | `GET /commands/v1/context` (`category` defaults to `CYCLE`) |
| `commands`        | `macAddress`, `applianceType`, `applianceModelId`, `code` | `GET /commands/v1/retrieve` (`os`/`appVersion` defaulted; optional `firmwareId`, `fwVersion`, `series` passed through) |
| `appliance-model` | `macAddress`, `code`                                   | `GET /commands/v1/appliance-model`            |

SwitchBot and Netatmo do not expose provider-level probes yet — a retrofit is tracked as a
follow-up issue; their device-level diagnostics remain available via `GET /devices/{uuid}/diagnostics`.

### Walkthrough — querying hOn step by step

Everything starts from `appliance-list`: it needs no parameters, and its response carries every
value the other probes ask for. The typical session:

**1. Discover the appliances** (replace `$TOKEN` and host):

```bash
curl -H "Authorization: Bearer $TOKEN" \
  "https://<host>/providers/hon/diagnostics?probe=appliance-list"
```

The appliances live at `modules.applianceList.payload.appliances[]` (observed shape, 2026-07 —
**no guarantee**, that is the point of this endpoint). Each entry carries the fields the other
probes need. Note the trap: the `applianceType` *parameter* comes from the **`applianceTypeName`**
field (e.g. `"WM"`, `"AC"`, `"REF"`), not from the payload's `applianceType`.

| Probe param        | Field in the `appliances[]` entry |
|--------------------|-----------------------------------|
| `macAddress`       | `macAddress`                      |
| `applianceType`    | `applianceTypeName`               |
| `applianceModelId` | `applianceModelId`                |
| `code`             | `code`                            |
| `firmwareId` *(opt)* | `firmwareId`                    |
| `fwVersion` *(opt)*  | `fwVersion`                     |
| `series` *(opt)*     | `series`                        |

**2. Current state of an appliance** (the polling call — live attribute values):

```bash
curl -H "Authorization: Bearer $TOKEN" \
  "https://<host>/providers/hon/diagnostics?probe=context&macAddress=<mac>&applianceType=WM"
```

**3. Command/program catalog** (what the appliance can do; the optional identifiers narrow the
catalog to the exact firmware — pass them when the appliance entry has them):

```bash
curl -H "Authorization: Bearer $TOKEN" \
  "https://<host>/providers/hon/diagnostics?probe=commands&macAddress=<mac>&applianceType=WM&applianceModelId=<id>&code=<code>&series=<series>"
```

**4. Model sheet** (static description of the model):

```bash
curl -H "Authorization: Bearer $TOKEN" \
  "https://<host>/providers/hon/diagnostics?probe=appliance-model&macAddress=<mac>&code=<code>"
```

A wrong or misspelled probe name answers `400` listing the available probes; missing params answer
`400` listing the expected set — the endpoint is self-describing, so when in doubt just call it.
The Bruno collection (`bruno/providers/`) has a ready-made request with all the optional params
pre-wired as disabled entries.

### Response `200 OK`

`Content-Type: application/json` — the provider's raw body, written through unchanged. If the
provider answers 200 with a **non-JSON body** (e.g. an HTML maintenance page), the body is wrapped
in a JSON envelope instead of being dropped:

```json
{ "nonJsonBody": "<html>maintenance</html>" }
```

### Response `400 Bad Request`

Self-describing hints. Unknown probe:

```json
{
  "message": "Unknown probe 'send-command' for provider 'HON'!",
  "availableProbes": ["appliance-list", "context", "commands", "appliance-model"]
}
```

Missing required probe params:

```json
{
  "message": "Probe 'context' is missing required params!",
  "expectedParams": ["macAddress", "applianceType"]
}
```

A missing `probe` query parameter is also a `400`.

### Response `404 Not Found`

Returned when `{provider}` does not match any known provider.

### Response `501 Not Implemented`

Returned when the provider is known but has no diagnostics implementation (or its conditional gate
is disabled, so no implementation is registered).

```json
{ "message": "Diagnostics is not available for provider 'NETATMO'!" }
```

### Response `502 Bad Gateway`

Returned when the provider is reached but errors or is unreachable; the transport failure is
surfaced in the body.

```json
{ "message": "HonServerError(statusCode=500)" }
```
