package org.agrfesta.sh.api.domain.devices

import java.util.UUID

data class Device (
    val uuid: UUID,
    val status: DeviceStatus,
    val providerId: String,
    val provider: Provider,
    val name: String,
    val features: Set<DeviceFeature>
) {
    constructor(uuid: UUID,  dataValue: DeviceDataValue, status: DeviceStatus = DeviceStatus.PAIRED) : this(
        uuid = uuid,
        status = status,
        providerId = dataValue.providerId,
        provider = dataValue.provider,
        name = dataValue.name,
        features = dataValue.features
    )

    fun asDataValue() = DeviceDataValue(providerId, provider, name, features)
}

data class DeviceDataValue(
    val providerId: String,
    val provider: Provider,
    val name: String,
    val features: Set<DeviceFeature>
)
