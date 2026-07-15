package org.agrfesta.sh.api.core.application.usecases.notifications

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.agrfesta.sh.api.core.application.ports.outbounds.RandomGenerator
import org.agrfesta.sh.api.core.application.ports.outbounds.TimeProvider
import org.agrfesta.sh.api.core.application.ports.outbounds.alerts.AlertsRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.notifications.NotificationDispatcher
import org.agrfesta.sh.api.core.domain.alerts.AlertLifecycle
import org.agrfesta.sh.api.core.domain.alerts.AlertStatus
import org.agrfesta.sh.api.core.domain.failures.AlertRepositoryError
import org.agrfesta.sh.api.core.domain.failures.NotificationRepositoryError
import org.agrfesta.sh.api.core.domain.notifications.Notification
import org.agrfesta.sh.api.core.domain.notifications.NotificationEvent
import org.agrfesta.sh.api.core.domain.notifications.ReminderPolicy
import org.agrfesta.sh.api.domain.anAlert
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID

class SendAlertRemindersServiceTest {

    private val alertsRepository: AlertsRepository = mockk()
    private val notificationDispatcher: NotificationDispatcher = mockk()
    private val randomGenerator: RandomGenerator = mockk()
    private val timeProvider: TimeProvider = mockk()
    private val cadence = Duration.ofDays(1)

    private val sut = SendAlertRemindersService(
        alertsRepository,
        notificationDispatcher,
        ReminderPolicy(cadence),
        randomGenerator,
        timeProvider
    )

    init {
        every { randomGenerator.uuid() } returns UUID.randomUUID()
        every { timeProvider.now() } returns Instant.now()
        every { notificationDispatcher.dispatch(any()) } returns Unit.right()
        every { alertsRepository.updateLastNotifiedAt(any(), any()) } returns Unit.right()
    }

    @Test
    fun `execute() dispatches a REMINDER and re-arms last_notified_at for a due OPEN alert`() {
        // Given
        val now = Instant.now()
        val notificationId = UUID.randomUUID()
        val dueAlert = anAlert(
            lifecycle = AlertLifecycle.Open,
            details = "battery=10%",
            lastNotifiedAt = now.minus(cadence).minusSeconds(1) // older than the cadence: due
        )
        every { alertsRepository.getAlerts(AlertStatus.OPEN) } returns listOf(dueAlert).right()
        every { timeProvider.now() } returns now
        every { randomGenerator.uuid() } returns notificationId
        every { notificationDispatcher.dispatch(any()) } returns Unit.right()
        every { alertsRepository.updateLastNotifiedAt(dueAlert.uuid, now) } returns Unit.right()

        // When
        sut.execute()

        // Then
        val dispatched = slot<Notification>()
        verify(exactly = 1) { notificationDispatcher.dispatch(capture(dispatched)) }
        withClue("the reminder should project the still-open alert") {
            dispatched.captured.uuid shouldBe notificationId
            dispatched.captured.alertUuid shouldBe dueAlert.uuid
            dispatched.captured.event shouldBe NotificationEvent.REMINDER
            dispatched.captured.sentAt shouldBe now
            dispatched.captured.payload shouldBe "battery=10%"
        }
        withClue("the emitted reminder must re-arm the cadence") {
            verify(exactly = 1) { alertsRepository.updateLastNotifiedAt(dueAlert.uuid, now) }
        }
    }

    @Test
    fun `execute() dispatches nothing for an OPEN alert notified within the cadence`() {
        // Given
        val now = Instant.now()
        val notDueAlert = anAlert(
            lifecycle = AlertLifecycle.Open,
            lastNotifiedAt = now.minusSeconds(1) // within the cadence: not due
        )
        every { alertsRepository.getAlerts(AlertStatus.OPEN) } returns listOf(notDueAlert).right()
        every { timeProvider.now() } returns now

        // When
        sut.execute()

        // Then
        withClue("a reminder must respect the cadence, not fire on every scan") {
            verify(exactly = 0) { notificationDispatcher.dispatch(any()) }
            verify(exactly = 0) { alertsRepository.updateLastNotifiedAt(any(), any()) }
        }
    }

    /**
     * Regression guard (#194): without the current OPEN alerts no safe reminder decision is possible —
     * the whole scan is skipped, nothing is dispatched and no cadence is re-armed.
     */
    @Test
    fun `execute() skips the whole scan when the open alerts cannot be read`() {
        // Given
        every { alertsRepository.getAlerts(AlertStatus.OPEN) } returns AlertRepositoryError.left()

        // When
        sut.execute()

        // Then
        verify(exactly = 0) { notificationDispatcher.dispatch(any()) }
        verify(exactly = 0) { alertsRepository.updateLastNotifiedAt(any(), any()) }
    }

    @Test
    fun `execute() does not re-arm the cadence when the REMINDER dispatch fails`() {
        // Given
        val now = Instant.now()
        val dueAlert = anAlert(
            lifecycle = AlertLifecycle.Open,
            lastNotifiedAt = now.minus(cadence).minusSeconds(1) // older than the cadence: due
        )
        every { alertsRepository.getAlerts(AlertStatus.OPEN) } returns listOf(dueAlert).right()
        every { timeProvider.now() } returns now
        every { notificationDispatcher.dispatch(any()) } returns NotificationRepositoryError.left()

        // When
        sut.execute()

        // Then
        withClue("a failed reminder must leave last_notified_at untouched, so the next scan retries") {
            verify(exactly = 0) { alertsRepository.updateLastNotifiedAt(any(), any()) }
        }
    }
}
