package org.agrfesta.sh.api.utils

import arrow.core.left
import arrow.core.right
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.agrfesta.sh.api.core.application.ports.outbounds.Cache
import org.agrfesta.sh.api.core.application.ports.outbounds.CacheError
import org.agrfesta.sh.api.core.application.ports.outbounds.CachedValueNotFound
import org.agrfesta.sh.api.core.domain.commons.PropertyEntry
import org.agrfesta.sh.api.core.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.core.application.ports.outbounds.settings.PropertyRepository
import org.springframework.stereotype.Service
import kotlin.time.Duration

@Service
class CacheAsserter(
    private val cache: Cache = mockk(relaxed = true),
    private val propertyRepository: PropertyRepository = mockk(relaxed = true)
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

    fun givenProperty(key: String, value: String) {
        every { propertyRepository.getEntry(key) } returns PropertyEntry(value).right()
    }
    fun givenPropertyFetchFailure() {
        every { propertyRepository.getEntry(any()) } returns PersistenceFailure(Exception("property fetch failure")).left()
    }
    fun givenPropertyUpsertFailure() {
        every { propertyRepository.upsert(any(), any()) } returns PersistenceFailure(Exception("property set failure")).left()
    }

    fun verifyPropertyUpsert(key: String, value: String) {
        verify { propertyRepository.upsert(key, value) }
    }

}
