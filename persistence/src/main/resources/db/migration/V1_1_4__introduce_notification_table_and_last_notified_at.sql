-- Reminder cadence tracking (#194): the instant the alert was last successfully notified.
-- NULL means never notified: the reminder scheduler treats it as immediately due, compensating
-- a lost `opened` emission.
ALTER TABLE smart_home.alert ADD COLUMN last_notified_at TIMESTAMPTZ;

-- Minimal persisted delivery channel (#194): each emitted notification is recorded here.
-- Rows are pruned by the retention scheduler, alerts are never deleted, so the FK is safe.
CREATE TABLE IF NOT EXISTS smart_home.notification (
    uuid UUID PRIMARY KEY,
    alert_uuid UUID NOT NULL REFERENCES smart_home.alert (uuid),
    event TEXT NOT NULL,
    sent_at TIMESTAMPTZ NOT NULL,
    payload TEXT
);

-- Serves both the sent_at DESC listing and the retention prune.
CREATE INDEX IF NOT EXISTS idx_notification_sent_at ON smart_home.notification (sent_at);
