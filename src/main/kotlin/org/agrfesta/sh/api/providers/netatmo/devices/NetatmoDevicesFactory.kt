package org.agrfesta.sh.api.providers.netatmo.devices

import org.agrfesta.sh.api.domain.devices.Device
import org.agrfesta.sh.api.domain.devices.DeviceDto
import org.agrfesta.sh.api.domain.devices.Provider
import org.agrfesta.sh.api.domain.devices.ProviderDevicesFactory
import org.springframework.stereotype.Service

@Service
class NetatmoDevicesFactory: ProviderDevicesFactory {
    override val provider = Provider.NETATMO

    override fun createDevice(dto: DeviceDto): Device {
        TODO("Not yet implemented")
    }

}
