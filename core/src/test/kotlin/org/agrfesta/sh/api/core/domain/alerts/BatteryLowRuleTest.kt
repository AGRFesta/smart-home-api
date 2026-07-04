package org.agrfesta.sh.api.core.domain.alerts

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class BatteryLowRuleTest {

    private val rule = BatteryLowRule(trigger = 15, clear = 25)

    @Test
    fun `rejects construction when clear threshold is not strictly greater than trigger`() {
        // Given
        val trigger = 25
        val clear = 25

        // When / Then
        shouldThrow<IllegalArgumentException> {
            BatteryLowRule(trigger = trigger, clear = clear)
        }
    }

    @Test
    fun `condition is met when no alert is open and the level is at or below the trigger`() {
        // Given
        val level = 15 // at the trigger threshold

        // When
        val met = rule.conditionMet(level = level, alertOpen = false)

        // Then
        met shouldBe true
    }

    @Test
    fun `condition is not met when no alert is open and the level is above the trigger`() {
        // Given
        val level = 16 // just above the trigger, inside the hysteresis band

        // When
        val met = rule.conditionMet(level = level, alertOpen = false)

        // Then
        withClue("a level above the trigger (16 > 15) must not open a new alert, even inside the band") {
            met shouldBe false
        }
    }

    @Test
    fun `condition stays met when an alert is open and the level is inside the hysteresis band`() {
        // Given
        val level = 16 // above the trigger (15) but below the clear (25)

        // When
        val met = rule.conditionMet(level = level, alertOpen = true)

        // Then
        withClue("an open alert must stay open until the level reaches the clear threshold (16 < 25)") {
            met shouldBe true
        }
    }

    @Test
    fun `condition clears when an alert is open and the level reaches the clear threshold`() {
        // Given
        val level = 25 // at the clear threshold

        // When
        val met = rule.conditionMet(level = level, alertOpen = true)

        // Then
        withClue("a level at or above the clear threshold (25 >= 25) must resolve the open alert") {
            met shouldBe false
        }
    }
}
