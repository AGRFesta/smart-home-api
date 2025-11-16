package org.agrfesta.sh.api.utils

import arrow.core.left
import arrow.core.right
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.agrfesta.sh.api.domain.commons.CacheEntry
import org.agrfesta.sh.api.persistence.CacheDao
import org.springframework.stereotype.Service
import kotlin.time.Duration

@Service
class CacheAsserter(
    private val cache: Cache = mockk(relaxed = true),
    private val cacheDao: CacheDao = mockk(relaxed = true)
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
        every { cacheDao.getEntry(key) } returns CacheEntry(value)
    }
    fun givenPersistedCacheEntryFetchFailure() {
        every { cacheDao.getEntry(any()) } throws Exception("persisted cache fetch failure")
    }
    fun givenPersistedCacheEntryUpsertFailure() {
        every { cacheDao.upsert(any(), any()) } throws Exception("persisted cache set failure")
    }

    fun verifyPersistedCacheEntryUpsert(key: String, value: String) {
        verify { cacheDao.upsert(key, value) }
    }

}
