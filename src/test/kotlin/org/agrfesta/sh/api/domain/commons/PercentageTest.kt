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
    fun invalidOfHundredsValues() =
        listOf(BigDecimal("-0.001"), BigDecimal("100.001"), BigDecimal("101"), BigDecimal("-1"))
            .map {
                dynamicTest("$it is an invalid hundreds value") {
                    shouldThrow<IllegalArgumentException> { Percentage.ofHundreds(it) }
                        .apply { message shouldBe "Percentage hundreds must be between 0 and 100, is $it." }
                }
            }

    @TestFactory
    fun ofHundredsFromBigDecimal() = listOf(
        BigDecimal.ZERO to Percentage(BigDecimal.ZERO),
        BigDecimal("0.333") to Percentage(BigDecimal("0.00333")),
        BigDecimal("1.2") to Percentage(BigDecimal("0.012")),
        BigDecimal("77") to Percentage(BigDecimal("0.77")),
        BigDecimal(100.0) to Percentage(BigDecimal.ONE)
    ).map { (hundreds, expected) ->
        dynamicTest("ofHundreds($hundreds) -> $expected") {
            Percentage.ofHundreds(hundreds) shouldBe expected
        }
    }

    @TestFactory
    fun ofHundredsFromInt() = listOf(
        0 to Percentage(BigDecimal.ZERO),
        50 to Percentage(BigDecimal("0.5")),
        100 to Percentage(BigDecimal.ONE)
    ).map { (hundreds, expected) ->
        dynamicTest("ofHundreds($hundreds) -> $expected") {
            Percentage.ofHundreds(hundreds) shouldBe expected
        }
    }

    @TestFactory
    fun ofHundredsFromString() = listOf(
        "0" to Percentage(BigDecimal.ZERO),
        "33.333" to Percentage(BigDecimal("0.33333")),
        "100" to Percentage(BigDecimal.ONE)
    ).map { (hundreds, expected) ->
        dynamicTest("ofHundreds(\"$hundreds\") -> $expected") {
            Percentage.ofHundreds(hundreds) shouldBe expected
        }
    }

    @TestFactory
    fun ofFromString() = listOf(
        "0" to Percentage(BigDecimal.ZERO),
        "0.5" to Percentage(BigDecimal("0.5")),
        "1" to Percentage(BigDecimal.ONE)
    ).map { (str, expected) ->
        dynamicTest("of(\"$str\") -> $expected") {
            Percentage.of(str) shouldBe expected
        }
    }

}
