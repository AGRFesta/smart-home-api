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
