package org.agrfesta.sh.api.domain.commons

import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.agrfesta.sh.api.core.domain.commons.AbsoluteHumidity
import org.agrfesta.test.annotations.PropertyBasedTest
import org.agrfesta.test.mothers.aRandomThermoHygroData
import org.agrfesta.test.property.pbtConfig
import org.agrfesta.test.property.percentage
import org.agrfesta.test.property.temperature
import org.junit.jupiter.api.Test
import java.math.BigDecimal

@PropertyBasedTest
class ThermoHygroDataPropertyTest {

    // Realistic climate band: keeps Kelvin positive and the Taylor-series exponent well-conditioned.
    private val realisticTemperature = Arb.temperature(BigDecimal("-40"), BigDecimal("50"))

    @Test
    fun `construction preserves its components`() {
        runBlocking {
            checkAll(pbtConfig, realisticTemperature, Arb.percentage()) { t, rh ->
                val data = aRandomThermoHygroData(temperature = t, relativeHumidity = rh)
                data.temperature shouldBe t
                data.relativeHumidity shouldBe rh
            }
        }
    }

    @Test
    fun `calculateAbsoluteHumidity is consistent with building AbsoluteHumidity from its components`() {
        runBlocking {
            checkAll(pbtConfig, realisticTemperature, Arb.percentage()) { t, rh ->
                val data = aRandomThermoHygroData(temperature = t, relativeHumidity = rh)
                data.calculateAbsoluteHumidity().value shouldBe AbsoluteHumidity(t, rh).value
            }
        }
    }
}
