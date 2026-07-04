package org.agrfesta.sh.api.core.application.usecases

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.agrfesta.sh.api.core.application.ports.outbounds.RandomGenerator
import org.agrfesta.sh.api.core.application.ports.outbounds.TimeProvider
import org.agrfesta.sh.api.core.application.ports.outbounds.alerts.AlertsRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DeviceBatteryRepository
import org.agrfesta.sh.api.core.domain.alerts.Alert
import org.agrfesta.sh.api.core.domain.alerts.AlertLifecycle
import org.agrfesta.sh.api.core.domain.alerts.AlertStatus
import org.agrfesta.sh.api.core.domain.alerts.AlertTarget
import org.agrfesta.sh.api.core.domain.alerts.AlertType
import org.agrfesta.sh.api.core.domain.alerts.BatteryLowRule
import org.agrfesta.sh.api.core.domain.failures.AlertRepositoryError
import org.agrfesta.sh.api.core.domain.failures.BatteryLookupError
import org.agrfesta.sh.api.domain.aDevice
import org.agrfesta.sh.api.domain.anAlert
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class EvaluateAlertsServiceTest {

    private val alertsRepository: AlertsRepository = mockk()
    private val deviceBatteryRepository: DeviceBatteryRepository = mockk()
    private val randomGenerator: RandomGenerator = mockk()
    private val timeProvider: TimeProvider = mockk()

    private val sut = EvaluateAlertsService(
        alertsRepository,
        deviceBatteryRepository,
        BatteryLowRule(trigger = 15, clear = 25),
        randomGenerator,
        timeProvider
    )

    init {
        every { randomGenerator.uuid() } returns UUID.randomUUID()
        every { timeProvider.now() } returns Instant.now()
        every { alertsRepository.create(any()) } returns Unit.right()
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
