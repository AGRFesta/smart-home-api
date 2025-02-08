CREATE TABLE smart_home.cache (
    key TEXT PRIMARY KEY,      -- Unique key for each cache entry
    value TEXT NOT NULL,       -- Store value as a simple string
    created_at TIMESTAMP DEFAULT NOW(), -- Timestamp of entry creation
    expires_at TIMESTAMP     -- Expiry time
);

-- Index to optimize lookups based on the key
CREATE INDEX idx_cache_key ON smart_home.cache(key);

-- Index to allow efficient cleanup of expired cache entries
CREATE INDEX idx_cache_expires ON smart_home.cache(expires_at);
