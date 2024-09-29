package org.agrfesta.sh.api.domain

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import org.agrfesta.sh.api.domain.devices.DeviceStatus
import org.agrfesta.sh.api.domain.devices.DevicesService
import org.junit.jupiter.api.Test

class DevicesServiceTest {
    private val sut: DevicesService = DevicesService()

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
        val deviceA = aDevice(providerId = providerDeviceA.providerId, provider = providerDeviceA.provider)
        val deviceB = aDevice(providerId = providerDeviceB.providerId, provider = providerDeviceB.provider)
        val deviceC = aDevice(providerId = providerDeviceC.providerId, provider = providerDeviceC.provider)

        val result = sut.refresh(
            providersDevices = listOf(providerDeviceA, providerDeviceB, providerDeviceC),
            devices = listOf(deviceA, deviceB, deviceC)
        )

        result.newDevices.shouldBeEmpty()
        result.updatedDevices.map { listOf(it.providerId, it.provider, it.name) }
            .shouldContainExactlyInAnyOrder(
                listOf(deviceB.providerId, deviceB.provider, providerDeviceB.name),
                listOf(deviceA.providerId, deviceA.provider, providerDeviceA.name),
                listOf(deviceC.providerId, deviceC.provider, providerDeviceC.name)
            )
        result.detachedDevices.shouldBeEmpty()
    }

    @Test
    fun `refresh() returns updated detached devices as paired when provider returns them`() {
        val providerDeviceA = aDeviceDataValue()
        val providerDeviceB = aDeviceDataValue()
        val providerDeviceC = aDeviceDataValue()
        val deviceA = aDevice(
            providerId = providerDeviceA.providerId,
            provider = providerDeviceA.provider,
            status = DeviceStatus.DETACHED)
        val deviceB = aDevice(
            providerId = providerDeviceB.providerId,
            provider = providerDeviceB.provider,
            status = DeviceStatus.DETACHED)
        val deviceC = aDevice(
            providerId = providerDeviceC.providerId,
            provider = providerDeviceC.provider,
            status = DeviceStatus.DETACHED)

        val result = sut.refresh(
            providersDevices = listOf(providerDeviceA, providerDeviceB, providerDeviceC),
            devices = listOf(deviceA, deviceB, deviceC)
        )

        result.newDevices.shouldBeEmpty()
        result.updatedDevices.map { listOf(it.providerId, it.provider, it.name, it.status) }
            .shouldContainExactlyInAnyOrder(
                listOf(deviceB.providerId, deviceB.provider, providerDeviceB.name, DeviceStatus.PAIRED),
                listOf(deviceA.providerId, deviceA.provider, providerDeviceA.name, DeviceStatus.PAIRED),
                listOf(deviceC.providerId, deviceC.provider, providerDeviceC.name, DeviceStatus.PAIRED)
            )
        result.detachedDevices.shouldBeEmpty()
    }

}