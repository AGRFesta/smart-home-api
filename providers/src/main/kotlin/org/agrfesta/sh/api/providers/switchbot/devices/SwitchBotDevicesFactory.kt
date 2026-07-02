package org.agrfesta.sh.api.providers.switchbot.devices

import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DeviceDriver
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.ProviderDevicesFactory
import org.agrfesta.sh.api.core.domain.devices.Device
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.providers.switchbot.ConditionalOnSwitchBot
import org.agrfesta.sh.api.providers.switchbot.SwitchBotDeviceType
import org.agrfesta.sh.api.providers.switchbot.SwitchBotDevicesClient
import org.springframework.stereotype.Service

@Service
@ConditionalOnSwitchBot
class SwitchBotDevicesFactory(
    private val client: SwitchBotDevicesClient
) : ProviderDevicesFactory {
    override val provider = Provider.SWITCHBOT

    override fun createDevice(record: Device): DeviceDriver =
        if (record.model?.value == SwitchBotDeviceType.HUB_MINI.model) {
            SwitchBotMiniHub(record.uuid, record.deviceProviderId)
        } else {
            SwitchBotMeter(record.uuid, record.provider, record.deviceProviderId, client)
        }
}
