package org.agrfesta.sh.api.domain.commons

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class PercentageTest {

    @TestFactory
    fun validPercentageValues() =
        listOf(BigDecimal(0.0), BigDecimal(0.25), BigDecimal(0.5), BigDecimal(0.75), BigDecimal(1.0))
            .map {
                dynamicTest("Percentage from $it") { Percentage(it).value shouldBe it }
            }

    @TestFactory
    fun invalidPercentageValues() =
        listOf(BigDecimal(-0.000000001), BigDecimal(1.00000001), BigDecimal(100), BigDecimal(50.1))
            .map {
                dynamicTest("$it is an invalid percentage value") {
                    shouldThrow<IllegalArgumentException> { Percentage(it) }
                        .apply { message shouldBe "Percentage must be between 0 and 1, is $it." }
                }
            }

    @TestFactory
    fun percentageToHundredsConversions() = listOf(
        Percentage(BigDecimal.ZERO) to PercentageHundreds(BigDecimal.ZERO),
        Percentage(BigDecimal("0.33333")) to PercentageHundreds(BigDecimal("33.333")),
        Percentage(BigDecimal("0.512")) to PercentageHundreds(BigDecimal("51.2")),
        Percentage(BigDecimal("0.99")) to PercentageHundreds(BigDecimal("99")),
        Percentage(BigDecimal.ONE) to PercentageHundreds(BigDecimal(100.0))
    ).map { (percentage, expectedHundreds) ->
        dynamicTest("$percentage -> $expectedHundreds") {
            percentage.toHundreds() shouldBe expectedHundreds
        }
    }

    @TestFactory
    fun percentageAsText() = listOf(
        Percentage(BigDecimal.ZERO) to "0",
        Percentage(BigDecimal("0.33333")) to "0.33333",
        Percentage(BigDecimal("0.512")) to "0.512",
        Percentage(BigDecimal("0.99")) to "0.99",
        Percentage(BigDecimal.ONE) to "1"
    ).map { (percentage, text) ->
        dynamicTest("$percentage -> $text") {
            percentage.asText() shouldBe text
        }
    }

    @TestFactory
    fun percentageToString() = listOf(
        Percentage(BigDecimal.ZERO) to "0%",
        Percentage(BigDecimal("0.33333")) to "33.333%",
        Percentage(BigDecimal("0.512")) to "51.2%",
        Percentage(BigDecimal("0.99")) to "99%",
        Percentage(BigDecimal.ONE) to "100%"
    ).map { (percentage, text) ->
        dynamicTest("$percentage -> $text") {
            percentage.toString() shouldBe text
        }
    }

    @TestFactory
    fun validPercentageHundredsValues() =
        listOf(BigDecimal(0.0), BigDecimal(25.5), BigDecimal(59.0), BigDecimal("70.333"), BigDecimal(100.0))
            .map {
                dynamicTest("Percentage Hundreds from $it") { PercentageHundreds(it).value shouldBe it }
            }

    @TestFactory
    fun invalidPercentageHundredsValues() =
        listOf(BigDecimal(-0.000000001), BigDecimal(100.00000001), BigDecimal(150), BigDecimal(-50.1))
            .map {
                dynamicTest("$it is an invalid percentage hundreds value") {
                    shouldThrow<IllegalArgumentException> { PercentageHundreds(it) }
                        .apply { message shouldBe "Percentage hundreds must be between 0 and 100, is $it." }
                }
            }

    @TestFactory
    fun hundredsToPercentageConversions() = listOf(
        PercentageHundreds(BigDecimal.ZERO) to Percentage(BigDecimal.ZERO),
        PercentageHundreds(BigDecimal("0.333")) to Percentage(BigDecimal("0.00333")),
        PercentageHundreds(BigDecimal("1.2")) to Percentage(BigDecimal("0.012")),
        PercentageHundreds(BigDecimal("77")) to Percentage(BigDecimal("0.77")),
        PercentageHundreds(BigDecimal(100.0)) to Percentage(BigDecimal.ONE)
    ).map { (hundreds, expectedPercentage) ->
        dynamicTest("$hundreds -> $expectedPercentage") {
            hundreds.toPercentage() shouldBe expectedPercentage
        }
    }

    @TestFactory
    fun hundredsToString() = listOf(
        PercentageHundreds(BigDecimal.ZERO) to "0%",
        PercentageHundreds(BigDecimal("33.333")) to "33.333%",
        PercentageHundreds(BigDecimal("1.2")) to "1.2%",
        PercentageHundreds(BigDecimal("77")) to "77%",
        PercentageHundreds(100) to "100%"
    ).map { (hundreds, text) ->
        dynamicTest("$hundreds -> $text") {
            hundreds.toString() shouldBe text
        }
    }

}
