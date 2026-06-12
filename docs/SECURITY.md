# Security — Smart Home API

## Authentication Model

All application endpoints require authentication, **except the public Actuator health probes**
(`/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`), which return their
status without a token so a container orchestrator can probe the service. Component **details** are
still shown only to authenticated callers (`show-details=when-authorized`). Every other Actuator
endpoint (e.g. `/actuator/info`) requires authentication.

The mechanism is a **static API key validated via SHA-256 hash** (`SimpleApiKeyFilter`). The raw token is never stored — only its SHA-256 hex digest is held in configuration.

### Request flow

```
HTTP Request
  └─ Authorization: Bearer <raw-token>
       └─ SimpleApiKeyFilter (OncePerRequestFilter)
            ├─ missing/malformed header  → 401
            ├─ SHA-256(raw-token) ≠ config hash  → 401
            ├─ config hash blank  → 500 (misconfiguration)
            └─ match → SecurityContext ← UsernamePasswordAuthenticationToken
                          principal   = "api-client"
                          authorities = [ROLE_API]
                          → filterChain.doFilter() continues
```

### Configuration

`application.properties` (or environment override):

```properties
security.api-token-hash=<sha256-hex-of-your-token>
```

Generate the hash for a chosen token:

```bash
echo -n "your-raw-token" | sha256sum
# or on macOS
echo -n "your-raw-token" | shasum -a 256
```

The raw token goes in the `Authorization` header at call time; only the hex digest goes into config.

---

## Constraints Relevant to Development

- **Single identity** — every valid request is authenticated as `"api-client"` with `ROLE_API`. There is no per-user or per-role differentiation. Controllers must not assume anything beyond `ROLE_API` being present.
- **No sessions, no CSRF** — the API is fully stateless; CSRF protection is disabled in `SecurityConfig`.
- **No token expiry or revocation** — rotation requires changing `security.api-token-hash` in config and redeploying. There is no blacklist.
- **All routes protected except public health probes** — `SecurityConfig` applies `.anyRequest().authenticated()`, with `permitAll()` only for the `PUBLIC_HEALTH_ENDPOINTS` (health/liveness/readiness probes). The same list drives the anonymous bypass in `SimpleApiKeyFilter`, so the two stay in sync. Adding any other endpoint automatically inherits the authenticated requirement; no extra annotation is needed.
- **Public probes are token-agnostic** — a missing, empty, or invalid token on a public health probe is ignored (the probe still returns its public status), never `401`. A *valid* token on `/actuator/health` still elevates the caller to see component details.

---

## Adding New Endpoints

No action needed to protect a new controller endpoint — the filter covers everything. To write an MVC slice test (`@WebMvcTest`) that exercises the authenticated path, use the test helpers in `controllers/Utils.kt`:

```kotlin
// MockMvc (unit/slice tests)
mockMvc.perform(get("/your-endpoint").authenticated())

// RestAssured (integration tests)
given().authenticated().get("/your-endpoint")

// Unauthenticated path (expect 401)
given().wrongAuthentication().get("/your-endpoint")
```

The test token is configured in `src/test/resources/application-test.yml` under `security.api-token-hash`. Do not change that value — all existing tests depend on it.
