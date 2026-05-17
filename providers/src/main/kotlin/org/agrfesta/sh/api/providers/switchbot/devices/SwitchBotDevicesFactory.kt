package org.agrfesta.sh.api.providers.switchbot.devices

import org.agrfesta.sh.api.core.application.ports.outbounds.devices.ProviderDevicesFactory
import org.agrfesta.sh.api.core.domain.devices.Device
import org.agrfesta.sh.api.core.domain.devices.DeviceDriver
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.providers.switchbot.SwitchBotDevicesClient
import org.springframework.stereotype.Service

@Service
class SwitchBotDevicesFactory(
    private val client: SwitchBotDevicesClient
) : ProviderDevicesFactory {
    override val provider = Provider.SWITCHBOT

    override fun createDevice(dto: Device): DeviceDriver {
        return if (dto.features.isEmpty()) {
            SwitchBotMiniHub(dto.uuid, dto.deviceProviderId)
        } else {
            SwitchBotMeter(dto.uuid, dto.provider, dto.deviceProviderId, client)
        }
    }
}
