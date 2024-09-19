package org.agrfesta.sh.api.domain.devices

data class Device (
    val providerId: String,
    val provider: Provider,
    val name: String,
    val status: DeviceStatus,
    val features: Set<DeviceFeature>
)
