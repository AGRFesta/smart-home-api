package org.agrfesta.sh.api.persistence

import arrow.core.Either
import org.agrfesta.sh.api.domain.commons.CacheEntry
import org.agrfesta.sh.api.domain.failures.FindPersistedCacheEntryFailure
import org.agrfesta.sh.api.domain.failures.GetPersistedCacheEntryFailure
import org.agrfesta.sh.api.domain.failures.PersistenceFailure

/**
 * Data access object for cache persistence operations.
 */
interface CacheDao {

    /**
     * Inserts or updates a cache entry identified by [key].
     *
     * @param key the unique key for the cache entry.
     * @param value the value to cache.
     * @param ttl optional time-to-live in seconds; `null` means no expiry.
     * @return [Either.Right] with [Unit] on success,
     * or [Either.Left] with [PersistenceFailure] if a database error occurs.
     */
    fun upsert(key: String, value: String, ttl: Long? = null): Either<PersistenceFailure, Unit>

    /**
     * Inserts or updates multiple cache entries in a single batch operation.
     *
     * @param entries the list of [CacheEntryDto] to persist.
     * @return [Either.Right] with [Unit] on success,
     * or [Either.Left] with [PersistenceFailure] if a database error occurs.
     */
    fun upsertBatch(entries: List<CacheEntryDto>): Either<PersistenceFailure, Unit>

    /**
     * Looks up a cache entry by [key] without failing if it does not exist.
     *
     * @param key the unique key to search for.
     * @return [Either.Right] with the [CacheEntry] if found, or `null` if no entry exists for that key,
     * or [Either.Left] with [FindPersistedCacheEntryFailure] if a database error occurs.
     */
    fun findEntry(key: String): Either<FindPersistedCacheEntryFailure, CacheEntry?>

    /**
     * Retrieves a cache entry by [key], failing if it does not exist.
     *
     * @param key the unique key to retrieve.
     * @return [Either.Right] with the [CacheEntry] if found,
     * or [Either.Left] with [GetPersistedCacheEntryFailure] if the entry does not exist or a database error occurs.
     */
    fun getEntry(key: String): Either<GetPersistedCacheEntryFailure, CacheEntry>

}
