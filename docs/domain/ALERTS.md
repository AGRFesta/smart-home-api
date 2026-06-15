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

## Persistence

- Table `smart_home.alert`, all timestamps stored with time zone (`TIMESTAMPTZ`).
- Partial unique index `uq_alert_open_per_target` on `(type, COALESCE(target, ''))` where `status = 'OPEN'`.
- Outbound port `AlertsRepository` with a JDBC adapter (no JPA), following the existing persistence
  conventions; infrastructure exceptions are caught at the adapter boundary and mapped to typed failures
  (`AlertRepositoryError`, `AlertAlreadyOpen`).

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
same `AlertRepository`. This keeps one source of truth and avoids pushing domain knowledge onto the
frontend. See [alerts API](../api/alerts.md).
