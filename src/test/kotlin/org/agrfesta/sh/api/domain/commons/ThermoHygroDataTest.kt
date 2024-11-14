package org.agrfesta.sh.api.domain.commons

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class ThermoHygroDataTest {

    @TestFactory
    fun absoluteHumidityCalculations() = listOf(
        Triple(Temperature("7.33"), RelativeHumidity("0.2333"), BigDecimal("1.84668")),
        Triple(Temperature("40.0"), RelativeHumidity("1.0"), BigDecimal("51.18221")),
        Triple(Temperature("34.9"), RelativeHumidity("0.759"), BigDecimal("29.90525")),
        Triple(Temperature("10.0"), RelativeHumidity("0.5"), BigDecimal("4.69675")),
        Triple(Temperature("30.5"), RelativeHumidity("0.001"), BigDecimal("0.03119")),
        Triple(Temperature("18.1"), RelativeHumidity("0.99"), BigDecimal("15.29155")),
        Triple(Temperature("0.0"), RelativeHumidity("1.0"), BigDecimal("4.84977"))
    ).map {
        dynamicTest("Temp ${it.first}°C, ${it.second} -> ${it.third}g/m³") {
            ThermoHygroData(temperature = it.first, relativeHumidity = it.second)
                .calculateAbsoluteHumidity().value shouldBe it.third
        }
    }

}
