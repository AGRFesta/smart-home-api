package org.agrfesta.sh.api.persistence.jdbc.adapters

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import org.agrfesta.sh.api.core.domain.failures.GetPropertyFailure
import org.agrfesta.sh.api.core.domain.failures.PropertyNotFound
import org.agrfesta.sh.api.core.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.persistence.PropertyEntryDto
import org.agrfesta.test.mothers.aRandomTtl
import org.agrfesta.test.mothers.aRandomUniqueString
import org.agrfesta.test.mothers.nowNoMills
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataAccessResourceFailureException

class PropertyJdbcAdapterTest : AbstractJdbcAdapterTest() {

    @Autowired private lateinit var sut: PropertyJdbcAdapter

    private val now = nowNoMills()

    @BeforeEach
    fun init() {
        every { timeProvider.now() } returns now
    }

    // upsert() ////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Test
    fun `upsert() Inserts a new entry when key is missing`() {
        val key = aRandomUniqueString()
        val value = aRandomUniqueString()
        val ttl = aRandomTtl()
        propertyRepo.findEntry(key).shouldBeNull()

        sut.upsert(key, value, ttl).shouldBeRight()

        val entry = propertyRepo.findEntry(key).shouldNotBeNull()
        entry.value shouldBe value
        entry.expiresAt shouldBe now.plusSeconds(ttl)
    }

    @Test
    fun `upsert() Inserts entry with no expiry when ttl is null`() {
        val key = aRandomUniqueString()
        val value = aRandomUniqueString()

        sut.upsert(key, value).shouldBeRight()

        val entry = propertyRepo.findEntry(key).shouldNotBeNull()
        entry.value shouldBe value
        entry.expiresAt.shouldBeNull()
    }

    @Test
    fun `upsert() Updates value and ttl when key already exists`() {
        val key = aRandomUniqueString()
        val newValue = aRandomUniqueString()
        val newTtl = aRandomTtl()
        propertyRepo.upsert(key, aRandomUniqueString(), aRandomTtl())

        sut.upsert(key, newValue, newTtl).shouldBeRight()

        val entry = propertyRepo.findEntry(key).shouldNotBeNull()
        entry.value shouldBe newValue
        entry.expiresAt shouldBe now.plusSeconds(newTtl)
    }

    @Test
    fun `upsert() Returns PersistenceFailure when repository throws`() {
        val key = aRandomUniqueString()
        val failure = DataAccessResourceFailureException("upsert failure")
        every { propertyRepo.upsert(key, any(), any()) } throws failure

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
            PropertyEntryDto(key1, value1),
            PropertyEntryDto(key2, value2)
        )

        sut.upsertBatch(entries).shouldBeRight()

        propertyRepo.findEntry(key1).shouldNotBeNull().value shouldBe value1
        propertyRepo.findEntry(key2).shouldNotBeNull().value shouldBe value2
    }

    @Test
    fun `upsertBatch() Updates existing entries and inserts new ones`() {
        val existingKey = aRandomUniqueString()
        val newKey = aRandomUniqueString()
        val newValueForExisting = aRandomUniqueString()
        val newTtl = aRandomTtl()
        propertyRepo.upsert(existingKey, aRandomUniqueString(), aRandomTtl())
        val entries = listOf(
            PropertyEntryDto(existingKey, newValueForExisting, newTtl),
            PropertyEntryDto(newKey, aRandomUniqueString())
        )

        sut.upsertBatch(entries).shouldBeRight()

        propertyRepo.findEntry(existingKey).shouldNotBeNull().also {
            it.value shouldBe newValueForExisting
            it.expiresAt shouldBe now.plusSeconds(newTtl)
        }
        propertyRepo.findEntry(newKey).shouldNotBeNull()
    }

    @Test
    fun `upsertBatch() Returns PersistenceFailure when repository throws`() {
        val entries = listOf(PropertyEntryDto(aRandomUniqueString(), aRandomUniqueString()))
        val failure = DataAccessResourceFailureException("batch upsert failure")
        every { propertyRepo.upsertBatch(entries) } throws failure

        sut.upsertBatch(entries)
            .shouldBeLeft()
            .shouldBeInstanceOf<PersistenceFailure>()
    }

    // findEntry() ////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Test
    fun `findEntry() Returns entry when found and not expired`() {
        val key = aRandomUniqueString()
        val value = aRandomUniqueString()
        propertyRepo.upsert(key, value)

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
        // upsert calls timeProvider.now() once, findEntry calls it once
        every { timeProvider.now() } returns now andThen now.plusSeconds(ttl + 10)
        propertyRepo.upsert(key, aRandomUniqueString(), ttl)

        sut.findEntry(key)
            .shouldBeRight()
            .shouldBeNull()
    }

    @Test
    fun `findEntry() Returns PersistenceFailure when repository throws`() {
        val key = aRandomUniqueString()
        val failure = DataAccessResourceFailureException("find entry failure")
        every { propertyRepo.findEntry(key) } throws failure

        sut.findEntry(key)
            .shouldBeLeft()
            .shouldBeInstanceOf<PersistenceFailure>()
    }

    // getEntry() /////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Test
    fun `getEntry() Returns entry when found and not expired`() {
        val key = aRandomUniqueString()
        val value = aRandomUniqueString()
        propertyRepo.upsert(key, value)

        sut.getEntry(key)
            .shouldBeRight()
            .value shouldBe value
    }

    @Test
    fun `getEntry() Returns PropertyNotFound when key is missing`() {
        sut.getEntry(aRandomUniqueString())
            .shouldBeLeft()
            .shouldBeInstanceOf<PropertyNotFound>()
    }

    @Test
    fun `getEntry() Returns PropertyNotFound when entry is expired`() {
        val key = aRandomUniqueString()
        val ttl = aRandomTtl()
        // upsert calls timeProvider.now() once, getEntry calls it once
        every { timeProvider.now() } returns now andThen now.plusSeconds(ttl + 10)
        propertyRepo.upsert(key, aRandomUniqueString(), ttl)

        sut.getEntry(key)
            .shouldBeLeft()
            .shouldBeInstanceOf<PropertyNotFound>()
    }

    @Test
    fun `getEntry() Returns PersistenceFailure when repository throws`() {
        val key = aRandomUniqueString()
        val failure = DataAccessResourceFailureException("get entry failure")
        every { propertyRepo.findEntry(key) } throws failure

        sut.getEntry(key)
            .shouldBeLeft()
            .shouldBeInstanceOf<PersistenceFailure>()
            .shouldBeInstanceOf<GetPropertyFailure>()
    }

}
