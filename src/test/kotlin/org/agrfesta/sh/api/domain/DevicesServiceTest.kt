package org.agrfesta.sh.api.domain

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import org.agrfesta.sh.api.core.domain.devices.DeviceDriver
import org.agrfesta.sh.api.core.domain.devices.DeviceStatus
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.ProviderDevicesFactory
import org.agrfesta.sh.api.core.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicesRepository
import org.agrfesta.sh.api.services.DevicesService
import org.agrfesta.sh.api.utils.RandomGenerator
import org.junit.jupiter.api.Test

class DevicesServiceTest {
    private val devicesFactories: Collection<ProviderDevicesFactory> = listOf()

    private val devicesRepository: DevicesRepository = mockk()
    private val randomGenerator: RandomGenerator = mockk()

    private val sut: DevicesService = DevicesService(devicesRepository, randomGenerator, devicesFactories)

    @Test
    fun `refresh() returns empty result when there are no devices and no provider devices`() {
        val result = sut.refresh(
            providersDevices = emptyList(),
            devices = emptyList()
        )

        result.newDevices.shouldBeEmpty()
        result.updatedDevices.shouldBeEmpty()
        result.detachedDevices.shouldBeEmpty()
    }

    @Test
    fun `refresh() returns new devices only when there are no devices`() {
        val deviceA = aProviderDeviceData()
        val deviceB = aProviderDeviceData()
        val deviceC = aProviderDeviceData()

        val result = sut.refresh(
            providersDevices = listOf(deviceA, deviceB, deviceC),
            devices = emptyList()
        )

        result.newDevices.shouldContainExactlyInAnyOrder(deviceB, deviceA, deviceC)
        result.updatedDevices.shouldBeEmpty()
        result.detachedDevices.shouldBeEmpty()
    }

    @Test
    fun `refresh() returns detached devices only when there are no providers devices`() {
        val deviceA = aDevice()
        val deviceB = aDevice()
        val deviceC = aDevice()

        val result = sut.refresh(
            providersDevices = emptyList(),
            devices = listOf(deviceA, deviceB, deviceC)
        )

        result.newDevices.shouldBeEmpty()
        result.updatedDevices.shouldBeEmpty()
        result.detachedDevices.shouldContainExactlyInAnyOrder(
            deviceC.copy(status = DeviceStatus.DETACHED),
            deviceA.copy(status = DeviceStatus.DETACHED),
            deviceB.copy(status = DeviceStatus.DETACHED))
    }

    @Test
    fun `refresh() returns updated devices only providers devices are exactly the same devices`() {
        val providerDeviceA = aProviderDeviceData()
        val providerDeviceB = aProviderDeviceData()
        val providerDeviceC = aProviderDeviceData()
        val deviceA = aDevice(providerId = providerDeviceA.deviceProviderId, provider = providerDeviceA.provider)
        val deviceB = aDevice(providerId = providerDeviceB.deviceProviderId, provider = providerDeviceB.provider)
        val deviceC = aDevice(providerId = providerDeviceC.deviceProviderId, provider = providerDeviceC.provider)

        val result = sut.refresh(
            providersDevices = listOf(providerDeviceA, providerDeviceB, providerDeviceC),
            devices = listOf(deviceA, deviceB, deviceC)
        )

        result.newDevices.shouldBeEmpty()
        result.updatedDevices.map { listOf(it.deviceProviderId, it.provider, it.name) }
            .shouldContainExactlyInAnyOrder(
                listOf(deviceB.deviceProviderId, deviceB.provider, providerDeviceB.name),
                listOf(deviceA.deviceProviderId, deviceA.provider, providerDeviceA.name),
                listOf(deviceC.deviceProviderId, deviceC.provider, providerDeviceC.name)
            )
        result.detachedDevices.shouldBeEmpty()
    }

    @Test
    fun `refresh() correctly distributes devices across new, updated, and detached sets`() {
        val providerDeviceA = aProviderDeviceData()
        val providerDeviceB = aProviderDeviceData()
        val deviceA = aDevice(providerId = providerDeviceA.deviceProviderId, provider = providerDeviceA.provider)
        val deviceC = aDevice()

        val result = sut.refresh(
            providersDevices = listOf(providerDeviceA, providerDeviceB),
            devices = listOf(deviceA, deviceC)
        )

        result.newDevices.shouldContainExactlyInAnyOrder(providerDeviceB)
        result.updatedDevices.map { it.deviceProviderId }
            .shouldContainExactlyInAnyOrder(deviceA.deviceProviderId)
        result.detachedDevices.map { it.deviceProviderId }
            .shouldContainExactlyInAnyOrder(deviceC.deviceProviderId)
    }

    @Test
    fun `refresh() returns updated detached devices as paired when provider returns them`() {
        val providerDeviceA = aProviderDeviceData()
        val providerDeviceB = aProviderDeviceData()
        val providerDeviceC = aProviderDeviceData()
        val deviceA = aDevice(
            providerId = providerDeviceA.deviceProviderId,
            provider = providerDeviceA.provider,
            status = DeviceStatus.DETACHED)
        val deviceB = aDevice(
            providerId = providerDeviceB.deviceProviderId,
            provider = providerDeviceB.provider,
            status = DeviceStatus.DETACHED)
        val deviceC = aDevice(
            providerId = providerDeviceC.deviceProviderId,
            provider = providerDeviceC.provider,
            status = DeviceStatus.DETACHED)

        val result = sut.refresh(
            providersDevices = listOf(providerDeviceA, providerDeviceB, providerDeviceC),
            devices = listOf(deviceA, deviceB, deviceC)
        )

        result.newDevices.shouldBeEmpty()
        result.updatedDevices.map { listOf(it.deviceProviderId, it.provider, it.name, it.status) }
            .shouldContainExactlyInAnyOrder(
                listOf(deviceB.deviceProviderId, deviceB.provider, providerDeviceB.name, DeviceStatus.PAIRED),
                listOf(deviceA.deviceProviderId, deviceA.provider, providerDeviceA.name, DeviceStatus.PAIRED),
                listOf(deviceC.deviceProviderId, deviceC.provider, providerDeviceC.name, DeviceStatus.PAIRED)
            )
        result.detachedDevices.shouldBeEmpty()
    }

    // createDevice()

    @Test
    fun `createDevice() returns UUID on success`() {
        val device = aProviderDeviceData()
        val uuid = UUID.randomUUID()
        every { randomGenerator.uuid() } returns uuid
        every { devicesRepository.create(uuid, device, any()) } returns Unit.right()

        sut.createDevice(device).shouldBeRight() shouldBe uuid
    }

    @Test
    fun `createDevice() returns failure when repository fails`() {
        val device = aProviderDeviceData()
        every { randomGenerator.uuid() } returns UUID.randomUUID()
        every {
            devicesRepository.create(any(), device, any())
        }returns PersistenceFailure(Exception("db error")).left()

        sut.createDevice(device).shouldBeLeft().shouldBeInstanceOf<PersistenceFailure>()
    }

    @Test
    fun `createDevice() uses PAIRED as default initial status`() {
        val device = aProviderDeviceData()
        every { randomGenerator.uuid() } returns UUID.randomUUID()
        every { devicesRepository.create(any(), device, any()) } returns Unit.right()

        sut.createDevice(device)

        verify { devicesRepository.create(any(), device, DeviceStatus.PAIRED) }
    }

    // getAllDevices()

    @Test
    fun `getAllDevices() returns domain devices built by the provider factory`() {
        val dto = aDevice(provider = Provider.SWITCHBOT)
        val domainDevice: DeviceDriver = mockk()
        val factory: ProviderDevicesFactory = mockk()
        every { factory.provider } returns Provider.SWITCHBOT
        every { factory.createDevice(dto) } returns domainDevice
        every { devicesRepository.getAll() } returns listOf(dto).right()
        val sut = DevicesService(devicesRepository, randomGenerator, listOf(factory))

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
        val sut = DevicesService(devicesRepository, randomGenerator, listOf(factory))

        sut.getAllDevices().shouldBeRight().shouldContainExactlyInAnyOrder(domainDevice)
    }

}