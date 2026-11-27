package org.agrfesta.sh.api.core.application.usecases.heating

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.agrfesta.sh.api.core.application.usecases.heating.HeatingStrategySelector.Companion.HEATING_STRATEGY_KEY
import org.agrfesta.sh.api.core.domain.commons.Percentage
import org.agrfesta.sh.api.core.domain.commons.Temperature
import org.agrfesta.sh.api.core.domain.devices.ActuatorStatus
import org.agrfesta.sh.api.core.domain.heating.HeatableAreaSnapshot
import org.agrfesta.sh.api.core.domain.heating.HeaterCommand
import org.agrfesta.sh.api.core.domain.heating.SharedHeatingStrategy.COMFORT
import org.agrfesta.sh.api.core.domain.heating.SharedHeatingStrategy.ECONOMY
import org.agrfesta.sh.api.utils.FakePropertyRepository
import org.junit.jupiter.api.Test
import java.util.UUID

class HeatingStrategySelectorTest {
    private val propertyRepository = FakePropertyRepository()

    private val sut = HeatingStrategySelector(
        defaultStrategy = ECONOMY,
        economyThreshold = Percentage.of("0.5"),
        propertyRepository = propertyRepository
    )

    /**
     * Discriminating input: 1 of 3 areas below range, 2 in-band with heater OFF (no demand, none above range).
     * COMFORT -> ON (at least one area demands heat); ECONOMY -> OFF (demand ratio 1/3 < threshold 0.5).
     */
    private val discriminatingAreas = listOf(
        aSnapshot(current = Temperature.of("15"), target = Temperature.of("20")),
        aSnapshot(current = Temperature.of("20.5"), target = Temperature.of("20"), heaterStatus = ActuatorStatus.OFF),
        aSnapshot(current = Temperature.of("20.5"), target = Temperature.of("20"), heaterStatus = ActuatorStatus.OFF)
    )

    @Test
    fun `select() returns the comfort decision when the configured strategy is COMFORT`() {
        // Given
        propertyRepository.givenProperty(HEATING_STRATEGY_KEY, COMFORT.name)

        // When
        val decide = sut.select()

        // Then
        withClue("COMFORT selected -> comfort decider; one area demands heat -> heater ON") {
            decide(discriminatingAreas) shouldBe HeaterCommand.ON
        }
    }

    @Test
    fun `select() returns the economy decision when the configured strategy is ECONOMY`() {
        // Given
        propertyRepository.givenProperty(HEATING_STRATEGY_KEY, ECONOMY.name)

        // When
        val decide = sut.select()

        // Then
        withClue("ECONOMY selected -> economy decider; demand ratio 1/3 < threshold 0.5 -> heater OFF") {
            decide(discriminatingAreas) shouldBe HeaterCommand.OFF
        }
    }

    @Test
    fun `select() resolves the configured strategy ignoring case`() {
        // Given
        propertyRepository.givenProperty(HEATING_STRATEGY_KEY, COMFORT.name.lowercase())

        // When
        val decide = sut.select()

        // Then
        withClue("'comfort' resolves to COMFORT -> comfort decider; one area demands heat -> heater ON") {
            decide(discriminatingAreas) shouldBe HeaterCommand.ON
        }
    }

    @Test
    fun `select() resolves the configured strategy ignoring surrounding whitespace`() {
        // Given
        propertyRepository.givenProperty(HEATING_STRATEGY_KEY, "  comfort  ")

        // When
        val decide = sut.select()

        // Then
        withClue("'  comfort  ' trims to COMFORT -> comfort decider -> heater ON") {
            decide(discriminatingAreas) shouldBe HeaterCommand.ON
        }
    }

    @Test
    fun `select() falls back to the default strategy when the configured value is invalid`() {
        // Given — default is ECONOMY
        propertyRepository.givenProperty(HEATING_STRATEGY_KEY, "not-a-strategy")

        // When / Then
        val decide = shouldNotThrowAny { sut.select() } // must fall back, not blow up on an unknown name
        withClue("invalid value -> default ECONOMY decider; demand ratio 1/3 < threshold 0.5 -> heater OFF") {
            decide(discriminatingAreas) shouldBe HeaterCommand.OFF
        }
    }

    @Test
    fun `select() falls back to the default strategy when the entry is missing`() {
        // Given — default is ECONOMY
        withClue("the repository should hold no HEATING_STRATEGY_KEY entry") {
            propertyRepository.storedValue(HEATING_STRATEGY_KEY) shouldBe null
        }

        // When / Then
        val decide = shouldNotThrowAny { sut.select() } // missing entry must fall back, not blow up
        withClue("missing entry -> default ECONOMY decider; demand ratio 1/3 < threshold 0.5 -> heater OFF") {
            decide(discriminatingAreas) shouldBe HeaterCommand.OFF
        }
    }

    @Test
    fun `select() falls back to the default strategy when the property fetch fails`() {
        // Given — default is ECONOMY
        propertyRepository.givenProperty(HEATING_STRATEGY_KEY, COMFORT.name)
        propertyRepository.givenReadFails()

        // When / Then
        val decide = shouldNotThrowAny { sut.select() } // fetch error must fall back, not blow up
        withClue("fetch error -> default ECONOMY decider; demand ratio 1/3 < threshold 0.5 -> heater OFF") {
            decide(discriminatingAreas) shouldBe HeaterCommand.OFF
        }
    }
}

private fun aSnapshot(
    current: Temperature? = null,
    target: Temperature? = null,
    heaterStatus: ActuatorStatus = ActuatorStatus.UNDEFINED,
    areaId: UUID = UUID.randomUUID()
) = HeatableAreaSnapshot(areaId, current, target, heaterStatus)
