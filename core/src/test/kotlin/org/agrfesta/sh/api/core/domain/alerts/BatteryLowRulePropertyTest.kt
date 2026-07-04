package org.agrfesta.sh.api.core.domain.alerts

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.agrfesta.test.annotations.PropertyBasedTest
import org.agrfesta.test.property.pbtConfig
import org.junit.jupiter.api.Test
import kotlin.math.max
import kotlin.math.min

@PropertyBasedTest
class BatteryLowRulePropertyTest {

    /**
     * Antitonicity guard: for any thresholds and any fixed alert state, a battery level can never be
     * "low" while a lower level is not — i.e. `conditionMet` never flips back to true as the level
     * rises. This is a one-directional contract (the boundary values are pinned by the example tests
     * in [BatteryLowRuleTest]), so the oracle does not restate the production formula.
     */
    @Test
    fun `for a fixed alert state, conditionMet never turns true as the battery level rises`() {
        runBlocking {
            checkAll(
                pbtConfig,
                Arb.int(0..99), // trigger
                Arb.int(1..50), // clear offset above the trigger (keeps clear > trigger)
                Arb.int(0..100), // one battery level
                Arb.int(0..100), // another battery level
                Arb.boolean() // whether an alert is already open
            ) { trigger, clearOffset, levelA, levelB, alertOpen ->
                // Given a valid hysteresis band and two ordered levels
                val rule = BatteryLowRule(trigger = trigger, clear = trigger + clearOffset)
                val lower = min(levelA, levelB)
                val higher = max(levelA, levelB)

                // When the condition is met at the higher level
                if (rule.conditionMet(level = higher, alertOpen = alertOpen)) {
                    // Then it must also be met at every lower level
                    withClue(
                        "trigger=$trigger clear=${trigger + clearOffset} alertOpen=$alertOpen: " +
                            "condition met at level $higher but not at lower level $lower"
                    ) {
                        rule.conditionMet(level = lower, alertOpen = alertOpen) shouldBe true
                    }
                }
            }
        }
    }
}
