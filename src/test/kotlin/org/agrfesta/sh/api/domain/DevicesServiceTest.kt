package org.agrfesta.sh.api.domain

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import org.agrfesta.sh.api.core.domain.devices.DeviceDriver
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.ProviderDevicesFactory
import org.agrfesta.sh.api.core.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicesRepository
import org.agrfesta.sh.api.services.DevicesService
import org.junit.jupiter.api.Test

class DevicesServiceTest {
    private val devicesRepository: DevicesRepository = mockk()

    private val sut: DevicesService = DevicesService(devicesRepository, listOf())

    // getAllDevices()

    @Test
    fun `getAllDevices() returns domain devices built by the provider factory`() {
        val dto = aDevice(provider = Provider.SWITCHBOT)
        val domainDevice: DeviceDriver = mockk()
        val factory: ProviderDevicesFactory = mockk()
        every { factory.provider } returns Provider.SWITCHBOT
        every { factory.createDevice(dto) } returns domainDevice
        every { devicesRepository.getAll() } returns listOf(dto).right()
        val sut = DevicesService(devicesRepository, listOf(factory))

        val result = sut.getAllDevices().shouldBeRight()

        result.shouldContainExactlyInAnyOrder(domainDevice)
    }

    @Test
    fun `getAllDevices() propagates PersistenceFailure when repository fails`() {
        every { devicesRepository.getAll() } returns PersistenceFailure(Exception("db error")).left()

        sut.getAllDevices().shouldBeLeft().shouldBeInstanceOf<PersistenceFailure>()
    }

    @Test
    fun `getAllDevices() skips device when no factory is registered for its provider`() {
        val dto = aDevice(provider = Provider.SWITCHBOT)
        every { devicesRepository.getAll() } returns listOf(dto).right()

        sut.getAllDevices().shouldBeRight().shouldBeEmpty()
    }

    @Test
    fun `getAllDevices() skips only devices with unregistered provider`() {
        val dtoWithFactory = aDevice(provider = Provider.SWITCHBOT)
        val dtoWithoutFactory = aDevice(provider = Provider.NETATMO)
        val domainDevice: DeviceDriver = mockk()
        val factory: ProviderDevicesFactory = mockk()
        every { factory.provider } returns Provider.SWITCHBOT
        every { factory.createDevice(dtoWithFactory) } returns domainDevice
        every { devicesRepository.getAll() } returns listOf(dtoWithFactory, dtoWithoutFactory).right()
        val sut = DevicesService(devicesRepository, listOf(factory))

        sut.getAllDevices().shouldBeRight().shouldContainExactlyInAnyOrder(domainDevice)
    }

}
