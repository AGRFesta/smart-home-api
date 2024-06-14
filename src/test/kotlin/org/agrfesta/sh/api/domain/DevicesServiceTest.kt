package org.agrfesta.sh.api.domain

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
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
        val deviceA = aProviderDevice()
        val deviceB = aProviderDevice()
        val deviceC = aProviderDevice()

        val result = sut.refresh(
            providersDevices = listOf(deviceA, deviceB, deviceC),
            devices = emptyList()
        )

        result.newDevices.map { listOf(it.providerId, it.name, it.provider) }
            .shouldContainExactlyInAnyOrder(
                listOf(deviceB.id, deviceB.name, deviceB.provider),
                listOf(deviceA.id, deviceA.name, deviceA.provider),
                listOf(deviceC.id, deviceC.name, deviceC.provider)
            )
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
        result.detachedDevices.shouldContainExactlyInAnyOrder(deviceC, deviceA, deviceB)
    }

    @Test
    fun `refresh() returns updated devices only providers devices are exactly the same devices`() {
        val providerDeviceA = aProviderDevice()
        val providerDeviceB = aProviderDevice()
        val providerDeviceC = aProviderDevice()
        val deviceA = aDevice(providerId = providerDeviceA.id, provider = providerDeviceA.provider)
        val deviceB = aDevice(providerId = providerDeviceB.id, provider = providerDeviceB.provider)
        val deviceC = aDevice(providerId = providerDeviceC.id, provider = providerDeviceC.provider)

        val result = sut.refresh(
            providersDevices = listOf(providerDeviceA, providerDeviceB, providerDeviceC),
            devices = listOf(deviceA, deviceB, deviceC)
        )

        result.newDevices.shouldBeEmpty()
        result.updatedDevices.map { listOf(it.uuid, it.providerId, it.provider, it.name) }
            .shouldContainExactlyInAnyOrder(
                listOf(deviceB.uuid, deviceB.providerId, deviceB.provider, providerDeviceB.name),
                listOf(deviceA.uuid, deviceA.providerId, deviceA.provider, providerDeviceA.name),
                listOf(deviceC.uuid, deviceC.providerId, deviceC.provider, providerDeviceC.name)
            )
        result.detachedDevices.shouldBeEmpty()
    }

}