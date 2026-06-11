package org.agrfesta.sh.api.domain.commons

import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.agrfesta.sh.api.core.domain.commons.Temperature
import org.agrfesta.sh.api.core.domain.commons.average
import org.agrfesta.test.annotations.PropertyBasedTest
import org.agrfesta.test.property.pbtConfig
import org.agrfesta.test.property.temperature
import org.junit.jupiter.api.Test
import kotlin.math.sign

@PropertyBasedTest
class TemperaturePropertyTest {

    @Test
    fun `of is idempotent`() {
        runBlocking {
            checkAll(pbtConfig, Arb.temperature()) { t ->
                Temperature.of(t.value) shouldBe t
            }
        }
    }

    @Test
    fun `compareTo is consistent with the underlying value`() {
        runBlocking {
            checkAll(pbtConfig, Arb.temperature(), Arb.temperature()) { a, b ->
                a.compareTo(b).sign shouldBe a.value.compareTo(b.value).sign
            }
        }
    }

    @Test
    fun `plus is commutative`() {
        runBlocking {
            checkAll(pbtConfig, Arb.temperature(), Arb.temperature()) { a, b ->
                (a + b) shouldBe (b + a)
            }
        }
    }

    @Test
    fun `subtracting the added temperature returns the original`() {
        runBlocking {
            checkAll(pbtConfig, Arb.temperature(), Arb.temperature()) { a, b ->
                ((a + b) - b) shouldBe a
            }
        }
    }

    @Test
    fun `average of a non-empty collection lies within min and max`() {
        runBlocking {
            checkAll(pbtConfig, Arb.list(Arb.temperature(), 1..20)) { temps ->
                val avg = temps.average()!!
                (avg in temps.min()..temps.max()) shouldBe true
            }
        }
    }
}
