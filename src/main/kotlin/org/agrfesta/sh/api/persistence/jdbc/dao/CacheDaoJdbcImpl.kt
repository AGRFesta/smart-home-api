package org.agrfesta.sh.api.persistence.jdbc.dao

import org.agrfesta.sh.api.domain.commons.CacheEntry
import org.agrfesta.sh.api.persistence.CacheDao
import org.agrfesta.sh.api.persistence.PersistedCacheEntryNotFoundException
import org.agrfesta.sh.api.persistence.jdbc.repositories.CacheJdbcRepository
import org.springframework.stereotype.Service

@Service
class CacheDaoJdbcImpl(
    private val cacheJdbcRepository: CacheJdbcRepository
): CacheDao {

    override fun upsert(key: String, value: String, ttl: Long?) = cacheJdbcRepository.upsert(key, value, ttl)

    override fun findEntry(key: String): CacheEntry? = cacheJdbcRepository.findEntry(key)

    override fun getEntry(key: String): CacheEntry = cacheJdbcRepository.findEntry(key)
        ?: throw PersistedCacheEntryNotFoundException()

}
