package org.agrfesta.sh.api.persistence.jdbc.dao

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import org.agrfesta.sh.api.domain.failures.GetPersistedCacheEntryFailure
import org.agrfesta.sh.api.domain.failures.PersistedCacheEntryNotFound
import org.agrfesta.sh.api.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.persistence.CacheEntryDto
import org.agrfesta.test.mothers.aRandomTtl
import org.agrfesta.test.mothers.aRandomUniqueString
import org.agrfesta.test.mothers.nowNoMills
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataAccessResourceFailureException

class CacheDaoJdbcImplTest : AbstractDaoJdbcImplTest() {

    @Autowired private lateinit var sut: CacheDaoJdbcImpl

    private val now = nowNoMills()

    @BeforeEach
    fun init() {
        every { timeService.now() } returns now
    }

    // upsert() ////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Test
    fun `upsert() Inserts a new entry when key is missing`() {
        val key = aRandomUniqueString()
        val value = aRandomUniqueString()
        val ttl = aRandomTtl()
        cacheRepo.findEntry(key).shouldBeNull()

        sut.upsert(key, value, ttl).shouldBeRight()

        val entry = cacheRepo.findEntry(key).shouldNotBeNull()
        entry.value shouldBe value
        entry.expiresAt shouldBe now.plusSeconds(ttl)
    }

    @Test
    fun `upsert() Inserts entry with no expiry when ttl is null`() {
        val key = aRandomUniqueString()
        val value = aRandomUniqueString()

        sut.upsert(key, value).shouldBeRight()

        val entry = cacheRepo.findEntry(key).shouldNotBeNull()
        entry.value shouldBe value
        entry.expiresAt.shouldBeNull()
    }

    @Test
    fun `upsert() Updates value and ttl when key already exists`() {
        val key = aRandomUniqueString()
        val newValue = aRandomUniqueString()
        val newTtl = aRandomTtl()
        cacheRepo.upsert(key, aRandomUniqueString(), aRandomTtl())

        sut.upsert(key, newValue, newTtl).shouldBeRight()

        val entry = cacheRepo.findEntry(key).shouldNotBeNull()
        entry.value shouldBe newValue
        entry.expiresAt shouldBe now.plusSeconds(newTtl)
    }

    @Test
    fun `upsert() Returns PersistenceFailure when repository throws`() {
        val key = aRandomUniqueString()
        val failure = DataAccessResourceFailureException("upsert failure")
        every { cacheRepo.upsert(key, any(), any()) } throws failure

        sut.upsert(key, aRandomUniqueString(), aRandomTtl())
            .shouldBeLeft()
            .shouldBeInstanceOf<PersistenceFailure>()
    }

    // upsertBatch() //////////////////////////////////////////////////////////////////////////////////////////////////////

    @Test
    fun `upsertBatch() Inserts all new entries`() {
        val key1 = aRandomUniqueString()
        val value1 = aRandomUniqueString()
        val key2 = aRandomUniqueString()
        val value2 = aRandomUniqueString()
        val entries = listOf(
            CacheEntryDto(key1, value1),
            CacheEntryDto(key2, value2)
        )

        sut.upsertBatch(entries).shouldBeRight()

        cacheRepo.findEntry(key1).shouldNotBeNull().value shouldBe value1
        cacheRepo.findEntry(key2).shouldNotBeNull().value shouldBe value2
    }

    @Test
    fun `upsertBatch() Updates existing entries and inserts new ones`() {
        val existingKey = aRandomUniqueString()
        val newKey = aRandomUniqueString()
        val newValueForExisting = aRandomUniqueString()
        val newTtl = aRandomTtl()
        cacheRepo.upsert(existingKey, aRandomUniqueString(), aRandomTtl())
        val entries = listOf(
            CacheEntryDto(existingKey, newValueForExisting, newTtl),
            CacheEntryDto(newKey, aRandomUniqueString())
        )

        sut.upsertBatch(entries).shouldBeRight()

        cacheRepo.findEntry(existingKey).shouldNotBeNull().also {
            it.value shouldBe newValueForExisting
            it.expiresAt shouldBe now.plusSeconds(newTtl)
        }
        cacheRepo.findEntry(newKey).shouldNotBeNull()
    }

    @Test
    fun `upsertBatch() Returns PersistenceFailure when repository throws`() {
        val entries = listOf(CacheEntryDto(aRandomUniqueString(), aRandomUniqueString()))
        val failure = DataAccessResourceFailureException("batch upsert failure")
        every { cacheRepo.upsertBatch(entries) } throws failure

        sut.upsertBatch(entries)
            .shouldBeLeft()
            .shouldBeInstanceOf<PersistenceFailure>()
    }

    // findEntry() ////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Test
    fun `findEntry() Returns entry when found and not expired`() {
        val key = aRandomUniqueString()
        val value = aRandomUniqueString()
        cacheRepo.upsert(key, value)

        sut.findEntry(key)
            .shouldBeRight()
            .shouldNotBeNull()
            .value shouldBe value
    }

    @Test
    fun `findEntry() Returns null when key is missing`() {
        sut.findEntry(aRandomUniqueString())
            .shouldBeRight()
            .shouldBeNull()
    }

    @Test
    fun `findEntry() Returns null when entry is expired`() {
        val key = aRandomUniqueString()
        val ttl = aRandomTtl()
        // upsert calls timeService.now() twice (createdAt + expiresAt), findEntry calls it once
        every { timeService.now() } returns now andThen now andThen now.plusSeconds(ttl + 10)
        cacheRepo.upsert(key, aRandomUniqueString(), ttl)

        sut.findEntry(key)
            .shouldBeRight()
            .shouldBeNull()
    }

    @Test
    fun `findEntry() Returns PersistenceFailure when repository throws`() {
        val key = aRandomUniqueString()
        val failure = DataAccessResourceFailureException("find entry failure")
        every { cacheRepo.findEntry(key) } throws failure

        sut.findEntry(key)
            .shouldBeLeft()
            .shouldBeInstanceOf<PersistenceFailure>()
    }

    // getEntry() /////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Test
    fun `getEntry() Returns entry when found and not expired`() {
        val key = aRandomUniqueString()
        val value = aRandomUniqueString()
        cacheRepo.upsert(key, value)

        sut.getEntry(key)
            .shouldBeRight()
            .value shouldBe value
    }

    @Test
    fun `getEntry() Returns PersistedCacheEntryNotFound when key is missing`() {
        sut.getEntry(aRandomUniqueString())
            .shouldBeLeft()
            .shouldBeInstanceOf<PersistedCacheEntryNotFound>()
    }

    @Test
    fun `getEntry() Returns PersistedCacheEntryNotFound when entry is expired`() {
        val key = aRandomUniqueString()
        val ttl = aRandomTtl()
        // upsert calls timeService.now() twice (createdAt + expiresAt), getEntry calls it once
        every { timeService.now() } returns now andThen now andThen now.plusSeconds(ttl + 10)
        cacheRepo.upsert(key, aRandomUniqueString(), ttl)

        sut.getEntry(key)
            .shouldBeLeft()
            .shouldBeInstanceOf<PersistedCacheEntryNotFound>()
    }

    @Test
    fun `getEntry() Returns PersistenceFailure when repository throws`() {
        val key = aRandomUniqueString()
        val failure = DataAccessResourceFailureException("get entry failure")
        every { cacheRepo.findEntry(key) } throws failure

        sut.getEntry(key)
            .shouldBeLeft()
            .shouldBeInstanceOf<PersistenceFailure>()
            .shouldBeInstanceOf<GetPersistedCacheEntryFailure>()
    }

}
