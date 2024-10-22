package org.agrfesta.sh.api.domain.commons

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class AbsoluteHumidityTest {

    @TestFactory
    fun validPercentageValues() = listOf(
            Triple(Temperature("7.33"), RelativeHumidityHundreds("23.33"), BigDecimal("1.84668")),
            Triple(Temperature("40.0"), RelativeHumidityHundreds("100.0"), BigDecimal("51.18221")),
            Triple(Temperature("34.9"), RelativeHumidityHundreds("75.9"), BigDecimal("29.90525")),
            Triple(Temperature("10.0"), RelativeHumidityHundreds("50.0"), BigDecimal("4.69675")),
            Triple(Temperature("30.5"), RelativeHumidityHundreds("0.1"), BigDecimal("0.03119")),
            Triple(Temperature("18.1"), RelativeHumidityHundreds("99.0"), BigDecimal("15.29155")),
            Triple(Temperature("0.0"), RelativeHumidityHundreds("100.0"), BigDecimal("4.84977"))
        ).map {
            dynamicTest("Temp ${it.first}°C, ${it.second} -> ${it.third}g/m³") {
                AbsoluteHumidity(it.first, it.second).value shouldBe it.third
            }
        }

}
