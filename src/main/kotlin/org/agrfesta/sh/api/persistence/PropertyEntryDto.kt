package org.agrfesta.sh.api.persistence

/**
 * Represents a single property entry.
 *
 * @property key The unique key for the property entry.
 * @property value The value to be stored.
 * @property ttl The time-to-live in seconds. If `null`, the entry does not expire automatically based on TTL.
 */
data class PropertyEntryDto(
    val key: String,
    val value: String,
    val ttl: Long? = null
)
