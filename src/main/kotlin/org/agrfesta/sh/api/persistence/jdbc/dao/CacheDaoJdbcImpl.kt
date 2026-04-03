package org.agrfesta.sh.api.persistence.jdbc.dao

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.agrfesta.sh.api.domain.commons.CacheEntry
import org.agrfesta.sh.api.domain.failures.FindPersistedCacheEntryFailure
import org.agrfesta.sh.api.domain.failures.GetPersistedCacheEntryFailure
import org.agrfesta.sh.api.domain.failures.PersistedCacheEntryNotFound
import org.agrfesta.sh.api.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.persistence.CacheDao
import org.agrfesta.sh.api.persistence.CacheEntryDto
import org.agrfesta.sh.api.persistence.jdbc.repositories.CacheJdbcRepository
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service

@Service
class CacheDaoJdbcImpl(
    private val cacheJdbcRepository: CacheJdbcRepository
): CacheDao {

    override fun upsert(key: String, value: String, ttl: Long?): Either<PersistenceFailure, Unit> = try {
        cacheJdbcRepository.upsert(key, value, ttl).right()
    } catch (e: DataAccessException) {
        PersistenceFailure(e).left()
    }

    override fun upsertBatch(entries: List<CacheEntryDto>): Either<PersistenceFailure, Unit> = try {
        cacheJdbcRepository.upsertBatch(entries).right()
    } catch (e: DataAccessException) {
        PersistenceFailure(e).left()
    }

    override fun findEntry(key: String): Either<FindPersistedCacheEntryFailure, CacheEntry?> = try {
        cacheJdbcRepository.findEntry(key).right()
    } catch (e: DataAccessException) {
        PersistenceFailure(e).left()
    }

    override fun getEntry(key: String): Either<GetPersistedCacheEntryFailure, CacheEntry> = try {
        cacheJdbcRepository.findEntry(key)?.right()
            ?: PersistedCacheEntryNotFound.left()
    } catch (e: DataAccessException) {
        PersistenceFailure(e).left()
    }

}
