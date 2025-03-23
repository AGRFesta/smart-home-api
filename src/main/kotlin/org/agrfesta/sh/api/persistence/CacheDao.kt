package org.agrfesta.sh.api.persistence

import org.agrfesta.sh.api.domain.commons.CacheEntry

interface CacheDao {
    fun upsert(key: String, value: String, ttl: Long? = null)

    fun findEntry(key: String): CacheEntry?

    /**
     * @throws PersistedCacheEntryNotFoundException when entry is missing.
     */
    fun getEntry(key: String): CacheEntry
}

class PersistedCacheEntryNotFoundException: Exception()
