package org.agrfesta.sh.api.core.application.usecases

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
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DeviceBatteryRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.notifications.NotificationDispatcher
import org.agrfesta.sh.api.core.domain.alerts.Alert
import org.agrfesta.sh.api.core.domain.alerts.AlertLifecycle
import org.agrfesta.sh.api.core.domain.alerts.AlertStatus
import org.agrfesta.sh.api.core.domain.alerts.AlertTarget
import org.agrfesta.sh.api.core.domain.alerts.AlertType
import org.agrfesta.sh.api.core.domain.alerts.BatteryLowRule
import org.agrfesta.sh.api.core.domain.failures.AlertRepositoryError
import org.agrfesta.sh.api.core.domain.failures.BatteryLookupError
import org.agrfesta.sh.api.core.domain.failures.NotificationRepositoryError
import org.agrfesta.sh.api.core.domain.notifications.Notification
import org.agrfesta.sh.api.core.domain.notifications.NotificationEvent
import org.agrfesta.sh.api.domain.aDevice
import org.agrfesta.sh.api.domain.anAlert
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class EvaluateAlertsServiceTest {

    private val alertsRepository: AlertsRepository = mockk()
    private val deviceBatteryRepository: DeviceBatteryRepository = mockk()
    private val notificationDispatcher: NotificationDispatcher = mockk()
    private val randomGenerator: RandomGenerator = mockk()
    private val timeProvider: TimeProvider = mockk()

    private val sut = EvaluateAlertsService(
        alertsRepository,
        deviceBatteryRepository,
        BatteryLowRule(trigger = 15, clear = 25),
        notificationDispatcher,
        randomGenerator,
        timeProvider
    )

    init {
        every { randomGenerator.uuid() } returns UUID.randomUUID()
        every { timeProvider.now() } returns Instant.now()
        every { alertsRepository.create(any()) } returns Unit.right()
        every { alertsRepository.updateLastNotifiedAt(any(), any()) } returns Unit.right()
        every { notificationDispatcher.dispatch(any()) } returns Unit.right()
    }

    @Test
    fun `execute() opens a BATTERY_LOW alert when the battery is at or below the trigger and none is open`() {
        // Given
        val device = aDevice()
        val batteryLevel = 10 // below the trigger (15)
        val newId = UUID.randomUUID()
        val now = Instant.now()
        every { alertsRepository.getAlerts(AlertStatus.OPEN) } returns emptyList<Alert>().right()
        every { deviceBatteryRepository.findBy(device) } returns batteryLevel.right()
        every { randomGenerator.uuid() } returns newId
        every { timeProvider.now() } returns now

        // When
        sut.execute(listOf(device))

        // Then
        val created = slot<Alert>()
        verify(exactly = 1) { alertsRepository.create(capture(created)) }
        created.captured.uuid shouldBe newId
        created.captured.type shouldBe AlertType.BATTERY_LOW
        created.captured.target shouldBe AlertTarget.Device(device.uuid)
        created.captured.lifecycle shouldBe AlertLifecycle.Open
        created.captured.openedAt shouldBe now
        created.captured.details shouldBe "battery=10%"
    }

    @Test
    fun `execute() dispatches an OPENED notification when the alert is opened successfully`() {
        // Given
        val device = aDevice()
        val alertId = UUID.randomUUID()
        val notificationId = UUID.randomUUID()
        val now = Instant.now()
        every { alertsRepository.getAlerts(AlertStatus.OPEN) } returns emptyList<Alert>().right()
        every { deviceBatteryRepository.findBy(device) } returns 10.right() // below the trigger (15)
        every { randomGenerator.uuid() } returns alertId andThen notificationId
        every { timeProvider.now() } returns now
        every { notificationDispatcher.dispatch(any()) } returns Unit.right()

        // When
        sut.execute(listOf(device))

        // Then
        val dispatched = slot<Notification>()
        verify(exactly = 1) { notificationDispatcher.dispatch(capture(dispatched)) }
        withClue("the notification should project the just-opened alert") {
            dispatched.captured.uuid shouldBe notificationId
            dispatched.captured.alertUuid shouldBe alertId
            dispatched.captured.event shouldBe NotificationEvent.OPENED
            dispatched.captured.sentAt shouldBe now
            dispatched.captured.payload shouldBe "battery=10%"
        }
    }

    @Test
    fun `execute() tracks last_notified_at when the OPENED notification dispatch succeeds`() {
        // Given
        val device = aDevice()
        val alertId = UUID.randomUUID()
        val now = Instant.now()
        every { alertsRepository.getAlerts(AlertStatus.OPEN) } returns emptyList<Alert>().right()
        every { deviceBatteryRepository.findBy(device) } returns 10.right() // below the trigger (15)
        every { randomGenerator.uuid() } returns alertId andThen UUID.randomUUID()
        every { timeProvider.now() } returns now
        every { notificationDispatcher.dispatch(any()) } returns Unit.right()
        every { alertsRepository.updateLastNotifiedAt(alertId, now) } returns Unit.right()

        // When
        sut.execute(listOf(device))

        // Then
        verify(exactly = 1) { alertsRepository.updateLastNotifiedAt(alertId, now) }
    }

    @Test
    fun `execute() tracks last_notified_at with the same instant stamped on the OPENED notification`() {
        // Given a clock that advances on every read: the two bookkeeping values must still match
        val device = aDevice()
        val base = Instant.parse("2026-07-11T12:00:00Z")
        every { alertsRepository.getAlerts(AlertStatus.OPEN) } returns emptyList<Alert>().right()
        every { deviceBatteryRepository.findBy(device) } returns 10.right() // below the trigger (15)
        every { timeProvider.now() } returnsMany (0L..5L).map { base.plusSeconds(it) }

        // When
        sut.execute(listOf(device))

        // Then
        val dispatched = slot<Notification>()
        val tracked = slot<Instant>()
        verify(exactly = 1) { notificationDispatcher.dispatch(capture(dispatched)) }
        verify(exactly = 1) { alertsRepository.updateLastNotifiedAt(any(), capture(tracked)) }
        withClue("sentAt and last_notified_at document the same emission and must carry the same instant") {
            tracked.captured shouldBe dispatched.captured.sentAt
        }
    }

    @Test
    fun `execute() does not track last_notified_at when the OPENED notification dispatch fails`() {
        // Given
        val device = aDevice()
        every { alertsRepository.getAlerts(AlertStatus.OPEN) } returns emptyList<Alert>().right()
        every { deviceBatteryRepository.findBy(device) } returns 10.right() // below the trigger (15)
        every { notificationDispatcher.dispatch(any()) } returns NotificationRepositoryError.left()

        // When
        sut.execute(listOf(device))

        // Then
        withClue("a failed dispatch must leave last_notified_at untouched, so the next scan compensates") {
            verify(exactly = 0) { alertsRepository.updateLastNotifiedAt(any(), any()) }
        }
    }

    /**
     * Regression guard (#194): dispatch lives on the success path of the transition save — a failed
     * save must produce no notification, or clients would be notified about an alert that does not exist.
     */
    @Test
    fun `execute() dispatches no notification when the alert creation fails`() {
        // Given
        val device = aDevice()
        every { alertsRepository.getAlerts(AlertStatus.OPEN) } returns emptyList<Alert>().right()
        every { deviceBatteryRepository.findBy(device) } returns 10.right() // below the trigger (15)
        every { alertsRepository.create(any()) } returns AlertRepositoryError.left()

        // When
        sut.execute(listOf(device))

        // Then
        verify(exactly = 0) { notificationDispatcher.dispatch(any()) }
        verify(exactly = 0) { alertsRepository.updateLastNotifiedAt(any(), any()) }
    }

    @Test
    fun `execute() keeps the open alert untouched when the battery is still inside the hysteresis band`() {
        // Given
        val device = aDevice()
        val batteryLevel = 16 // above the trigger (15), below the clear (25)
        val openAlert = anAlert(
            type = AlertType.BATTERY_LOW,
            target = AlertTarget.Device(device.uuid),
            lifecycle = AlertLifecycle.Open
        )
        every { alertsRepository.getAlerts(AlertStatus.OPEN) } returns listOf(openAlert).right()
        every { deviceBatteryRepository.findBy(device) } returns batteryLevel.right()

        // When
        sut.execute(listOf(device))

        // Then
        verify(exactly = 0) { alertsRepository.create(any()) }
        verify(exactly = 0) { alertsRepository.resolve(any()) }
    }

    @Test
    fun `execute() resolves the open alert when the battery reaches the clear threshold`() {
        // Given
        val device = aDevice()
        val batteryLevel = 25 // at the clear threshold
        val now = Instant.now()
        val openAlert = anAlert(
            type = AlertType.BATTERY_LOW,
            target = AlertTarget.Device(device.uuid),
            lifecycle = AlertLifecycle.Open
        )
        every { alertsRepository.getAlerts(AlertStatus.OPEN) } returns listOf(openAlert).right()
        every { deviceBatteryRepository.findBy(device) } returns batteryLevel.right()
        every { timeProvider.now() } returns now
        every { alertsRepository.resolve(any()) } returns Unit.right()

        // When
        sut.execute(listOf(device))

        // Then
        val resolved = slot<Alert>()
        verify(exactly = 1) { alertsRepository.resolve(capture(resolved)) }
        resolved.captured.uuid shouldBe openAlert.uuid
        resolved.captured.lifecycle shouldBe AlertLifecycle.Resolved(now)
        verify(exactly = 0) { alertsRepository.create(any()) }
    }

    @Test
    fun `execute() dispatches a RESOLVED notification when the alert is resolved successfully`() {
        // Given
        val device = aDevice()
        val notificationId = UUID.randomUUID()
        val now = Instant.now()
        val openAlert = anAlert(
            type = AlertType.BATTERY_LOW,
            target = AlertTarget.Device(device.uuid),
            lifecycle = AlertLifecycle.Open,
            details = "battery=10%"
        )
        every { alertsRepository.getAlerts(AlertStatus.OPEN) } returns listOf(openAlert).right()
        every { deviceBatteryRepository.findBy(device) } returns 25.right() // at the clear threshold
        every { randomGenerator.uuid() } returns notificationId
        every { timeProvider.now() } returns now
        every { alertsRepository.resolve(any()) } returns Unit.right()
        every { notificationDispatcher.dispatch(any()) } returns Unit.right()

        // When
        sut.execute(listOf(device))

        // Then
        val dispatched = slot<Notification>()
        verify(exactly = 1) { notificationDispatcher.dispatch(capture(dispatched)) }
        withClue("the notification should project the just-resolved alert") {
            dispatched.captured.uuid shouldBe notificationId
            dispatched.captured.alertUuid shouldBe openAlert.uuid
            dispatched.captured.event shouldBe NotificationEvent.RESOLVED
            dispatched.captured.sentAt shouldBe now
            dispatched.captured.payload shouldBe "battery=10%"
        }
    }

    /**
     * Regression guard (#194): like the opened emission, the `resolved` one lives on the success path
     * of the transition save — a failed resolve must not announce a resolution that did not happen.
     */
    @Test
    fun `execute() dispatches no notification when the alert resolution fails`() {
        // Given
        val device = aDevice()
        val openAlert = anAlert(
            type = AlertType.BATTERY_LOW,
            target = AlertTarget.Device(device.uuid),
            lifecycle = AlertLifecycle.Open
        )
        every { alertsRepository.getAlerts(AlertStatus.OPEN) } returns listOf(openAlert).right()
        every { deviceBatteryRepository.findBy(device) } returns 25.right() // at the clear threshold
        every { alertsRepository.resolve(any()) } returns AlertRepositoryError.left()

        // When
        sut.execute(listOf(device))

        // Then
        verify(exactly = 0) { notificationDispatcher.dispatch(any()) }
    }

    /**
     * Regression guard (skip-on-absent, #193): Redis gives no presence guarantee (cold start, eviction,
     * TTL), so a missing battery value means "not evaluable" — never "condition cleared". Resolving here
     * would make an offline device flap: resolve → reopen → re-notify on every blip.
     */
    @Test
    fun `execute() leaves the open alert untouched when no battery value is cached`() {
        // Given
        val device = aDevice()
        val openAlert = anAlert(
            type = AlertType.BATTERY_LOW,
            target = AlertTarget.Device(device.uuid),
            lifecycle = AlertLifecycle.Open
        )
        every { alertsRepository.getAlerts(AlertStatus.OPEN) } returns listOf(openAlert).right()
        every { deviceBatteryRepository.findBy(device) } returns null.right()

        // When
        sut.execute(listOf(device))

        // Then
        verify(exactly = 0) { alertsRepository.create(any()) }
        verify(exactly = 0) { alertsRepository.resolve(any()) }
    }

    @Test
    fun `execute() skips a device whose battery lookup fails and still evaluates the remaining ones`() {
        // Given
        val failingDevice = aDevice()
        val healthyDevice = aDevice()
        every { alertsRepository.getAlerts(AlertStatus.OPEN) } returns emptyList<Alert>().right()
        every { deviceBatteryRepository.findBy(failingDevice) } returns
            BatteryLookupError(Exception("boom")).left()
        every { deviceBatteryRepository.findBy(healthyDevice) } returns 10.right() // below the trigger

        // When
        sut.execute(listOf(failingDevice, healthyDevice))

        // Then
        val created = slot<Alert>()
        verify(exactly = 1) { alertsRepository.create(capture(created)) }
        created.captured.target shouldBe AlertTarget.Device(healthyDevice.uuid)
        verify(exactly = 0) { alertsRepository.resolve(any()) }
    }

    @Test
    fun `execute() performs no evaluation at all when the open alerts cannot be read`() {
        // Given
        val device = aDevice()
        every { alertsRepository.getAlerts(AlertStatus.OPEN) } returns AlertRepositoryError.left()
        every { deviceBatteryRepository.findBy(device) } returns 10.right() // low: would open an alert

        // When
        sut.execute(listOf(device))

        // Then
        verify(exactly = 0) { alertsRepository.create(any()) }
        verify(exactly = 0) { alertsRepository.resolve(any()) }
    }
}
