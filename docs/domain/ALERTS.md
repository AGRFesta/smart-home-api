# Alert Domain Model

## Overview

An `Alert` is a **stateful, idempotent** domain aggregate representing a condition that has become true and
must be surfaced. It opens when a condition first becomes true, stays `OPEN` while the condition persists,
and becomes `RESOLVED` once the condition clears.

The alert state is the **source of truth**: notifications are a projection of it. An alert is the only
piece of this state that must survive a restart — losing it would re-fire notifications for a condition
already known and already notified. Alerts therefore live exclusively on the DB; runtime memory and cache
are deliberately excluded.

---

## Aggregate

| Field       | Meaning |
|-------------|---------|
| `uuid`      | Surrogate identifier (PK). |
| `type`      | Extensible enum (`BATTERY_LOW`, `DEVICE_DETACHED`, ...). The aggregate is agnostic of the concrete semantics — the rule evaluating the condition owns them. |
| `target`    | What the alert is about, as a sealed `AlertTarget`: `Device(uuid)`, `Provider(provider)` or `Global`. Its `scope` (`DEVICE` / `PROVIDER` / `GLOBAL`) is derived. |
| `openedAt`  | When the condition first became true. |
| `lifecycle` | Sealed `AlertLifecycle`: `Open`, or `Resolved(resolvedAt)`. Exposes the derived `status` (`OPEN` / `RESOLVED`). |
| `details`   | Small free-form payload describing what tripped the alert (e.g. the offending value). |

**Illegal states are unrepresentable by construction:** `target` couples scope and reference (a `Global`
alert cannot carry a reference, nor a `Device` one lack a uuid), and `lifecycle` couples status and
`resolvedAt` (an `Open` alert has no resolution instant, a `Resolved` one always has).

Timestamps are always supplied by the `TimeProvider` outbound port, never read from the clock inside the
aggregate, keeping the lifecycle pure and unit-testable.

---

## Lifecycle / state machine

The lifecycle is a **pure** function, `evaluateAlert`, in the core domain. It takes the currently open
alert (or `null`) and a single boolean — whether the condition is currently met — and returns the
`AlertTransition` to apply to storage:

```
                condition met
   (none) ───────────────────────────────► Opened     persist a new OPEN alert
   (none) ── condition not met ───────────► Unchanged

   OPEN   ── condition still met ─────────► Unchanged  idempotent: no new alert
   OPEN   ── condition cleared ───────────► Resolved   update to RESOLVED, set resolvedAt
```

The aggregate is condition-agnostic: it reacts only to the boolean `conditionMet` and never decides *how*
that boolean is computed. `Opened` / `Resolved` / `Unchanged` carry the resulting state so the caller only
has to project it onto storage.

### Idempotency

Re-evaluating a still-true condition yields `Unchanged` — no duplicate alert. This is enforced at **two**
levels:

1. **Domain:** an already-open alert + condition met ⇒ `Unchanged`.
2. **Storage (safety net):** a partial unique index guarantees **at most one `OPEN` alert per
   `(type, target)`**, so even a race cannot create a duplicate. `target` is folded with
   `COALESCE(target, '')` so `GLOBAL` alerts (null target) are constrained too. A second concurrent create
   surfaces as `AlertAlreadyOpen`.

---

## Evaluation: rules and the polling hook

Alert state is driven by an **evaluation engine** (`EvaluateAlertsService`) that runs on the **success
path of the polling cycle** (`FetchSensorReadingsService`), right **before** the `HomeStateRefresh`
publish — so the snapshot pushed over SSE in the same cycle already reflects the just-opened/resolved
alerts (see [HOME_STREAM.md](HOME_STREAM.md)). There is no dedicated timer: the cadence is inherited from
the polling scheduler, and a **failed polling cycle triggers no evaluation** — no transitions on stale
data.

The engine is a pure consumer of state: it reads battery values from the cache port and open alerts from
the repository; it never calls device drivers. If the open alerts cannot be read, the whole evaluation is
skipped for that cycle (without the current state a safe diff is impossible); a single device whose
lookup fails is skipped without stopping the others.

The inbound port (`EvaluateAlertsUseCase`) deliberately returns `Unit`: every failure is a tolerated
degradation on the polling success path and the caller has no meaningful branch to take today. This is a
conscious exception to the `Either`-returning port rule, scoped to #193 — a typed outcome report
(mirroring the heating evaluation design of #201) is planned for #195 together with the rule abstraction,
so the report is designed once for N rules.

`details` is a **snapshot at open time** (the value that tripped the rule), not current state: an
`Unchanged` transition never touches storage, so the payload is not refreshed while the alert stays open.

### `BATTERY_LOW` rule

The first concrete rule compares the cached battery level (numeric %, SwitchBot semantics — see #191 for
the deferred Netatmo normalization) against a **global** two-threshold hysteresis band, configured via
Spring properties:

| Property                     | Default | Meaning                                              |
|------------------------------|---------|------------------------------------------------------|
| `alerts.battery-low.trigger` | `15`    | The alert opens at this level or below.              |
| `alerts.battery-low.clear`   | `25`    | An open alert resolves only at this level or above.  |

Inside the band an open alert stays open: this stickiness prevents open/resolve flapping around a single
threshold. `clear > trigger` is enforced at startup. Per-device overrides are deferred to a later issue.

### Skip-on-absent

**Absence of a cached value ⇒ "not evaluable" ⇒ skip — never "condition cleared".** Redis gives no
presence guarantee (cold start, eviction, TTL), so a missing battery value must not resolve an open alert
(it would close → reopen → re-notify in a loop). Transitions happen **only on a fresh, confirmed
reading**: an alert (DB) can outlive the value that created it (cache) and stays `OPEN` until a reading
proves the level crossed `clear`. Same principle as [DEVICES.md](DEVICES.md): absence of data ≠ condition
ceased.

Consequence for `DETACHED` devices: their readings stop refreshing, so skip-on-absent **freezes** their
battery alerts in place — no resolve, no churn. "Device offline" is a distinct condition, owned by the
future `DEVICE_DETACHED` rule, not conflated with battery.

### Invariant for out-of-cycle transitions

Within the polling cycle no extra SSE trigger is needed: alert changes ride the existing publish. Any
alert transition introduced **outside** the polling cycle (e.g. a future manual ack/snooze) must publish
a `HomeStateRefresh` itself, the same way config-change use cases already do.

---

## Persistence

- Table `smart_home.alert`, all timestamps stored with time zone (`TIMESTAMPTZ`).
- Partial unique index `uq_alert_open_per_target` on `(type, COALESCE(target, ''))` where `status = 'OPEN'`.
- Outbound port `AlertsRepository` with a JDBC adapter (no JPA), following the existing persistence
  conventions; infrastructure exceptions are caught at the adapter boundary and mapped to typed failures
  (`AlertRepositoryError`, `AlertAlreadyOpen`).
- `resolve` projects an `AlertTransition.Resolved` onto storage (updates `status` and `resolved_at`);
  resolving a row that no longer exists is a typed failure, never a silent no-op.

---

## Orphan alerts on device lifecycle

- A `DEVICE`-scoped alert references a device by uuid in `target`. Synchronization never deletes devices
  (absence → `DETACHED`, see [DEVICES.md](DEVICES.md)), so a device row backing an alert always exists and
  there are no true orphans.
- **Guard for the future:** if device de-registration / hard-delete is ever introduced, it must
  **cascade-resolve (or delete) that device's alerts**. `target` is polymorphic (device uuid / provider id /
  none), so there is no literal FK; the cleanup must be done explicitly in the delete use case (or by
  introducing a FK with the chosen on-delete behaviour).

---

## `GET /alerts` is the canonical read model

`GET /alerts` is the canonical, detailed representation of alert state and the single source of truth on the
read side (it defaults to the currently `OPEN` alerts). Other read models — home dashboard, device status —
do **not** re-derive or duplicate alerts: they expose a minimal projection computed at read time from the
same `AlertsRepository`. This keeps one source of truth and avoids pushing domain knowledge (which device
belongs to which area) onto the frontend. See [alerts API](../api/alerts.md).

Two projections exist, both additive and limited to the `OPEN` alert **types** (no severity — the indicator
derives from presence; for the detail clients follow `GET /alerts`):

- **Home dashboard** (`GET /home` and the [SSE stream](HOME_STREAM.md)): each area carries
  `activeAlerts: FieldResult<AlertType[]>` — the types of the open alerts targeting the area's devices
  (sensors and actuators; `PROVIDER`/`GLOBAL` alerts are not attributed to areas, and neither are alerts on
  devices with no current area assignment — the former surface only in `GET /alerts`, the latter in
  `GET /alerts` and the device detail projection below). The open alerts are read
  in a **single batched query** per dashboard build (no N+1), and a lookup failure degrades to a field
  failure — never a false "no alerts". Within the polling cycle the indicator rides the existing
  `HomeStateRefresh` publish, since alert evaluation runs before it (see *Evaluation* above).
- **Device detail** (`GET /devices/{uuid}`): the aggregate carries `activeAlerts: Set<AlertType>?` — the
  types of the open alerts targeting the device. `null` means the lookup failed ("unknown"), an empty set
  means none: absence of data ≠ no alerts, consistently with skip-on-absent.
