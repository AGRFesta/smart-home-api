package org.agrfesta.sh.api.core.domain.notifications

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class ReminderDueTest {

    private val now = Instant.parse("2026-07-09T12:00:00Z")
    private val cadence = Duration.ofDays(1)

    @Test
    fun `is due when the last notification is older than the cadence`() {
        // Given
        val lastNotifiedAt = now.minus(cadence).minusSeconds(1)

        // When
        val due = reminderDue(lastNotifiedAt = lastNotifiedAt, now = now, cadence = cadence)

        // Then
        withClue("a reminder should be due once the cadence has elapsed since the last notification") {
            due shouldBe true
        }
    }

    @Test
    fun `is not due when the last notification is within the cadence`() {
        // Given
        val lastNotifiedAt = now.minusSeconds(1) // just notified (e.g. just-opened alert)

        // When
        val due = reminderDue(lastNotifiedAt = lastNotifiedAt, now = now, cadence = cadence)

        // Then
        withClue("a reminder should not be due again before the cadence has elapsed") {
            due shouldBe false
        }
    }

    @Test
    fun `is due when the alert has never been notified`() {
        // Given
        val lastNotifiedAt: Instant? = null // e.g. the opened emission failed: compensate at the next scan

        // When
        val due = reminderDue(lastNotifiedAt = lastNotifiedAt, now = now, cadence = cadence)

        // Then
        withClue("a never-notified alert should be immediately due, compensating a lost opened emission") {
            due shouldBe true
        }
    }

    @Test
    fun `is due when the elapsed time equals the cadence exactly`() {
        // Given
        val lastNotifiedAt = now.minus(cadence)

        // When
        val due = reminderDue(lastNotifiedAt = lastNotifiedAt, now = now, cadence = cadence)

        // Then
        withClue("a reminder should be due at the exact cadence boundary, not one instant later") {
            due shouldBe true
        }
    }
}
