package org.agrfesta.sh.api.domain.commons

import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.agrfesta.sh.api.core.domain.commons.AbsoluteHumidity
import org.agrfesta.sh.api.core.domain.commons.Percentage
import org.agrfesta.test.annotations.PropertyBasedTest
import org.agrfesta.test.property.pbtConfig
import org.agrfesta.test.property.percentage
import org.agrfesta.test.property.temperature
import org.junit.jupiter.api.Test
import java.math.BigDecimal

@PropertyBasedTest
class AbsoluteHumidityPropertyTest {

    // Realistic climate band: keeps Kelvin positive and the Taylor-series exponent well-conditioned.
    private val realisticTemperature = Arb.temperature(BigDecimal("-40"), BigDecimal("50"))

    @Test
    fun `absolute humidity is never negative`() {
        runBlocking {
            checkAll(pbtConfig, realisticTemperature, Arb.percentage()) { t, rh ->
                (AbsoluteHumidity(t, rh).value.signum() >= 0) shouldBe true
            }
        }
    }

    @Test
    fun `zero relative humidity yields zero absolute humidity`() {
        runBlocking {
            checkAll(pbtConfig, realisticTemperature) { t ->
                AbsoluteHumidity(t, Percentage(BigDecimal.ZERO)).value.signum() shouldBe 0
            }
        }
    }

    @Test
    fun `absolute humidity is monotonic in relative humidity`() {
        runBlocking {
            checkAll(pbtConfig, realisticTemperature, Arb.percentage(), Arb.percentage()) { t, rh1, rh2 ->
                val (lo, hi) = if (rh1.value <= rh2.value) rh1 to rh2 else rh2 to rh1
                (AbsoluteHumidity(t, lo).value <= AbsoluteHumidity(t, hi).value) shouldBe true
            }
        }
    }
}
