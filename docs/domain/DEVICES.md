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

---

## Diagnostics Passthrough (provider's truth)

During synchronization the system **normalizes** provider data at the adapter boundary
(`DevicesProvider.getAllDevices()` → `ProviderDeviceData`) and discards everything it does not model.
The diagnostics passthrough exposes the opposite: the **provider's truth**, the full unfiltered payload a
provider holds about a single device, in realtime.

See [`GET /devices/{uuid}/diagnostics`](../api/devices.md#get-devicesuuiddiagnostics). It is the sibling
of `GET /devices/{uuid}` (our persisted *aggregate*); diagnostics is realtime-only — **never persisted nor
cached** — and intentionally surfaces provider failures rather than masking them.

### Design

- **Diagnostics is a device capability, not a provider-wide port.** A device driver opts in by implementing
  the `Inspectable` capability (`inspect(): Either<DevicesProviderFailure, String>`), sitting next to
  `fetchReadings()` / `getActuatorStatus()` — "ask *this* device about itself, now". This is distinct (ISP)
  from the sync snapshot.
- **Routing reuses `ProviderDevicesFactory`.** `InspectDeviceService` resolves the persisted device, builds
  its driver via the matching factory, and calls `inspect()` when the driver is `Inspectable`. A driver that
  is not `Inspectable` (e.g. the SwitchBot hub) yields `501 Not Implemented` — support is per device type,
  not per provider.
- **Raw `String`, not a parsed tree.** The capability returns the literal provider body, keeping the core
  free of any JSON library and avoiding a parse → re-serialize round trip that could reorder keys or drop
  fields. The controller writes it through with `Content-Type: application/json`.
- **Per-provider source.** Each driver decides which provider call(s) expose the device's realtime truth:
  - **SwitchBot** (`SwitchBotMeter`) → `GET /devices/{id}/status`, body passed through byte-for-byte.
  - **Netatmo** (`NetatmoSmarther`) → home status, from which the driver extracts **its own room** (by
    `roomId`). Single-thermostat-per-home is assumed today, consistent with `fetchReadings()`; handling
    multiple devices per room is deferred.
