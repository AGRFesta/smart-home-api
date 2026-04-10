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
import org.agrfesta.sh.api.domain.devices.Device
import org.agrfesta.sh.api.domain.devices.DeviceStatus
import org.agrfesta.sh.api.domain.devices.Provider
import org.agrfesta.sh.api.domain.devices.ProviderDevicesFactory
import org.agrfesta.sh.api.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.persistence.DevicesDao
import org.agrfesta.sh.api.services.DevicesService
import org.junit.jupiter.api.Test

class DevicesServiceTest {
    private val devicesFactories: Collection<ProviderDevicesFactory> = listOf()

    private val devicesDao: DevicesDao = mockk()

    private val sut: DevicesService = DevicesService(devicesDao, devicesFactories)

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
        val deviceA = aDeviceDataValue()
        val deviceB = aDeviceDataValue()
        val deviceC = aDeviceDataValue()

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
        val providerDeviceA = aDeviceDataValue()
        val providerDeviceB = aDeviceDataValue()
        val providerDeviceC = aDeviceDataValue()
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
        val providerDeviceA = aDeviceDataValue()
        val providerDeviceB = aDeviceDataValue()
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
        val providerDeviceA = aDeviceDataValue()
        val providerDeviceB = aDeviceDataValue()
        val providerDeviceC = aDeviceDataValue()
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
        val device = aDeviceDataValue()
        val uuid = UUID.randomUUID()
        every { devicesDao.create(device, any()) } returns uuid.right()

        sut.createDevice(device).shouldBeRight() shouldBe uuid
    }

    @Test
    fun `createDevice() returns failure when dao fails`() {
        val device = aDeviceDataValue()
        every { devicesDao.create(device, any()) } returns PersistenceFailure(Exception("db error")).left()

        sut.createDevice(device).shouldBeLeft().shouldBeInstanceOf<PersistenceFailure>()
    }

    @Test
    fun `createDevice() uses PAIRED as default initial status`() {
        val device = aDeviceDataValue()
        every { devicesDao.create(device, any()) } returns UUID.randomUUID().right()

        sut.createDevice(device)

        verify { devicesDao.create(device, DeviceStatus.PAIRED) }
    }

    // getAllDevices()

    @Test
    fun `getAllDevices() returns domain devices built by the provider factory`() {
        val dto = aDevice(provider = Provider.SWITCHBOT)
        val domainDevice: Device = mockk()
        val factory: ProviderDevicesFactory = mockk()
        every { factory.provider } returns Provider.SWITCHBOT
        every { factory.createDevice(dto) } returns domainDevice
        every { devicesDao.getAll() } returns listOf(dto).right()
        val sut = DevicesService(devicesDao, listOf(factory))

        val result = sut.getAllDevices().shouldBeRight()

        result.shouldContainExactlyInAnyOrder(domainDevice)
    }

    @Test
    fun `getAllDevices() propagates PersistenceFailure when dao fails`() {
        every { devicesDao.getAll() } returns PersistenceFailure(Exception("db error")).left()

        sut.getAllDevices().shouldBeLeft().shouldBeInstanceOf<PersistenceFailure>()
    }

    @Test
    fun `getAllDevices() skips device when no factory is registered for its provider`() {
        val dto = aDevice(provider = Provider.SWITCHBOT)
        every { devicesDao.getAll() } returns listOf(dto).right()

        sut.getAllDevices().shouldBeRight().shouldBeEmpty()
    }

    @Test
    fun `getAllDevices() skips only devices with unregistered provider`() {
        val dtoWithFactory = aDevice(provider = Provider.SWITCHBOT)
        val dtoWithoutFactory = aDevice(provider = Provider.NETATMO)
        val domainDevice: Device = mockk()
        val factory: ProviderDevicesFactory = mockk()
        every { factory.provider } returns Provider.SWITCHBOT
        every { factory.createDevice(dtoWithFactory) } returns domainDevice
        every { devicesDao.getAll() } returns listOf(dtoWithFactory, dtoWithoutFactory).right()
        val sut = DevicesService(devicesDao, listOf(factory))

        sut.getAllDevices().shouldBeRight().shouldContainExactlyInAnyOrder(domainDevice)
    }

}