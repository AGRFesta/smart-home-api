package org.agrfesta.sh.api.core.application.usecases.heating

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.verify
import org.agrfesta.sh.api.core.application.ports.outbounds.home.HomeStateRefreshPublisher
import org.agrfesta.sh.api.core.application.usecases.heating.EvaluateHeatingStateService.Companion.HEATING_ENABLED_KEY
import org.agrfesta.sh.api.core.domain.failures.PropertyRepositoryError
import org.agrfesta.sh.api.utils.FakePropertyRepository
import org.junit.jupiter.api.Test

class SetHeatingEnabledServiceTest {
    private val propertyRepository = FakePropertyRepository()
    private val homeStateRefreshPublisher: HomeStateRefreshPublisher = mockk(relaxUnitFun = true)
    private val sut = SetHeatingEnabledService(propertyRepository, homeStateRefreshPublisher)

    @Test fun `execute() saves heating_enabled as true when heating is enabled`() {
        // Given
        propertyRepository.givenProperty(HEATING_ENABLED_KEY, "false")

        // When
        val result = sut.execute(true)

        // Then
        result.shouldBeRight()
        withClue("heating.enabled should be saved as \"true\"") {
            propertyRepository.storedValue(HEATING_ENABLED_KEY) shouldBe "true"
        }
    }

    @Test fun `execute() saves heating_enabled as false when heating is disabled`() {
        // Given
        propertyRepository.givenProperty(HEATING_ENABLED_KEY, "true")

        // When
        val result = sut.execute(false)

        // Then
        result.shouldBeRight()
        withClue("heating.enabled should be saved as \"false\"") {
            propertyRepository.storedValue(HEATING_ENABLED_KEY) shouldBe "false"
        }
    }

    @Test fun `execute() publishes exactly one home state refresh after saving the flag`() {
        // Given
        propertyRepository.givenProperty(HEATING_ENABLED_KEY, "false")

        // When
        sut.execute(true)

        // Then
        verify(exactly = 1) { homeStateRefreshPublisher.publish() }
    }

    @Test fun `execute() returns PropertyRepositoryError, keeps the flag and does not publish when the save fails`() {
        // Given
        propertyRepository.givenProperty(HEATING_ENABLED_KEY, "true")
        propertyRepository.givenUpsertFails()

        // When
        val result = sut.execute(false)

        // Then
        result.shouldBeLeft() shouldBe PropertyRepositoryError
        withClue("heating.enabled should keep its previous value when the save fails") {
            propertyRepository.storedValue(HEATING_ENABLED_KEY) shouldBe "true"
        }
        withClue("connected clients should receive no update when nothing changed") {
            verify(exactly = 0) { homeStateRefreshPublisher.publish() }
        }
    }
}
