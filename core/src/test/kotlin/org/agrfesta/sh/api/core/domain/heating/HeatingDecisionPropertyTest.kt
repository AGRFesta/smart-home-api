package org.agrfesta.sh.api.core.domain.heating

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.agrfesta.sh.api.core.domain.commons.Temperature
import org.agrfesta.sh.api.core.domain.devices.ActuatorStatus
import org.agrfesta.test.annotations.PropertyBasedTest
import org.agrfesta.test.property.pbtConfig
import org.agrfesta.test.property.percentage
import org.junit.jupiter.api.Test
import java.math.BigInteger
import java.util.UUID

@PropertyBasedTest
class HeatingDecisionPropertyTest {

    /**
     * The ECONOMY decision must be exact: ON if `count / size >= threshold`, with no rounding artifacts
     * at the boundary. We build a set with a known demand count (no area above its range, so anti-overheat
     * never short-circuits) and check the decision against an **independent** oracle: exact integer
     * cross-multiplication on the rational `num / den` form of the threshold. This integer arithmetic is
     * deliberately different from the production `BigDecimal` multiplication, so the property is a genuine
     * spec check (bidirectional) rather than a copy of the implementation formula.
     */
    @Test
    fun `decideEconomy() turns ON exactly when the demand ratio meets the threshold, with no rounding`() {
        runBlocking {
            checkAll(
                pbtConfig,
                Arb.int(0..12),
                Arb.int(0..12),
                Arb.percentage()
            ) { demanding, nonDemanding, threshold ->
                // Given a non-empty set with a known demand count and no area above its range
                if (demanding + nonDemanding == 0) return@checkAll
                val areas = List(demanding) { demandsHeat() } + List(nonDemanding) { inBandNoDemand() }
                val size = demanding + nonDemanding

                // When
                val command = decideEconomy(areas, threshold)

                // Then: exact rational comparison count/size >= num/den  <=>  count*den >= num*size
                val den = BigInteger.TEN.pow(threshold.value.scale()) // Arb.percentage() yields a non-negative scale
                val num = threshold.value.unscaledValue()
                val meetsThreshold = demanding.toBigInteger() * den >= num * size.toBigInteger()

                withClue("demanding=$demanding size=$size threshold=${threshold.value}") {
                    command shouldBe if (meetsThreshold) HeaterCommand.ON else HeaterCommand.OFF
                }
            }
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
