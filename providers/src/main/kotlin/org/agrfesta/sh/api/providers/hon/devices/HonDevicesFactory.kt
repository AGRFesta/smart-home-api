package org.agrfesta.sh.api.providers.hon.devices

import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DeviceDriver
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.ProviderDevicesFactory
import org.agrfesta.sh.api.core.domain.devices.Device
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.providers.hon.ConditionalOnHon
import org.agrfesta.sh.api.providers.hon.HonApplianceStore
import org.agrfesta.sh.api.providers.hon.HonService
import org.springframework.stereotype.Service

@Service
@ConditionalOnHon
class HonDevicesFactory(
    private val appliances: HonApplianceStore
) : ProviderDevicesFactory {
    override val provider = Provider.HON

    override fun createDevice(record: Device): DeviceDriver =
        when (record.model.value) {
            HonService.AC_AS25PBPHRA_PRE_MODEL, HonService.AC_AS35PBPHRA_PRE_MODEL ->
                HonAc(record.uuid, record.deviceProviderId, appliances)
            else -> HonUnknownDevice(record.uuid, record.deviceProviderId)
        }
}
