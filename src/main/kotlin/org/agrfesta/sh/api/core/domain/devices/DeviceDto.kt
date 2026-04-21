package org.agrfesta.sh.api.core.domain.devices

import com.fasterxml.jackson.annotation.JsonIgnore
import java.util.*

interface DeviceProviderIdentity {
    val deviceProviderId: String
    val provider: Provider
}

data class DeviceDto (
    val uuid: UUID,
    val status: DeviceStatus,
    override val deviceProviderId: String,
    override val provider: Provider,
    val name: String,
    val features: Set<DeviceFeature>
): DeviceProviderIdentity {
    constructor(uuid: UUID,  dataValue: DeviceDataValue, status: DeviceStatus = DeviceStatus.PAIRED) : this(
        uuid = uuid,
        status = status,
        deviceProviderId = dataValue.deviceProviderId,
        provider = dataValue.provider,
        name = dataValue.name,
        features = dataValue.features
    )

    fun asDataValue() = DeviceDataValue(deviceProviderId, provider, name, features)

    @JsonIgnore
    fun isSensor(): Boolean = features.contains(DeviceFeature.SENSOR)

    @JsonIgnore
    fun isActuator(): Boolean = features.contains(DeviceFeature.ACTUATOR)
}

data class DeviceDataValue(
    override val deviceProviderId: String,
    override val provider: Provider,
    val name: String,
    val features: Set<DeviceFeature>
): DeviceProviderIdentity
