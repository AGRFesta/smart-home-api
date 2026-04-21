package org.agrfesta.sh.api.utils

import arrow.core.left
import arrow.core.right
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.agrfesta.sh.api.core.domain.commons.CacheEntry
import org.agrfesta.sh.api.core.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.core.application.ports.outbounds.CacheRepository
import org.springframework.stereotype.Service
import kotlin.time.Duration

@Service
class CacheAsserter(
    private val cache: Cache = mockk(relaxed = true),
    private val cacheRepository: CacheRepository = mockk(relaxed = true)
) {

    fun givenCacheEntry(key: String, value: String) {
        every { cache.get(key) } returns value.right()
    }
    fun givenMissingEntry(key: String) {
        every { cache.get(key) } returns CachedValueNotFound(key).left()
    }
    fun givenFailingCache() {
        every { cache.get(any()) } returns CacheError(Exception("cache failure!")).left()
        every { cache.set(any(), any()) } throws Exception("cache failure!")
    }

    fun verifyCacheEntrySet(key: String, value: String, duration: Duration? = null) {
        verify { cache.set(key, value, duration) }
    }
    fun verifyCacheEntryRemoval(key: String) {
        verify { cache.remove(key) }
    }

    fun givenPersistedCacheEntry(key: String, value: String) {
        every { cacheRepository.getEntry(key) } returns CacheEntry(value).right()
    }
    fun givenPersistedCacheEntryFetchFailure() {
        every { cacheRepository.getEntry(any()) } returns PersistenceFailure(Exception("persisted cache fetch failure")).left()
    }
    fun givenPersistedCacheEntryUpsertFailure() {
        every { cacheRepository.upsert(any(), any()) } returns PersistenceFailure(Exception("persisted cache set failure")).left()
    }

    fun verifyPersistedCacheEntryUpsert(key: String, value: String) {
        verify { cacheRepository.upsert(key, value) }
    }

}
