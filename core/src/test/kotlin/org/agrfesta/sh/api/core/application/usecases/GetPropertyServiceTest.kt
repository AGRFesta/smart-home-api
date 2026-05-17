package org.agrfesta.sh.api.core.application.usecases

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.agrfesta.sh.api.core.application.ports.outbounds.settings.PropertyRepository
import org.agrfesta.sh.api.core.domain.commons.PropertyEntry
import org.agrfesta.sh.api.core.domain.failures.PropertyNotFound
import org.agrfesta.sh.api.core.domain.failures.PropertyRepositoryError
import org.agrfesta.test.mothers.aRandomUniqueString
import org.junit.jupiter.api.Test

class GetPropertyServiceTest {
    private val propertyRepository: PropertyRepository = mockk()
    private val sut = GetPropertyService(propertyRepository)

    @Test fun `execute() returns PropertyEntry on success`() {
        val key = aRandomUniqueString()
        val entry = PropertyEntry(value = aRandomUniqueString())
        every { propertyRepository.getEntry(key) } returns entry.right()

        sut.execute(key).shouldBeRight() shouldBe entry
    }

    @Test fun `execute() returns PropertyNotFound when key does not exist`() {
        val key = aRandomUniqueString()
        every { propertyRepository.getEntry(key) } returns PropertyNotFound.left()

        sut.execute(key).shouldBeLeft() shouldBe PropertyNotFound
    }

    @Test fun `execute() returns PropertyRepositoryError when repository fails`() {
        val key = aRandomUniqueString()
        every { propertyRepository.getEntry(key) } returns PropertyRepositoryError.left()

        sut.execute(key).shouldBeLeft() shouldBe PropertyRepositoryError
    }
}
