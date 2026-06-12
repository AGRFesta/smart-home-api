# API Index

All endpoints require authentication via Bearer token, **except** the public health probes noted
below. See [SECURITY.md](../SECURITY.md).

| Method | Path     | Resource | Description                        |
|--------|----------|----------|------------------------------------|
| `GET`    | `/actuator/health` | [health](health.md#get-actuatorhealth) | Public health status (details when authenticated) |
| `GET`    | `/actuator/health/readiness` | [health](health.md#get-actuatorhealthreadiness) | Public readiness probe (app + DB; Redis excluded) |
| `GET`    | `/actuator/info` | [health](health.md#get-actuatorinfo) | Build version (authenticated) |
| `POST`   | `/devices/synchronizations` | [devices](devices.md#post-devicessynchronizations) | Synchronise persisted devices with provider snapshot |
| `GET`    | `/home`  | [home](home.md#get-home) | BFF dashboard — global state + areas |
| `GET`    | `/home/stream` | [home](home.md#get-homestream) | SSE stream — pushes the dashboard on every home-state change |
| `POST`   | `/areas` | [areas](areas.md#post-areas) | Create a new area |
| `GET`    | `/areas` | [areas](areas.md#get-areas) | List all areas |
| `GET`    | `/areas/{id}` | [areas](areas.md#get-areasid) | Get a single area by id |
| `PUT`    | `/areas/{id}` | [areas](areas.md#put-areasid) | Update an area |
| `DELETE` | `/areas/{id}` | [areas](areas.md#delete-areasid) | Delete an area |
| `PUT`    | `/areas/{areaId}/sensors/{deviceId}` | [areas](areas.md#put-areasareaidsensorsdeviceid) | Assign a device as sensor to an area |
| `DELETE` | `/areas/{areaId}/sensors/{deviceId}` | [areas](areas.md#delete-areasareaidsensorsdeviceid) | Unassign a sensor from an area |
| `PUT`    | `/areas/{areaId}/actuators/{deviceId}` | [areas](areas.md#put-areasareaidactuatorsdeviceid) | Assign a device as actuator to an area |
| `DELETE` | `/areas/{areaId}/actuators/{deviceId}` | [areas](areas.md#delete-areasareaidactuatorsdeviceid) | Unassign an actuator from an area |
| `GET`    | `/areas/{areaId}/heating-schedule` | [areas](areas.md#get-areasareaidheating-schedule) | Get the heating schedule for an area |
| `PUT`    | `/areas/{areaId}/heating-schedule` | [areas](areas.md#put-areasareaidheating-schedule) | Create or replace the heating schedule for an area |
| `DELETE` | `/areas/{areaId}/heating-schedule` | [areas](areas.md#delete-areasareaidheating-schedule) | Delete the heating schedule for an area |
