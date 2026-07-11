# Notifications API

## GET /notifications

Returns the emitted alert notifications, **most recent first**. A notification is the delivery
projection of an alert transition — `OPENED`, `REMINDER` or `RESOLVED` — recorded by the persisted
delivery channel (see [ALERTS.md](../domain/ALERTS.md)). The alert remains the source of truth:
this endpoint answers "what was notified, and when", not "what is currently wrong" (`GET /alerts`
does that).

The table is unbounded over time, so the endpoint is **always paginated** and old rows are pruned
after the configured retention window (`alerts.notifications.retention`, default 90 days).

### Query parameters

| Param  | Default | Description                                                                     |
|--------|---------|---------------------------------------------------------------------------------|
| `page` | `0`     | Zero-based page index. Must be between `0` and `21474836` (keeps the offset within integer range). |
| `size` | `20`    | Page size. Must be between `1` and `100`.                                       |

### Response `200 OK`

A JSON envelope with the page items and the pagination metadata. An empty page is returned as an
empty `items` array, not an error. `sentAt` is Unix epoch seconds (consistent with all timestamps
in this API). `payload` is the free-form snapshot of the alert condition at emission time (`null`
when the alert carries no details); for `REMINDER`s it is still the open-time snapshot, not the
current value.

```json
{
  "items": [
    {
      "uuid": "0d1c2e46-0d3f-4b1a-9be7-0f6a3c34f9d2",
      "alertUuid": "a87454c6-6949-4449-a932-1b953212dd49",
      "event": "RESOLVED",
      "sentAt": 1718460398,
      "payload": "battery=10%"
    },
    {
      "uuid": "7b8d9f10-4c5e-4a6b-8c7d-9e0f1a2b3c4d",
      "alertUuid": "a87454c6-6949-4449-a932-1b953212dd49",
      "event": "OPENED",
      "sentAt": 1718460338,
      "payload": "battery=10%"
    }
  ],
  "page": 0,
  "size": 20,
  "total": 2
}
```

### Response `400 Bad Request`

Returned when `page` or `size` are outside their bounds.

```json
{ "message": "page must be between 0 and 21474836 and size between 1 and 100!" }
```

### Response `500 Internal Server Error`

Returned when the persisted notifications cannot be read.

```json
{ "message": "Unable to retrieve notifications!" }
```
