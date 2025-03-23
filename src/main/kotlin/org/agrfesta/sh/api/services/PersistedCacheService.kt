package org.agrfesta.sh.api.services

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.agrfesta.sh.api.domain.commons.CacheEntry
import org.agrfesta.sh.api.domain.failures.GetPersistedCacheEntryFailure
import org.agrfesta.sh.api.domain.failures.PersistedCacheEntryNotFound
import org.agrfesta.sh.api.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.persistence.CacheDao
import org.agrfesta.sh.api.persistence.PersistedCacheEntryNotFoundException
import org.springframework.stereotype.Service

@Service
class PersistedCacheService(
    private val cacheDao: CacheDao
) {

    fun upsert(key: String, value: String, ttl: Long? = null): Either<PersistenceFailure, Unit> = try {
        cacheDao.upsert(key, value, ttl).right()
    } catch (e: Exception) {
        PersistenceFailure(e).left()
    }

    fun getEntry(key: String): Either<GetPersistedCacheEntryFailure, CacheEntry> = try {
        cacheDao.getEntry(key).right()
    } catch (e: PersistedCacheEntryNotFoundException) {
        PersistedCacheEntryNotFound.left()
    } catch (e: Exception) {
        PersistenceFailure(e).left()
    }

}
