package org.agrfesta.sh.api.domain.commons

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal
import org.agrfesta.sh.api.core.domain.commons.AbsoluteHumidity
import org.agrfesta.sh.api.core.domain.commons.Percentage
import org.agrfesta.sh.api.core.domain.commons.Temperature

class AbsoluteHumidityTest {

    @TestFactory
    fun validPercentageValues() = listOf(
            Triple(Temperature.of("7.33"), Percentage.ofHundreds("23.33"), BigDecimal("1.84668")),
            Triple(Temperature.of("40.0"), Percentage.ofHundreds("100.0"), BigDecimal("51.18221")),
            Triple(Temperature.of("34.9"), Percentage.ofHundreds("75.9"), BigDecimal("29.90525")),
            Triple(Temperature.of("10.0"), Percentage.ofHundreds("50.0"), BigDecimal("4.69675")),
            Triple(Temperature.of("30.5"), Percentage.ofHundreds("0.1"), BigDecimal("0.03119")),
            Triple(Temperature.of("18.1"), Percentage.ofHundreds("99.0"), BigDecimal("15.29155")),
            Triple(Temperature.of("0.0"), Percentage.ofHundreds("100.0"), BigDecimal("4.84977"))
        ).map {
            dynamicTest("Temp ${it.first}°C, ${it.second} -> ${it.third}g/m³") {
                AbsoluteHumidity(it.first, it.second).value shouldBe it.third
            }
        }

}
