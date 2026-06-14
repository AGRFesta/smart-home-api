package org.agrfesta.sh.api.core.application.usecases

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicesRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.ProviderDevicesFactory
import org.agrfesta.sh.api.core.domain.devices.DeviceDriver
import org.agrfesta.sh.api.core.domain.devices.Inspectable
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.core.domain.failures.DeviceNotFound
import org.agrfesta.sh.api.core.domain.failures.DeviceRepositoryError
import org.agrfesta.sh.api.core.domain.failures.DevicesProviderError
import org.agrfesta.sh.api.core.domain.failures.DiagnosticsNotSupported
import org.agrfesta.sh.api.core.domain.failures.DiagnosticsProviderFailure
import org.agrfesta.sh.api.domain.aDevice
import org.agrfesta.test.mothers.aRandomUniqueString
import org.junit.jupiter.api.Test
import java.util.UUID

class InspectDeviceServiceTest {
    private val devicesRepository: DevicesRepository = mockk()

    @Test
    fun `execute() returns DeviceNotFound when the device does not exist`() {
        // Given
        val deviceId = UUID.randomUUID()
        every { devicesRepository.getDeviceById(deviceId) } returns DeviceNotFound(deviceId).left()
        val sut = InspectDeviceService(devicesRepository, deviceFactories = emptySet())

        // When
        val result = sut.execute(deviceId).shouldBeLeft()

        // Then
        result.shouldBeInstanceOf<DeviceNotFound>()
    }

    @Test
    fun `execute() builds the driver via the matching factory and returns its inspect raw body`() {
        // Given
        val deviceId = UUID.randomUUID()
        val device = aDevice(uuid = deviceId, provider = Provider.SWITCHBOT)
        val rawBody = aRandomUniqueString()
        val driver = mockk<DeviceDriver>(moreInterfaces = arrayOf(Inspectable::class))
        every { (driver as Inspectable).inspect() } returns rawBody.right()
        val factory = mockk<ProviderDevicesFactory> {
            every { provider } returns Provider.SWITCHBOT
            every { createDevice(device) } returns driver
        }
        every { devicesRepository.getDeviceById(deviceId) } returns device.right()
        val sut = InspectDeviceService(devicesRepository, setOf(factory))

        // When
        val result = sut.execute(deviceId).shouldBeRight()

        // Then
        result shouldBe rawBody
    }

    @Test
    fun `execute() returns DiagnosticsNotSupported when no factory matches the device provider`() {
        // Given
        val deviceId = UUID.randomUUID()
        val device = aDevice(uuid = deviceId, provider = Provider.NETATMO)
        val switchBotFactory = mockk<ProviderDevicesFactory> {
            every { provider } returns Provider.SWITCHBOT
        }
        every { devicesRepository.getDeviceById(deviceId) } returns device.right()
        val sut = InspectDeviceService(devicesRepository, setOf(switchBotFactory))

        // When
        val result = sut.execute(deviceId).shouldBeLeft()

        // Then
        result shouldBe DiagnosticsNotSupported
    }

    @Test
    fun `execute() returns DiagnosticsNotSupported when the built driver is not Inspectable`() {
        // Given
        val deviceId = UUID.randomUUID()
        val device = aDevice(uuid = deviceId, provider = Provider.SWITCHBOT)
        val nonInspectableDriver = mockk<DeviceDriver>()
        val factory = mockk<ProviderDevicesFactory> {
            every { provider } returns Provider.SWITCHBOT
            every { createDevice(device) } returns nonInspectableDriver
        }
        every { devicesRepository.getDeviceById(deviceId) } returns device.right()
        val sut = InspectDeviceService(devicesRepository, setOf(factory))

        // When
        val result = sut.execute(deviceId).shouldBeLeft()

        // Then
        result shouldBe DiagnosticsNotSupported
    }

    @Test
    fun `execute() returns DiagnosticsProviderFailure surfacing the provider message when inspect fails`() {
        // Given
        val deviceId = UUID.randomUUID()
        val device = aDevice(uuid = deviceId, provider = Provider.SWITCHBOT)
        val driver = mockk<DeviceDriver>(moreInterfaces = arrayOf(Inspectable::class))
        every { (driver as Inspectable).inspect() } returns
            DevicesProviderError(RuntimeException("provider exploded")).left()
        val factory = mockk<ProviderDevicesFactory> {
            every { provider } returns Provider.SWITCHBOT
            every { createDevice(device) } returns driver
        }
        every { devicesRepository.getDeviceById(deviceId) } returns device.right()
        val sut = InspectDeviceService(devicesRepository, setOf(factory))

        // When
        val result = sut.execute(deviceId).shouldBeLeft()

        // Then
        val failure = result.shouldBeInstanceOf<DiagnosticsProviderFailure>()
        failure.message shouldBe "provider exploded"
    }

    @Test
    fun `execute() returns DeviceRepositoryError when the device lookup hits a database error`() {
        // Given
        val deviceId = UUID.randomUUID()
        every { devicesRepository.getDeviceById(deviceId) } returns DeviceRepositoryError.left()
        val sut = InspectDeviceService(devicesRepository, deviceFactories = emptySet())

        // When
        val result = sut.execute(deviceId).shouldBeLeft()

        // Then
        result.shouldBeInstanceOf<DeviceRepositoryError>()
    }
}
