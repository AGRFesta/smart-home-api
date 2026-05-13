package org.agrfesta.sh.api.providers.netatmo.devices

import org.agrfesta.sh.api.core.domain.devices.DeviceDriver
import org.agrfesta.sh.api.core.domain.devices.Device
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.ProviderDevicesFactory
import org.agrfesta.sh.api.providers.netatmo.NetatmoClient
import org.agrfesta.sh.api.providers.netatmo.NetatmoConfiguration
import org.agrfesta.sh.api.core.application.ports.outbounds.TimeProvider
import org.springframework.stereotype.Service

@Service
class NetatmoDevicesFactory(
    private val config: NetatmoConfiguration,
    private val client: NetatmoClient,
    private val timeProvider: TimeProvider
): ProviderDevicesFactory {
    override val provider = Provider.NETATMO

    override fun createDevice(dto: Device): DeviceDriver =
        NetatmoSmarther(
            uuid = dto.uuid,
            deviceProviderId = dto.deviceProviderId,
            homeId = config.homeId,
            roomId = config.roomId,
            client = client,
            timeProvider = timeProvider
        )

}
