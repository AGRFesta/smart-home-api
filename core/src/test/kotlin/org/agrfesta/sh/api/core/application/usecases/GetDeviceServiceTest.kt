package org.agrfesta.sh.api.core.application.usecases

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.agrfesta.sh.api.core.application.ports.outbounds.alerts.AlertsRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DeviceAggregateRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DeviceBatteryRepository
import org.agrfesta.sh.api.core.domain.alerts.AlertStatus
import org.agrfesta.sh.api.core.domain.alerts.AlertTarget
import org.agrfesta.sh.api.core.domain.alerts.AlertType
import org.agrfesta.sh.api.core.domain.failures.AlertRepositoryError
import org.agrfesta.sh.api.domain.aDeviceAggregate
import org.agrfesta.sh.api.domain.anAlert
import org.junit.jupiter.api.Test
import java.util.UUID

class GetDeviceServiceTest {
    private val deviceAggregateRepository: DeviceAggregateRepository = mockk()
    private val deviceBatteryRepository: DeviceBatteryRepository = mockk()
    private val alertsRepository: AlertsRepository = mockk()

    private val sut = GetDeviceService(deviceAggregateRepository, deviceBatteryRepository, alertsRepository)

    init {
        every { deviceBatteryRepository.findBy(any()) } returns null.right()
        every { alertsRepository.getAlerts(AlertStatus.OPEN) } returns emptyList<Nothing>().right()
    }

    @Test
    fun `execute() returns the device aggregate the repository returns`() {
        // Given
        val deviceId = UUID.randomUUID()
        val aggregate = aDeviceAggregate(uuid = deviceId)
        every { deviceAggregateRepository.findById(deviceId) } returns aggregate.right()

        // When
        val result = sut.execute(deviceId).shouldBeRight()

        // Then
        result shouldBe aggregate.copy(activeAlerts = emptySet())
        verify { deviceAggregateRepository.findById(deviceId) }
    }

    @Test
    fun `execute() enriches the aggregate with the cached battery level`() {
        // Given
        val deviceId = UUID.randomUUID()
        val aggregate = aDeviceAggregate(uuid = deviceId, batteryLevel = null)
        every { deviceAggregateRepository.findById(deviceId) } returns aggregate.right()
        every { deviceBatteryRepository.findBy(aggregate) } returns 64.right()

        // When
        val result = sut.execute(deviceId).shouldBeRight()

        // Then
        withClue("aggregate should carry the cached battery level returned by deviceBatteryRepository") {
            result.batteryLevel shouldBe 64
        }
    }

    @Test
    fun `execute() enriches the aggregate with the types of the open alerts targeting the device`() {
        // Given
        val deviceId = UUID.randomUUID()
        val aggregate = aDeviceAggregate(uuid = deviceId)
        every { deviceAggregateRepository.findById(deviceId) } returns aggregate.right()
        every { alertsRepository.getAlerts(AlertStatus.OPEN) } returns
            listOf(anAlert(type = AlertType.BATTERY_LOW, target = AlertTarget.Device(deviceId))).right()

        // When
        val result = sut.execute(deviceId).shouldBeRight()

        // Then
        withClue("activeAlerts") {
            result.activeAlerts shouldBe setOf(AlertType.BATTERY_LOW)
        }
    }

    @Test
    fun `execute() activeAlerts is empty when the open alerts do not target the device`() {
        // Given
        val deviceId = UUID.randomUUID()
        val aggregate = aDeviceAggregate(uuid = deviceId)
        every { deviceAggregateRepository.findById(deviceId) } returns aggregate.right()
        every { alertsRepository.getAlerts(AlertStatus.OPEN) } returns listOf(
            anAlert(target = AlertTarget.Device(UUID.randomUUID())),
            anAlert(target = AlertTarget.Global)
        ).right()

        // When
        val result = sut.execute(deviceId).shouldBeRight()

        // Then
        withClue("activeAlerts") {
            result.activeAlerts shouldBe emptySet<AlertType>()
        }
    }

    @Test
    fun `execute() activeAlerts is null when the open alerts lookup fails`() {
        // Given
        val deviceId = UUID.randomUUID()
        val aggregate = aDeviceAggregate(uuid = deviceId)
        every { deviceAggregateRepository.findById(deviceId) } returns aggregate.right()
        every { alertsRepository.getAlerts(AlertStatus.OPEN) } returns AlertRepositoryError.left()

        // When
        val result = sut.execute(deviceId).shouldBeRight()

        // Then
        withClue("activeAlerts") {
            result.activeAlerts shouldBe null
        }
    }
}
