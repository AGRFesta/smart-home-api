package org.agrfesta.sh.api.utils

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.agrfesta.sh.api.core.application.ports.outbounds.settings.PropertyRepository
import org.agrfesta.sh.api.core.domain.commons.PropertyUpsertEntry
import org.agrfesta.sh.api.core.domain.failures.PropertyNotFound
import org.agrfesta.test.mothers.aRandomTtl
import org.agrfesta.test.mothers.aRandomUniqueString
import org.agrfesta.test.mothers.nowNoMills
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Domain outcomes of [PropertyRepository], run on every implementation of the port.
 * Every test writes under keys of its own, so it does not depend on what else the repository holds,
 * and runs with the current instant set to [now].
 * `docs/TDD_WORKFLOW.md` § Contract tests of a fake owns the rules of a contract test.
 */
abstract class PropertyRepositoryContract {

    protected abstract val sut: PropertyRepository

    /**
     * Sets the instant that the implementation under test reads as the current one.
     */
    protected abstract fun givenNow(instant: Instant)

    private val now = nowNoMills()

    @BeforeEach
    fun setCurrentInstant() {
        givenNow(now)
    }

    // upsert()

    @Test
    fun `upsert() Inserts a new entry when key is missing`() {
        val key = aRandomUniqueString()
        val value = aRandomUniqueString()
        val ttl = aRandomTtl()
        sut.findEntry(key).shouldBeRight().shouldBeNull()

        sut.upsert(key, value, ttl).shouldBeRight()

        sut.findEntry(key).shouldBeRight().shouldNotBeNull().also {
            it.value shouldBe value
            it.expiresAt shouldBe now.plusSeconds(ttl)
        }
    }

    @Test
    fun `upsert() Inserts entry with no expiry when ttl is null`() {
        val key = aRandomUniqueString()
        val value = aRandomUniqueString()

        sut.upsert(key, value).shouldBeRight()

        sut.findEntry(key).shouldBeRight().shouldNotBeNull().also {
            it.value shouldBe value
            it.expiresAt.shouldBeNull()
        }
    }

    @Test
    fun `upsert() Updates value and ttl when key already exists`() {
        val key = aRandomUniqueString()
        val newValue = aRandomUniqueString()
        val newTtl = aRandomTtl()
        sut.upsert(key, aRandomUniqueString(), aRandomTtl()).shouldBeRight()

        sut.upsert(key, newValue, newTtl).shouldBeRight()

        sut.findEntry(key).shouldBeRight().shouldNotBeNull().also {
            it.value shouldBe newValue
            it.expiresAt shouldBe now.plusSeconds(newTtl)
        }
    }

    // upsertBatch()

    @Test
    fun `upsertBatch() Inserts all new entries`() {
        val key1 = aRandomUniqueString()
        val value1 = aRandomUniqueString()
        val key2 = aRandomUniqueString()
        val value2 = aRandomUniqueString()
        val entries = listOf(
            PropertyUpsertEntry(key1, value1),
            PropertyUpsertEntry(key2, value2)
        )

        sut.upsertBatch(entries).shouldBeRight()

        sut.findEntry(key1).shouldBeRight().shouldNotBeNull().value shouldBe value1
        sut.findEntry(key2).shouldBeRight().shouldNotBeNull().value shouldBe value2
    }

    @Test
    fun `upsertBatch() Updates existing entries and inserts new ones`() {
        val existingKey = aRandomUniqueString()
        val newKey = aRandomUniqueString()
        val newValueForExisting = aRandomUniqueString()
        val newTtl = aRandomTtl()
        sut.upsert(existingKey, aRandomUniqueString(), aRandomTtl()).shouldBeRight()
        val entries = listOf(
            PropertyUpsertEntry(existingKey, newValueForExisting, newTtl),
            PropertyUpsertEntry(newKey, aRandomUniqueString())
        )

        sut.upsertBatch(entries).shouldBeRight()

        sut.findEntry(existingKey).shouldBeRight().shouldNotBeNull().also {
            it.value shouldBe newValueForExisting
            it.expiresAt shouldBe now.plusSeconds(newTtl)
        }
        sut.findEntry(newKey).shouldBeRight().shouldNotBeNull()
    }

    // findEntry()

    @Test
    fun `findEntry() Returns entry when found and not expired`() {
        val key = aRandomUniqueString()
        val value = aRandomUniqueString()
        sut.upsert(key, value).shouldBeRight()

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
        sut.upsert(key, aRandomUniqueString(), ttl).shouldBeRight()
        givenNow(now.plusSeconds(ttl + 10))

        sut.findEntry(key)
            .shouldBeRight()
            .shouldBeNull()
    }

    @Test
    fun `findEntry() Returns null when the current instant equals expiresAt`() {
        val key = aRandomUniqueString()
        val ttl = aRandomTtl()
        sut.upsert(key, aRandomUniqueString(), ttl).shouldBeRight()
        givenNow(now.plusSeconds(ttl))

        sut.findEntry(key)
            .shouldBeRight()
            .shouldBeNull()
    }

    @Test
    fun `findEntry() Returns entry one millisecond before expiresAt`() {
        val key = aRandomUniqueString()
        val value = aRandomUniqueString()
        val ttl = aRandomTtl()
        sut.upsert(key, value, ttl).shouldBeRight()
        givenNow(now.plusSeconds(ttl).minusMillis(1))

        sut.findEntry(key)
            .shouldBeRight()
            .shouldNotBeNull()
            .value shouldBe value
    }

    // getEntry()

    @Test
    fun `getEntry() Returns entry when found and not expired`() {
        val key = aRandomUniqueString()
        val value = aRandomUniqueString()
        sut.upsert(key, value).shouldBeRight()

        sut.getEntry(key)
            .shouldBeRight()
            .value shouldBe value
    }

    @Test
    fun `getEntry() Returns PropertyNotFound when key is missing`() {
        sut.getEntry(aRandomUniqueString())
            .shouldBeLeft()
            .shouldBe(PropertyNotFound)
    }

    @Test
    fun `getEntry() Returns PropertyNotFound when entry is expired`() {
        val key = aRandomUniqueString()
        val ttl = aRandomTtl()
        sut.upsert(key, aRandomUniqueString(), ttl).shouldBeRight()
        givenNow(now.plusSeconds(ttl + 10))

        sut.getEntry(key)
            .shouldBeLeft()
            .shouldBe(PropertyNotFound)
    }
}
