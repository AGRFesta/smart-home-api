package org.agrfesta.sh.api.core.application.usecases.heating

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.verify
import org.agrfesta.sh.api.core.application.ports.outbounds.home.HomeStateRefreshPublisher
import org.agrfesta.sh.api.core.application.usecases.heating.HeatingStrategySelector.Companion.HEATING_STRATEGY_KEY
import org.agrfesta.sh.api.core.domain.failures.PropertyRepositoryError
import org.agrfesta.sh.api.core.domain.heating.SharedHeatingStrategy.COMFORT
import org.agrfesta.sh.api.core.domain.heating.SharedHeatingStrategy.ECONOMY
import org.agrfesta.sh.api.utils.FakePropertyRepository
import org.junit.jupiter.api.Test

class SetHeatingStrategyServiceTest {
    private val propertyRepository = FakePropertyRepository()
    private val homeStateRefreshPublisher: HomeStateRefreshPublisher = mockk(relaxUnitFun = true)
    private val sut = SetHeatingStrategyService(propertyRepository, homeStateRefreshPublisher)

    @Test fun `execute() saves heating_strategy as COMFORT when the strategy is COMFORT`() {
        // Given
        propertyRepository.givenProperty(HEATING_STRATEGY_KEY, "ECONOMY")

        // When
        val result = sut.execute(COMFORT)

        // Then
        result.shouldBeRight()
        withClue("heating.strategy should be saved as \"COMFORT\"") {
            propertyRepository.storedValue(HEATING_STRATEGY_KEY) shouldBe "COMFORT"
        }
    }

    @Test fun `execute() saves heating_strategy as ECONOMY when the strategy is ECONOMY`() {
        // Given
        propertyRepository.givenProperty(HEATING_STRATEGY_KEY, "COMFORT")

        // When
        val result = sut.execute(ECONOMY)

        // Then
        result.shouldBeRight()
        withClue("heating.strategy should be saved as \"ECONOMY\"") {
            propertyRepository.storedValue(HEATING_STRATEGY_KEY) shouldBe "ECONOMY"
        }
    }

    @Test fun `execute() publishes a home state refresh after saving the strategy`() {
        // Given
        propertyRepository.givenProperty(HEATING_STRATEGY_KEY, "ECONOMY")

        // When
        sut.execute(COMFORT)

        // Then
        verify { homeStateRefreshPublisher.publish() }
    }

    @Test fun `execute() returns PropertyRepositoryError and keeps the strategy when the save fails`() {
        // Given
        propertyRepository.givenProperty(HEATING_STRATEGY_KEY, "ECONOMY")
        propertyRepository.givenUpsertFails()

        // When
        val result = sut.execute(COMFORT)

        // Then
        result.shouldBeLeft() shouldBe PropertyRepositoryError
        withClue("heating.strategy should keep its previous value when the save fails") {
            propertyRepository.storedValue(HEATING_STRATEGY_KEY) shouldBe "ECONOMY"
        }
        withClue("connected clients should receive no update when nothing changed") {
            verify(exactly = 0) { homeStateRefreshPublisher.publish() }
        }
    }
}
