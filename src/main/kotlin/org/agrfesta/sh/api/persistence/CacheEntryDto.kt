package org.agrfesta.sh.api.persistence

/**
 * Represents a single cache entry.
 *
 * @property key The unique key for the cache entry.
 * @property value The value to be cached.
 * @property ttl The time-to-live in seconds. If `null`, the entry does not expire automatically based on TTL.
 */
data class CacheEntryDto(
    val key: String,
    val value: String,
    val ttl: Long? = null
)
