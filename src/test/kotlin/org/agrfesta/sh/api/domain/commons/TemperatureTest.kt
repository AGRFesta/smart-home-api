package org.agrfesta.sh.api.domain.commons

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
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

    @TestFactory
    fun scaleIndependentEquality() = listOf(
        Temperature("1.0") to Temperature("1"),
        Temperature("20.00") to Temperature("20"),
        Temperature("1") to Temperature("1.0"),
        Temperature("20") to Temperature("20.00"),
        Temperature("21.5") to Temperature("21.50"),
        Temperature("0.0") to Temperature("0"),
        Temperature("-5.00") to Temperature("-5"),
    ).map { (first, second) ->
        dynamicTest("$first == $second") {
            first shouldBe second
        }
    }

    @TestFactory
    fun constructorVariants() = listOf(
        Temperature("20.5") to Temperature(20.5),
        Temperature("20") to Temperature(20),
        Temperature("0") to Temperature(0),
        Temperature("-5.25") to Temperature(-5.25),
    ).map { (fromString, fromNumber) ->
        dynamicTest("Temperature from string vs number: $fromString == $fromNumber") {
            fromString shouldBe fromNumber
        }
    }

    @TestFactory
    fun arithmeticOperations() = listOf(
        Triple(Temperature("10"), Temperature("5"), Temperature("15")) to "plus",
        Triple(Temperature("10"), Temperature("5"), Temperature("5")) to "minus",
        Triple(Temperature("10"), Temperature("2"), Temperature("20")) to "times",
        Triple(Temperature("10"), Temperature("2"), Temperature("5")) to "div",
    ).map { (triple, operation) ->
        val (first, second, expected) = triple
        dynamicTest("$first $operation $second = $expected") {
            when (operation) {
                "plus" -> (first + second) shouldBe expected
                "minus" -> (first - second) shouldBe expected
                "times" -> (first * second) shouldBe expected
                "div" -> (first / second) shouldBe expected
            }
        }
    }

    @Test
    fun unaryMinusOperation() {
        -Temperature("10") shouldBe Temperature("-10")
        -Temperature("-5") shouldBe Temperature("5")
        -Temperature("0") shouldBe Temperature("0")
    }

    @TestFactory
    fun comparisonOperations() = listOf(
        Triple(Temperature("10"), Temperature("5"), true) to "greater than",
        Triple(Temperature("5"), Temperature("10"), false) to "greater than",
        Triple(Temperature("10"), Temperature("10"), false) to "greater than",
        Triple(Temperature("5"), Temperature("10"), true) to "less than",
        Triple(Temperature("10"), Temperature("5"), false) to "less than",
        Triple(Temperature("10"), Temperature("10"), false) to "less than",
    ).map { (triple, operation) ->
        val (first, second, expected) = triple
        dynamicTest("$first $operation $second = $expected") {
            when (operation) {
                "greater than" -> (first > second) shouldBe expected
                "less than" -> (first < second) shouldBe expected
            }
        }
    }

    @Test
    fun comparisonWithEqualValues() {
        Temperature("10") >= Temperature("10") shouldBe true
        Temperature("10") <= Temperature("10") shouldBe true
        Temperature("10") >= Temperature("5") shouldBe true
        Temperature("5") <= Temperature("10") shouldBe true
    }

}
