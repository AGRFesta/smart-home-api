package org.agrfesta.sh.api.domain.devices

interface ProviderDevicesFactory {
    val provider: Provider
    fun createDevice(dto: DeviceDto): Device
}
