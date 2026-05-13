package org.agrfesta.sh.api.domain.commons

import io.kotest.matchers.shouldBe
import org.agrfesta.sh.api.core.domain.commons.Temperature
import org.agrfesta.sh.api.core.domain.commons.average
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory

class TemperatureTest {

    @TestFactory
    fun averageCalculations() = listOf(
        listOf<Temperature>() to null,
        listOf(Temperature.of("0")) to Temperature.of("0"),
        listOf(Temperature.of("20"), Temperature.of("21")) to Temperature.of("20.5"),
        listOf(
            Temperature.of("17"),
            Temperature.of("21"),
            Temperature.of("15")
        ) to Temperature.of("17.67"),
        listOf(
            Temperature.of("-20"),
            Temperature.of("2.34"),
            Temperature.of("1.7")
        ) to Temperature.of("-5.32"),
    ).map {
        dynamicTest("${it.first} -> ${it.second}") {
            it.first.average() shouldBe it.second
        }
    }

}
