package org.agrfesta.sh.api.core.application.usecases.devices

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.AirConditioner
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DeviceDriver
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicesRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.ProviderDevicesFactory
import org.agrfesta.sh.api.core.domain.commons.Temperature
import org.agrfesta.sh.api.core.domain.devices.AcFanSpeed
import org.agrfesta.sh.api.core.domain.devices.AcMode
import org.agrfesta.sh.api.core.domain.devices.AcState
import org.agrfesta.sh.api.core.domain.devices.ActuatorStatus
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.core.domain.failures.AcProviderFailure
import org.agrfesta.sh.api.core.domain.failures.ActuatorOperationFailure
import org.agrfesta.sh.api.core.domain.failures.DeviceNotFound
import org.agrfesta.sh.api.core.domain.failures.DeviceRepositoryError
import org.agrfesta.sh.api.core.domain.failures.NotAnAirConditioner
import org.agrfesta.sh.api.domain.aDevice
import org.junit.jupiter.api.Test
import java.util.UUID

class GetAcStateServiceTest {
    private val devicesRepository: DevicesRepository = mockk()

    @Test
    fun `execute() builds the driver via the matching factory and returns its control state`() {
        // Given
        val deviceId = UUID.randomUUID()
        val device = aDevice(uuid = deviceId, provider = Provider.HON)
        val state = AcState(
            power = ActuatorStatus.ON,
            mode = AcMode.COOL,
            targetTemperature = Temperature.of("22"),
            fanSpeed = AcFanSpeed.AUTO,
        )
        val driver = mockk<AirConditioner> { every { getState() } returns state.right() }
        val factory = mockk<ProviderDevicesFactory> {
            every { provider } returns Provider.HON
            every { createDevice(device) } returns driver
        }
        every { devicesRepository.getDeviceById(deviceId) } returns device.right()
        val sut = GetAcStateService(devicesRepository, setOf(factory))

        // When
        val result = sut.execute(deviceId).shouldBeRight()

        // Then
        result shouldBe state
    }

    @Test
    fun `execute() returns DeviceNotFound when the device does not exist`() {
        // Given
        val deviceId = UUID.randomUUID()
        every { devicesRepository.getDeviceById(deviceId) } returns DeviceNotFound(deviceId).left()
        val sut = GetAcStateService(devicesRepository, deviceFactories = emptySet())

        // When
        val result = sut.execute(deviceId).shouldBeLeft()

        // Then
        result.shouldBeInstanceOf<DeviceNotFound>()
    }

    @Test
    fun `execute() returns DeviceRepositoryError when the device lookup hits a database error`() {
        // Given
        val deviceId = UUID.randomUUID()
        every { devicesRepository.getDeviceById(deviceId) } returns DeviceRepositoryError.left()
        val sut = GetAcStateService(devicesRepository, deviceFactories = emptySet())

        // When
        val result = sut.execute(deviceId).shouldBeLeft()

        // Then
        result.shouldBeInstanceOf<DeviceRepositoryError>()
    }

    @Test
    fun `execute() returns NotAnAirConditioner when no factory matches the device provider`() {
        // Given
        val deviceId = UUID.randomUUID()
        val device = aDevice(uuid = deviceId, provider = Provider.HON)
        val switchBotFactory = mockk<ProviderDevicesFactory> {
            every { provider } returns Provider.SWITCHBOT
        }
        every { devicesRepository.getDeviceById(deviceId) } returns device.right()
        val sut = GetAcStateService(devicesRepository, setOf(switchBotFactory))

        // When
        val result = sut.execute(deviceId).shouldBeLeft()

        // Then
        result shouldBe NotAnAirConditioner
    }

    @Test
    fun `execute() returns NotAnAirConditioner when the built driver is not an air conditioner`() {
        // Given
        val deviceId = UUID.randomUUID()
        val device = aDevice(uuid = deviceId, provider = Provider.HON)
        val nonAcDriver = mockk<DeviceDriver>()
        val factory = mockk<ProviderDevicesFactory> {
            every { provider } returns Provider.HON
            every { createDevice(device) } returns nonAcDriver
        }
        every { devicesRepository.getDeviceById(deviceId) } returns device.right()
        val sut = GetAcStateService(devicesRepository, setOf(factory))

        // When
        val result = sut.execute(deviceId).shouldBeLeft()

        // Then
        result shouldBe NotAnAirConditioner
    }

    @Test
    fun `execute() returns AcProviderFailure surfacing the driver failure when the state read fails`() {
        // Given
        val deviceId = UUID.randomUUID()
        val device = aDevice(uuid = deviceId, provider = Provider.HON)
        val driver = mockk<AirConditioner> { every { getState() } returns BoomFailure.left() }
        val factory = mockk<ProviderDevicesFactory> {
            every { provider } returns Provider.HON
            every { createDevice(device) } returns driver
        }
        every { devicesRepository.getDeviceById(deviceId) } returns device.right()
        val sut = GetAcStateService(devicesRepository, setOf(factory))

        // When
        val result = sut.execute(deviceId).shouldBeLeft()

        // Then
        val failure = result.shouldBeInstanceOf<AcProviderFailure>()
        withClue("the driver's human-readable cause should surface, never a debug toString") {
            failure.message shouldBe "provider exploded"
        }
    }

    companion object {
        /** A driver failure carrying its human-readable cause, as an adapter would produce it. */
        private data object BoomFailure : ActuatorOperationFailure {
            override val message: String = "provider exploded"
        }
    }
}
