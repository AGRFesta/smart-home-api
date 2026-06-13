package org.agrfesta.sh.api.core.application.usecases

import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DeviceAggregateRepository
import org.agrfesta.sh.api.domain.aDeviceAggregate
import org.junit.jupiter.api.Test
import java.util.UUID

class GetDeviceServiceTest {
    private val deviceAggregateRepository: DeviceAggregateRepository = mockk()

    private val sut = GetDeviceService(deviceAggregateRepository)

    @Test
    fun `execute() returns the device aggregate the repository returns`() {
        // Given
        val deviceId = UUID.randomUUID()
        val aggregate = aDeviceAggregate(uuid = deviceId)
        every { deviceAggregateRepository.findById(deviceId) } returns aggregate.right()

        // When
        val result = sut.execute(deviceId).shouldBeRight()

        // Then
        result shouldBe aggregate
        verify { deviceAggregateRepository.findById(deviceId) }
    }
}
