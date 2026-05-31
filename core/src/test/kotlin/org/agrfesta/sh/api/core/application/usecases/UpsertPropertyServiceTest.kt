package org.agrfesta.sh.api.core.application.usecases

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.agrfesta.sh.api.core.application.ports.outbounds.home.HomeStateRefreshPublisher
import org.agrfesta.sh.api.core.application.ports.outbounds.settings.PropertyRepository
import org.agrfesta.sh.api.core.domain.failures.PropertyRepositoryError
import org.agrfesta.test.mothers.aRandomTtl
import org.agrfesta.test.mothers.aRandomUniqueString
import org.junit.jupiter.api.Test

class UpsertPropertyServiceTest {
    private val propertyRepository: PropertyRepository = mockk()
    private val homeStateRefreshPublisher: HomeStateRefreshPublisher = mockk(relaxUnitFun = true)
    private val sut = UpsertPropertyService(propertyRepository, homeStateRefreshPublisher)

    @Test fun `execute() publishes home state refresh after a successful upsert`() {
        every { propertyRepository.upsert(any(), any(), any()) } returns Unit.right()

        sut.execute("key", "value", 3600)

        verify { homeStateRefreshPublisher.publish() }
    }

    @Test fun `execute() does not publish home state refresh when the upsert fails`() {
        every { propertyRepository.upsert(any(), any(), any()) } returns PropertyRepositoryError.left()

        sut.execute("key", "value", 3600)

        verify(exactly = 0) { homeStateRefreshPublisher.publish() }
    }

    @Test fun `execute() returns Unit on success`() {
        val key = aRandomUniqueString()
        val value = aRandomUniqueString()
        val ttl = aRandomTtl()
        every { propertyRepository.upsert(key, value, ttl) } returns Unit.right()

        sut.execute(key, value, ttl).shouldBeRight() shouldBe Unit
    }

    @Test fun `execute() returns PropertyRepositoryError when repository fails`() {
        val key = aRandomUniqueString()
        val value = aRandomUniqueString()
        every { propertyRepository.upsert(key, value, null) } returns PropertyRepositoryError.left()

        sut.execute(key, value).shouldBeLeft() shouldBe PropertyRepositoryError
    }
}
