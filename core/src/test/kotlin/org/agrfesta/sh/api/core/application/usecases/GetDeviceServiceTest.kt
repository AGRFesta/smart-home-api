package org.agrfesta.sh.api.core.application.usecases

import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DeviceAggregateRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DeviceBatteryRepository
import org.agrfesta.sh.api.domain.aDeviceAggregate
import org.junit.jupiter.api.Test
import java.util.UUID

class GetDeviceServiceTest {
    private val deviceAggregateRepository: DeviceAggregateRepository = mockk()
    private val deviceBatteryRepository: DeviceBatteryRepository = mockk()

    private val sut = GetDeviceService(deviceAggregateRepository, deviceBatteryRepository)

    @Test
    fun `execute() returns the device aggregate the repository returns`() {
        // Given
        val deviceId = UUID.randomUUID()
        val aggregate = aDeviceAggregate(uuid = deviceId)
        every { deviceAggregateRepository.findById(deviceId) } returns aggregate.right()
        every { deviceBatteryRepository.findBy(aggregate) } returns null.right()

        // When
        val result = sut.execute(deviceId).shouldBeRight()

        // Then
        result shouldBe aggregate
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
}
