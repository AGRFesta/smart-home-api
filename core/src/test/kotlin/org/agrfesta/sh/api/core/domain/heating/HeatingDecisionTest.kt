package org.agrfesta.sh.api.core.domain.heating

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.agrfesta.sh.api.core.domain.commons.Percentage
import org.agrfesta.sh.api.core.domain.commons.Temperature
import org.agrfesta.sh.api.core.domain.devices.ActuatorStatus
import org.junit.jupiter.api.Test
import java.util.UUID

class HeatingDecisionTest {

    @Test
    fun `decideComfort() returns NONE when there are no areas`() {
        // Given
        val areas = emptyList<HeatableAreaSnapshot>()

        // When
        val command = decideComfort(areas)

        // Then
        command shouldBe HeaterCommand.NONE
    }

    @Test
    fun `decideComfort() returns ON when an area is below its target range`() {
        // Given
        val area = aSnapshot(
            current = Temperature.of("18"),
            target = Temperature.of("20") // current < target - HYSTERESIS(1°C)
        )

        // When
        val command = decideComfort(listOf(area))

        // Then
        command shouldBe HeaterCommand.ON
    }

    @Test
    fun `decideComfort() returns OFF when all areas are above their target range`() {
        // Given
        val areas = listOf(
            aSnapshot(current = Temperature.of("22"), target = Temperature.of("20")),
            aSnapshot(current = Temperature.of("25"), target = Temperature.of("20"))
        ) // each current > target + HYSTERESIS(1°C)

        // When
        val command = decideComfort(areas)

        // Then
        withClue("no area requires heating (all above target + hysteresis) -> heater OFF") {
            command shouldBe HeaterCommand.OFF
        }
    }

    @Test
    fun `decideComfort() returns ON when an in-band area has heater status ON`() {
        // Given
        val area = aSnapshot(
            current = Temperature.of("20.5"),
            target = Temperature.of("20"), // in-band: target - H .. target + H
            heaterStatus = ActuatorStatus.ON
        )

        // When
        val command = decideComfort(listOf(area))

        // Then
        withClue("in-band area with heater ON keeps requiring heat -> heater ON") {
            command shouldBe HeaterCommand.ON
        }
    }

    @Test
    fun `decideComfort() returns OFF when an in-band area has heater status OFF`() {
        // Given
        val area = aSnapshot(
            current = Temperature.of("19.5"),
            target = Temperature.of("20"), // in-band: target - H .. target + H
            heaterStatus = ActuatorStatus.OFF
        )

        // When
        val command = decideComfort(listOf(area))

        // Then
        withClue("in-band area with heater OFF does not require heat -> heater OFF") {
            command shouldBe HeaterCommand.OFF
        }
    }

    @Test
    fun `decideComfort() returns ON when an in-band area has undefined heater and current is at or below target`() {
        // Given
        val area = aSnapshot(
            current = Temperature.of("20"),
            target = Temperature.of("20"), // in-band, current <= target
            heaterStatus = ActuatorStatus.UNDEFINED
        )

        // When
        val command = decideComfort(listOf(area))

        // Then
        withClue("in-band area, undefined heater, current <= target -> requires heat -> heater ON") {
            command shouldBe HeaterCommand.ON
        }
    }

    @Test
    fun `decideComfort() returns OFF when an in-band area has undefined heater and current is above target`() {
        // Given
        val area = aSnapshot(
            current = Temperature.of("20.5"),
            target = Temperature.of("20"), // in-band, current > target
            heaterStatus = ActuatorStatus.UNDEFINED
        )

        // When
        val command = decideComfort(listOf(area))

        // Then
        withClue("in-band area, undefined heater, current > target -> no heat -> heater OFF") {
            command shouldBe HeaterCommand.OFF
        }
    }

    @Test
    fun `decideComfort() returns OFF when the only area has an unavailable current temperature`() {
        // Given
        val area = aSnapshot(
            current = null, // reading unavailable
            target = Temperature.of("20")
        )

        // When
        val command = decideComfort(listOf(area))

        // Then
        withClue("unavailable current temperature is treated as not requiring heat -> heater OFF") {
            command shouldBe HeaterCommand.OFF
        }
    }

    @Test
    fun `decideComfort() returns OFF when the only area has no target temperature`() {
        // Given
        val area = aSnapshot(
            current = Temperature.of("18"),
            target = null // no target configured
        )

        // When
        val command = decideComfort(listOf(area))

        // Then
        withClue("an area with no target temperature does not require heat -> heater OFF") {
            command shouldBe HeaterCommand.OFF
        }
    }

    @Test
    fun `decideEconomy() returns NONE when there are no areas`() {
        // Given
        val areas = emptyList<HeatableAreaSnapshot>()

        // When
        val command = decideEconomy(areas, threshold = Percentage.of("0.5"))

        // Then
        command shouldBe HeaterCommand.NONE
    }

    @Test
    fun `decideEconomy() returns OFF when any area is above its target range, even if others demand heat`() {
        // Given
        val areas = listOf(
            aSnapshot(current = Temperature.of("25"), target = Temperature.of("20")), // above range
            aSnapshot(current = Temperature.of("15"), target = Temperature.of("20")) // below range -> demands heat
        )

        // When
        val command = decideEconomy(areas, threshold = Percentage.of("0.5"))

        // Then
        withClue("an area above target range forces anti-overheat -> heater OFF") {
            command shouldBe HeaterCommand.OFF
        }
    }

    @Test
    fun `decideEconomy() returns ON when the demand ratio is exactly at the threshold`() {
        // Given
        val areas = listOf(demandsHeat(), inBandNoDemand()) // demand ratio = 1/2 = 0.5

        // When
        val command = decideEconomy(areas, threshold = Percentage.of("0.5"))

        // Then
        withClue("demand ratio (0.5) >= threshold (0.5) -> heater ON") {
            command shouldBe HeaterCommand.ON
        }
    }

    @Test
    fun `decideEconomy() returns OFF when the true demand ratio is below the threshold but HALF_UP would round it up`() {
        // Given
        val areas = List(5) { demandsHeat() } + List(3) { inBandNoDemand() } // demand ratio = 5/8 = 0.625

        // When
        val command = decideEconomy(areas, threshold = Percentage.of("0.63"))

        // Then
        withClue("true ratio (0.625) < threshold (0.63) -> heater OFF; HALF_UP rounding to 0.63 must not flip it ON") {
            command shouldBe HeaterCommand.OFF
        }
    }

    @Test
    fun `decideEconomy() returns OFF when the demand ratio is below the threshold`() {
        // Given
        val areas = listOf(demandsHeat(), inBandNoDemand(), inBandNoDemand()) // demand ratio = 1/3 ≈ 0.33

        // When
        val command = decideEconomy(areas, threshold = Percentage.of("0.5"))

        // Then
        withClue("demand ratio (0.33) < threshold (0.5) -> heater OFF") {
            command shouldBe HeaterCommand.OFF
        }
    }
}

private fun aSnapshot(
    current: Temperature? = null,
    target: Temperature? = null,
    heaterStatus: ActuatorStatus = ActuatorStatus.UNDEFINED,
    areaId: UUID = UUID.randomUUID()
) = HeatableAreaSnapshot(areaId, current, target, heaterStatus)

/** An area below its target range: demands heat, contributes to the demand ratio numerator. */
private fun demandsHeat() = aSnapshot(current = Temperature.of("15"), target = Temperature.of("20"))

/** An area in-band with heater OFF: no demand and not above range, so it never forces anti-overheat OFF. */
private fun inBandNoDemand() = aSnapshot(
    current = Temperature.of("20.5"),
    target = Temperature.of("20"),
    heaterStatus = ActuatorStatus.OFF
)
