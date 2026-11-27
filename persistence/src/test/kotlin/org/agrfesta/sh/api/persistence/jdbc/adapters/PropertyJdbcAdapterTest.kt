package org.agrfesta.sh.api.persistence.jdbc.adapters

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.matchers.shouldBe
import io.mockk.every
import org.agrfesta.sh.api.core.application.ports.outbounds.settings.PropertyRepository
import org.agrfesta.sh.api.core.domain.commons.PropertyUpsertEntry
import org.agrfesta.sh.api.core.domain.failures.PropertyRepositoryError
import org.agrfesta.sh.api.utils.PropertyRepositoryContract
import org.agrfesta.test.mothers.aRandomTtl
import org.agrfesta.test.mothers.aRandomUniqueString
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataAccessResourceFailureException
import java.time.Instant

class PropertyJdbcAdapterTest : AbstractJdbcAdapterTest() {

    @Autowired private lateinit var adapter: PropertyJdbcAdapter

    @Nested
    inner class Contract : PropertyRepositoryContract() {
        override val sut: PropertyRepository get() = adapter

        override fun givenNow(instant: Instant) {
            every { timeProvider.now() } returns instant
        }
    }

    // upsert()

    @Test
    fun `upsert() Returns PropertyRepositoryError when repository throws`() {
        val key = aRandomUniqueString()
        val failure = DataAccessResourceFailureException("upsert failure")
        every { propertyRepo.upsert(key, any(), any()) } throws failure

        adapter.upsert(key, aRandomUniqueString(), aRandomTtl())
            .shouldBeLeft()
            .shouldBe(PropertyRepositoryError)
    }

    // upsertBatch()

    @Test
    fun `upsertBatch() Returns PropertyRepositoryError when repository throws`() {
        val entries = listOf(PropertyUpsertEntry(aRandomUniqueString(), aRandomUniqueString()))
        val failure = DataAccessResourceFailureException("batch upsert failure")
        every { propertyRepo.upsertBatch(entries) } throws failure

        adapter.upsertBatch(entries)
            .shouldBeLeft()
            .shouldBe(PropertyRepositoryError)
    }

    // findEntry()

    @Test
    fun `findEntry() Returns PropertyRepositoryError when repository throws`() {
        val key = aRandomUniqueString()
        val failure = DataAccessResourceFailureException("find entry failure")
        every { propertyRepo.findEntry(key) } throws failure

        adapter.findEntry(key)
            .shouldBeLeft()
            .shouldBe(PropertyRepositoryError)
    }

    // getEntry()

    @Test
    fun `getEntry() Returns PropertyRepositoryError when repository throws`() {
        val key = aRandomUniqueString()
        val failure = DataAccessResourceFailureException("get entry failure")
        every { propertyRepo.findEntry(key) } throws failure

        adapter.getEntry(key)
            .shouldBeLeft()
            .shouldBe(PropertyRepositoryError)
    }
}
