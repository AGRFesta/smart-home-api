CREATE TABLE IF NOT EXISTS smart_home.alert (
    uuid UUID PRIMARY KEY,
    type TEXT NOT NULL,
    scope TEXT NOT NULL,
    target TEXT,
    status TEXT NOT NULL,
    opened_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ,
    details TEXT
);

-- Idempotency guarantee: at most one OPEN alert per (type, target).
-- COALESCE folds GLOBAL alerts (NULL target) onto a single key so they are constrained too.
CREATE UNIQUE INDEX IF NOT EXISTS uq_alert_open_per_target
    ON smart_home.alert (type, COALESCE(target, ''))
    WHERE status = 'OPEN';
