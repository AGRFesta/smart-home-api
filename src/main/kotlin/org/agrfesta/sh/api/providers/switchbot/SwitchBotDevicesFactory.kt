package org.agrfesta.sh.api.providers.switchbot

import org.agrfesta.sh.api.domain.devices.DeviceDto
import org.agrfesta.sh.api.domain.devices.Device
import org.agrfesta.sh.api.domain.devices.Provider
import org.agrfesta.sh.api.domain.devices.ProviderDevicesFactory
import org.springframework.stereotype.Service

@Service
class SwitchBotDevicesFactory(
    private val client: SwitchBotDevicesClient
): ProviderDevicesFactory {
    override val provider = Provider.SWITCHBOT

    override fun createDevice(dto: DeviceDto): Device {
        return SwitchBotMeter(dto.uuid, dto.provider, dto.deviceProviderId, client)
    }

}
