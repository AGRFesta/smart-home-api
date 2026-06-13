package org.agrfesta.sh.api.core.application.usecases

import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldContainExactly
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicesRepository
import org.agrfesta.sh.api.core.domain.devices.DeviceFeature
import org.agrfesta.sh.api.core.domain.devices.DeviceStatus
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.domain.aDevice
import org.junit.jupiter.api.Test

class GetDevicesServiceTest {
    private val devicesRepository: DevicesRepository = mockk()

    private val sut = GetDevicesService(devicesRepository)

    @Test
    fun `execute() returns the devices the repository returns for the given filters`() {
        // Given
        val provider = Provider.SWITCHBOT
        val status = DeviceStatus.PAIRED
        val feature = DeviceFeature.SENSOR
        val devices = listOf(aDevice(), aDevice())
        every { devicesRepository.getDevices(provider, status, feature) } returns devices.right()

        // When
        val result = sut.execute(provider, status, feature).shouldBeRight()

        // Then
        result.shouldContainExactly(devices)
        verify { devicesRepository.getDevices(provider, status, feature) }
    }
}
