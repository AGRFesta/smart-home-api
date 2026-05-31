package org.agrfesta.sh.api.core.application.usecases

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.agrfesta.sh.api.core.application.ports.inbounds.UpsertPropertyBatchUseCase
import org.agrfesta.sh.api.core.application.ports.outbounds.home.HomeStateRefreshPublisher
import org.agrfesta.sh.api.core.application.ports.outbounds.settings.PropertyRepository
import org.agrfesta.sh.api.core.domain.commons.PropertyUpsertEntry
import org.agrfesta.sh.api.core.domain.failures.DuplicatePropertyKeys
import org.agrfesta.sh.api.core.domain.failures.EmptyPropertyBatch
import org.agrfesta.sh.api.core.domain.failures.PropertyBatchTooLarge
import org.agrfesta.sh.api.core.domain.failures.PropertyRepositoryError
import org.agrfesta.test.mothers.aRandomTtl
import org.agrfesta.test.mothers.aRandomUniqueString
import org.junit.jupiter.api.Test

class UpsertPropertyBatchServiceTest {
    private val propertyRepository: PropertyRepository = mockk()
    private val homeStateRefreshPublisher: HomeStateRefreshPublisher = mockk(relaxUnitFun = true)
    private val sut = UpsertPropertyBatchService(propertyRepository, homeStateRefreshPublisher)

    @Test fun `execute() returns EmptyPropertyBatch when entries list is empty`() {
        sut.execute(emptyList()).shouldBeLeft() shouldBe EmptyPropertyBatch
    }

    @Test fun `execute() returns PropertyBatchTooLarge when entries exceed max batch size`() {
        val tooManyEntries = (1..(UpsertPropertyBatchUseCase.MAX_BATCH_SIZE + 1))
            .map { PropertyUpsertEntry(aRandomUniqueString(), aRandomUniqueString()) }

        sut.execute(tooManyEntries).shouldBeLeft() shouldBe
            PropertyBatchTooLarge(UpsertPropertyBatchUseCase.MAX_BATCH_SIZE)
    }

    @Test fun `execute() returns DuplicatePropertyKeys when entries contain duplicate keys`() {
        val dupKey = aRandomUniqueString()
        val entries = listOf(
            PropertyUpsertEntry(dupKey, aRandomUniqueString()),
            PropertyUpsertEntry(aRandomUniqueString(), aRandomUniqueString()),
            PropertyUpsertEntry(dupKey, aRandomUniqueString())
        )

        sut.execute(entries).shouldBeLeft() shouldBe DuplicatePropertyKeys
    }

    @Test fun `execute() returns Unit on success`() {
        val entries = listOf(
            PropertyUpsertEntry(aRandomUniqueString(), aRandomUniqueString(), aRandomTtl()),
            PropertyUpsertEntry(aRandomUniqueString(), aRandomUniqueString())
        )
        every { propertyRepository.upsertBatch(entries) } returns Unit.right()

        sut.execute(entries).shouldBeRight() shouldBe Unit
    }

    @Test fun `execute() returns PropertyRepositoryError when repository fails`() {
        val entries = listOf(PropertyUpsertEntry(aRandomUniqueString(), aRandomUniqueString()))
        every { propertyRepository.upsertBatch(entries) } returns PropertyRepositoryError.left()

        sut.execute(entries).shouldBeLeft() shouldBe PropertyRepositoryError
    }

    @Test fun `execute() publishes home state refresh after a successful batch upsert`() {
        val entries = listOf(PropertyUpsertEntry(aRandomUniqueString(), aRandomUniqueString()))
        every { propertyRepository.upsertBatch(entries) } returns Unit.right()

        sut.execute(entries)

        verify { homeStateRefreshPublisher.publish() }
    }

    @Test fun `execute() does not publish home state refresh when the batch upsert fails`() {
        val entries = listOf(PropertyUpsertEntry(aRandomUniqueString(), aRandomUniqueString()))
        every { propertyRepository.upsertBatch(entries) } returns PropertyRepositoryError.left()

        sut.execute(entries)

        verify(exactly = 0) { homeStateRefreshPublisher.publish() }
    }

    @Test fun `execute() does not publish home state refresh on validation failure`() {
        sut.execute(emptyList())

        verify(exactly = 0) { homeStateRefreshPublisher.publish() }
    }
}
