package org.agrfesta.sh.api.providers.switchbot.devices

import org.agrfesta.sh.api.domain.devices.DeviceDto
import org.agrfesta.sh.api.domain.devices.Device
import org.agrfesta.sh.api.domain.devices.Provider
import org.agrfesta.sh.api.domain.devices.ProviderDevicesFactory
import org.agrfesta.sh.api.providers.switchbot.SwitchBotDevicesClient
import org.springframework.stereotype.Service

@Service
class SwitchBotDevicesFactory(
    private val client: SwitchBotDevicesClient
): ProviderDevicesFactory {
    override val provider = Provider.SWITCHBOT

    override fun createDevice(dto: DeviceDto): Device {
        return if (dto.features.isEmpty()) {
            SwitchBotMiniHub(dto.uuid, dto.deviceProviderId)
        } else {
            SwitchBotMeter(dto.uuid, dto.provider, dto.deviceProviderId, client)
        }
    }

}
