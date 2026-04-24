# API Index

All endpoints require authentication via Bearer token. See [SECURITY.md](../SECURITY.md).

| Method | Path     | Resource | Description                        |
|--------|----------|----------|------------------------------------|
| `GET`    | `/home`  | [home](home.md#get-home) | BFF dashboard — global state + areas |
| `GET`    | `/areas/{areaId}/heating-schedule` | [areas](areas.md#get-areasareaidheating-schedule) | Get the heating schedule for an area |
| `PUT`    | `/areas/{areaId}/heating-schedule` | [areas](areas.md#put-areasareaidheating-schedule) | Create or replace the heating schedule for an area |
| `DELETE` | `/areas/{areaId}/heating-schedule` | [areas](areas.md#delete-areasareaidheating-schedule) | Delete the heating schedule for an area |
