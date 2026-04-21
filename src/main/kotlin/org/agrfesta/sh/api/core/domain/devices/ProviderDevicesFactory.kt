package org.agrfesta.sh.api.core.domain.devices

interface ProviderDevicesFactory {
    val provider: Provider
    fun createDevice(dto: DeviceDto): Device
}
