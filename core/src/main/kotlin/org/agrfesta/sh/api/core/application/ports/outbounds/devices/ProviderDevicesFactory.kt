package org.agrfesta.sh.api.core.application.ports.outbounds.devices

import org.agrfesta.sh.api.core.domain.devices.DeviceDriver
import org.agrfesta.sh.api.core.domain.devices.Device
import org.agrfesta.sh.api.core.domain.devices.Provider

interface ProviderDevicesFactory {
    val provider: Provider
    fun createDevice(record: Device): DeviceDriver
}
