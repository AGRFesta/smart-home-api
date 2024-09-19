package org.agrfesta.sh.api.domain

import org.agrfesta.sh.api.domain.devices.Device
import org.agrfesta.sh.api.domain.devices.DeviceFeature
import org.agrfesta.sh.api.domain.devices.DeviceStatus
import org.agrfesta.sh.api.domain.devices.Provider
import java.util.*

fun aDevice(
    providerId: String = UUID.randomUUID().toString(),
    provider: Provider = Provider.SWITCHBOT,
    status: DeviceStatus = DeviceStatus.PAIRED,
    name: String = UUID.randomUUID().toString(),
    features: Set<DeviceFeature> = emptySet()
) = Device(providerId, provider, name, status, features)
