# Device Domain Model

## Overview

A `Device` is a physical smart-home appliance registered through an external provider (e.g. SwitchBot, Netatmo).
It is identified within the system by a surrogate `uuid` generated at creation time, and uniquely identified
at the provider level by the composite natural key `(provider, deviceProviderId)`.

The DB enforces this natural key via a unique constraint on `(provider, provider_id)`.

---

## Device Status Lifecycle

A device's `DeviceStatus` reflects its **last known reachability** as reported by the provider.
It is not a permanent domain event — it is a snapshot updated on every synchronization cycle.

```
           provider returns device
DETACHED ─────────────────────────► PAIRED
  ▲                                    │
  │    provider absent or fails        │
  └────────────────────────────────────┘
```

| Status     | Meaning |
|------------|---------|
| `PAIRED`   | The provider included this device in its latest snapshot. The device is reachable. |
| `DETACHED` | The device was absent from the provider's latest snapshot, **or the provider itself failed to respond**. The device is temporarily unreachable. |

### Key design decision: provider failure → DETACHED

If a provider fails during synchronization, its devices are treated as unreachable and marked `DETACHED`.
This is intentional: the system always reflects the last confirmed state. A transient provider outage
will resolve itself on the next successful sync cycle, at which point all re-appearing devices are
automatically re-paired (`DETACHED` → `PAIRED`).

This makes `DETACHED` semantically equivalent to **OFFLINE** — a temporary, reversible condition —
rather than a permanent "removed from provider registry" state.

---

## Synchronization Flow

Triggered by `POST /devices/synchronizations` (see [devices API](../api/devices.md)).

For each registered provider:
1. Fetch the current device snapshot (`DevicesProvider.getAllDevices()`).
2. If the provider fails, its devices become `DETACHED` (treated as unreachable).
3. Compare the snapshot against persisted devices using the natural key `(provider, deviceProviderId)`.

| Outcome       | Condition | Action |
|---------------|-----------|--------|
| **New**       | Device in snapshot, not in DB for that provider | Persist with status `PAIRED` |
| **Updated**   | Device in snapshot and in DB for that provider | Refresh `name` and status → `PAIRED` |
| **Detached**  | Device in DB for that provider, absent from snapshot | Update status → `DETACHED` |

The operation is **best-effort**: a failure on a single device (e.g. a DB insert error) does not abort
the synchronization. The remaining devices are processed independently.
