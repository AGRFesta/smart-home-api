CREATE SCHEMA IF NOT EXISTS smart_home;

CREATE TABLE IF NOT EXISTS smart_home.device (
    uuid UUID PRIMARY KEY,
    name TEXT NOT NULL,
    provider TEXT NOT NULL,
    provider_id TEXT NOT NULL,
    created_on TIMESTAMP NOT NULL,
    updated_on TIMESTAMP
);
