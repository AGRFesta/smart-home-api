package org.agrfesta.sh.api.controllers

import java.util.UUID
import org.agrfesta.sh.api.core.domain.devices.Device
import org.agrfesta.sh.api.core.domain.devices.DeviceFeature
import org.agrfesta.sh.api.core.domain.devices.DeviceStatus
import org.agrfesta.sh.api.core.domain.devices.Provider

data class DeviceResponse(
    val uuid: UUID,
    val status: DeviceStatus,
    val deviceProviderId: String,
    val provider: Provider,
    val name: String,
    val features: Set<DeviceFeature>
)

fun Device.toResponse() = DeviceResponse(uuid, status, deviceProviderId, provider, name, features)
