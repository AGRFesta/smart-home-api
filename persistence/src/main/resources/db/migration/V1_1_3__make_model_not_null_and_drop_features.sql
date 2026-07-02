-- Backfill any residual NULL model (legacy/detached rows never re-synced) with a non-catalog
-- sentinel, so SET NOT NULL is correct-by-construction on any environment (restore/replay/fresh).
-- 'unknown' is absent from the DeviceModelCatalog, so rolesOf() yields empty features.
UPDATE smart_home.device SET model = 'unknown' WHERE model IS NULL;
ALTER TABLE smart_home.device ALTER COLUMN model SET NOT NULL;
ALTER TABLE smart_home.device DROP COLUMN features;
