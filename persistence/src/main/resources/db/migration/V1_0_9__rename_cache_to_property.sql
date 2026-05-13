ALTER TABLE smart_home.cache RENAME TO property;

ALTER INDEX smart_home.idx_cache_key RENAME TO idx_property_key;
ALTER INDEX smart_home.idx_cache_expires RENAME TO idx_property_expires;
