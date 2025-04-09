package org.agrfesta.sh.api.domain.commons

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory

class TemperatureTest {

    @TestFactory
    fun averageCalculations() = listOf(
        listOf<Temperature>() to null,
        listOf(Temperature("0")) to Temperature("0"),
        listOf(Temperature("20"), Temperature("21")) to Temperature("20.5"),
        listOf(Temperature("17"), Temperature("21"), Temperature("15")) to Temperature("17.67"),
        listOf(Temperature("-20"), Temperature("2.34"), Temperature("1.7")) to Temperature("-5.32"),
    ).map {
        dynamicTest("${it.first} -> ${it.second}") {
            it.first.average() shouldBe it.second
        }
    }

}
