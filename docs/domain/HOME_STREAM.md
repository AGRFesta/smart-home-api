# Home Stream (Real-Time Dashboard)

## Overview

The home stream pushes the current home dashboard to connected clients over
[Server-Sent Events](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events) (`GET /home/stream`),
so the app's home screen always shows fresh data without client-side polling.

The pushed payload is **exactly** the `HomeDashboardResponse` returned by `GET /home` — the stream reuses
`GetHomeDashboardUseCase` and the same response mapping. The one-shot `GET /home` endpoint remains available for
clients where a persistent connection is not suitable (widgets, Wear OS, one-off queries).

---

## Data Flow

```
                         ┌──────────────────────────── core ────────────────────────────┐
 polling cycle           │                                                               │
 (every minute) ───► FetchSensorReadingsService ──onRight──┐                             │
                         │                                  │                             │
 config change           │                                  ▼                             │
 (PUT heating/           │                       HomeStateRefreshPublisher  (outbound port)
  property) ───► Upsert / Replace / Delete ──onRight──►     │                             │
                         │   *Service                       │                             │
                         └──────────────────────────────────┼─────────────────────────────┘
                                                            │ publish()
                                                            ▼
                                        SpringHomeStateRefreshPublisher  (outbound adapter, app)
                                                            │ publishEvent
                                                            ▼
                                              HomeStateRefreshEvent  (in-process ApplicationEvent)
                                                            │ @EventListener
                                                            ▼
                                            HomeStreamBroadcaster  (inbound adapter, app)
                                              GetHomeDashboardUseCase.execute()
                                                            │ broadcast
                                                            ▼
                                          all registered SseEmitter → connected clients
```

On connection, `HomeController.getHomeStream()` calls `HomeStreamBroadcaster.register()`, which registers a new
`SseEmitter` and **immediately** sends the current dashboard as the initial event.

---

## Trigger Sources

| Trigger | Published by | Frequency |
|---|---|---|
| Device polling cycle | `FetchSensorReadingsService` (on a successful `Right`) | Inherited from `DevicesDataFetchScheduler` (every minute) |
| Heating target / schedule change | `ReplaceHeatingScheduleService`, `DeleteHeatingScheduleService` | Immediately on successful save |
| Shared strategy / heating-enabled change | `UpsertPropertyService`, `UpsertPropertyBatchService` | Immediately on successful upsert |

A trigger only fires on the **success** path of the originating operation. If a polling cycle fails, no event is
published and clients retain their last received state.

---

## Update Frequency

Stream cadence is governed entirely by the existing polling interval — **no separate timer is introduced**.
Configuration changes bypass that interval and push immediately, because a user-initiated change (e.g. raising the
heating target) must be reflected at once rather than after up to a minute.

A comment-only keep-alive (`: ping`) is emitted on a fixed interval (`home.stream.keep-alive-ms`, default 30 s) so
idle connections survive proxies and load balancers.

---

## Key Design Decisions

| Decision | Rationale |
|---|---|
| **Spring `ApplicationEvent` (in-process)** | Single server instance — no message broker needed. The publisher sits behind the outbound port `HomeStateRefreshPublisher`, so the delivery mechanism can be swapped (e.g. Redis Pub/Sub) if the deployment model ever changes, without touching the core. |
| **Trigger lives in the core use cases, not the scheduler** | "Readings updated ⇒ home state changed" is application logic; it belongs in `FetchSensorReadingsService`, which publishes on its own success path. The `DevicesDataFetchScheduler` stays a thin driving adapter (`cron → execute()`). This keeps a single publish path driven only by the core, and naturally satisfies "polling failure ⇒ no event". |
| **Push always, no diff** | Comparing current vs previous payload adds complexity for negligible benefit at a one-minute cadence. Clients handle idempotent updates naturally. |
| **Full payload on every event** | Stateless and simple — the client replaces its state and never merges deltas. Payload size is acceptable for a domestic home. |
| **Synchronous event listener** | The project enables `@EnableScheduling` but not `@EnableAsync`; the broadcast runs on the publishing thread. Broadcasting is cheap (in-memory fan-out) and avoids adding an async executor that would also change the existing scheduler's behaviour. |
| **Thread-safe emitter registry** | `HomeStreamBroadcaster` holds a `CopyOnWriteArrayList<SseEmitter>` because the polling thread and HTTP request threads access it concurrently; iteration during broadcast tolerates concurrent removal of dropped emitters. |
| **`GET /home` retained** | The one-shot snapshot stays for clients where a persistent connection is unsuitable. |

---

## Failure Handling

- **Dashboard fetch fails** (`GetHomeDashboardUseCase` returns `Left`): nothing is sent for that event; clients keep
  their last state.
- **Client disconnected / emitter timed out**: the failing `send` is caught and that emitter is removed from the
  registry; the broadcast continues to the remaining clients.
- **Emitter timeout**: configurable via `home.stream.emitter-timeout-ms` (default one hour).

---

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `home.stream.emitter-timeout-ms` | `3600000` (1 h) | Server-side lifetime of an idle `SseEmitter` |
| `home.stream.keep-alive-ms` | `30000` (30 s) | Interval between keep-alive comment events |

---

## Out of Scope

- WebSocket or any bidirectional channel
- Delta/patch events (full payload only)
- Multi-instance deployment / Redis Pub/Sub (single-server assumption)
- Background push notifications (tracked separately)
