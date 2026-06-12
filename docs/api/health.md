# Resource: Health & Info (Actuator)

Operational endpoints exposed via Spring Boot Actuator. Only `health` and `info` are exposed;
every other actuator endpoint (`env`, `beans`, `metrics`, …) is **not** reachable.

Unlike the rest of the API, the health endpoints are **partially public** so that a container
orchestrator can probe the service without an API key. See [SECURITY.md](../SECURITY.md).

## GET /actuator/health

Aggregated health of the application and its infrastructure (`db`, `redis`, `diskSpace`).

### Authentication

**Public** — the aggregated status is returned without a token. Component **details** are shown
only to authenticated callers (`show-details=when-authorized`); anonymous callers receive the
status alone.

### Response: `200 OK` (anonymous)

```json
{ "status": "UP" }
```

### Response: `200 OK` (authenticated)

```json
{
  "status": "UP",
  "components": {
    "db":        { "status": "UP" },
    "redis":     { "status": "UP" },
    "diskSpace": { "status": "UP" }
  }
}
```

> ❗ The aggregate is `DOWN` if **any** component (including Redis) is down. Do **not** use it as
> the orchestrator probe — use `GET /actuator/health/readiness` instead.

### HTTP Status Codes

| Status | Condition |
|--------|-----------|
| `200`  | Application is healthy (`status: UP`) |
| `503`  | One or more components are down (`status: DOWN`) |

---

## GET /actuator/health/liveness

Liveness probe — whether the application process is alive. It deliberately has **no external
dependencies** (DB, Redis): a transient infrastructure outage must not be reported as the process
being dead.

### Authentication

**Public** — no token required.

### Response: `200 OK`

```json
{ "status": "UP" }
```

### HTTP Status Codes

| Status | Condition |
|--------|-----------|
| `200`  | Application process is live (`status: UP`) |
| `503`  | Application liveness state is broken (`status: DOWN`) |

---

## GET /actuator/health/readiness

Readiness probe **intended for** a container/orchestrator readiness check. It is intentionally
**narrower** than the aggregate: it reports `UP` only when the application and the **database** are
healthy.

### Authentication

**Public** — no token required. Component details are shown only to authenticated callers.

### Response: `200 OK` (authenticated)

```json
{
  "status": "UP",
  "components": {
    "db":             { "status": "UP" },
    "readinessState": { "status": "UP" }
  }
}
```

> **Redis is deliberately excluded** from readiness: it is a cache, and an outage must not take
> the app out of rotation. Redis still appears in the full `/actuator/health` aggregate as an
> informational component.

### HTTP Status Codes

| Status | Condition |
|--------|-----------|
| `200`  | Application and database are ready (`status: UP`) |
| `503`  | Application or database not ready (`status: DOWN`) |

---

## GET /actuator/info

Exposes the application build metadata (version).

### Authentication

Bearer token **required** — returns `401 Unauthorized` without a valid token. This avoids leaking
the running version to anonymous callers.

### Response: `200 OK`

```json
{
  "build": {
    "artifact": "app",
    "name": "smart-home-api",
    "version": "1.1.0",
    "group": "org.agrfesta.sh"
  }
}
```

### HTTP Status Codes

| Status | Condition |
|--------|-----------|
| `200`  | Build info returned |
| `401`  | Missing, empty, or invalid Bearer token |
