package org.agrfesta.sh.api.providers.netatmo.devices

import org.agrfesta.sh.api.domain.devices.Device
import org.agrfesta.sh.api.domain.devices.DeviceDto
import org.agrfesta.sh.api.domain.devices.Provider
import org.agrfesta.sh.api.domain.devices.ProviderDevicesFactory
import org.agrfesta.sh.api.providers.netatmo.NetatmoClient
import org.agrfesta.sh.api.providers.netatmo.NetatmoConfiguration
import org.agrfesta.sh.api.utils.TimeService
import org.springframework.stereotype.Service

@Service
class NetatmoDevicesFactory(
    private val config: NetatmoConfiguration,
    private val client: NetatmoClient,
    private val timeService: TimeService
): ProviderDevicesFactory {
    override val provider = Provider.NETATMO

    override fun createDevice(dto: DeviceDto): Device =
        NetatmoSmarther(
            uuid = dto.uuid,
            deviceProviderId = dto.deviceProviderId,
            homeId = config.homeId,
            roomId = config.roomId,
            client = client,
            timeService = timeService
        )

}
