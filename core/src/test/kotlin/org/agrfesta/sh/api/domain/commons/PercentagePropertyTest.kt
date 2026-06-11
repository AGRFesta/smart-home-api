package org.agrfesta.sh.api.domain.commons

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bigDecimal
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.merge
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.agrfesta.sh.api.core.domain.commons.Percentage
import org.agrfesta.sh.api.core.domain.commons.average
import org.agrfesta.test.annotations.PropertyBasedTest
import org.agrfesta.test.property.pbtConfig
import org.agrfesta.test.property.percentage
import org.junit.jupiter.api.Test
import java.math.BigDecimal

@PropertyBasedTest
class PercentagePropertyTest {

    @Test
    fun `value is always within 0 and 1`() {
        runBlocking {
            checkAll(pbtConfig, Arb.percentage()) { p ->
                (p.value in BigDecimal.ZERO..BigDecimal.ONE) shouldBe true
            }
        }
    }

    @Test
    fun `ofHundreds round-trips back to the same percentage`() {
        runBlocking {
            checkAll(pbtConfig, Arb.percentage()) { p ->
                Percentage.ofHundreds(p.value.movePointRight(2)).value.compareTo(p.value) shouldBe 0
            }
        }
    }

    @Test
    fun `rejects values outside 0 and 1`() {
        runBlocking {
            val outOfRange = Arb.bigDecimal(BigDecimal("-1000"), BigDecimal("-0.0000000001"))
                .merge(Arb.bigDecimal(BigDecimal("1.0000000001"), BigDecimal("1000")))
            checkAll(pbtConfig, outOfRange) { value ->
                shouldThrow<IllegalArgumentException> { Percentage(value) }
                    .message shouldBe "Percentage must be between 0 and 1, is $value."
            }
        }
    }

    @Test
    fun `average of a non-empty collection lies within min and max`() {
        runBlocking {
            checkAll(pbtConfig, Arb.list(Arb.percentage(), 1..20)) { percentages ->
                val avg = percentages.average()!!
                val min = percentages.minOf { it.value }
                val max = percentages.maxOf { it.value }
                (avg in min..max) shouldBe true
            }
        }
    }
}
