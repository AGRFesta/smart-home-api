package org.agrfesta.sh.api.domain.devices

import com.fasterxml.jackson.annotation.JsonIgnore
import java.util.*
import org.agrfesta.sh.api.domain.devices.DeviceFeature.ACTUATOR
import org.agrfesta.sh.api.domain.devices.DeviceFeature.SENSOR

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
    fun isSensor(): Boolean = features.contains(SENSOR)

    @JsonIgnore
    fun isActuator(): Boolean = features.contains(ACTUATOR)
}

data class DeviceDataValue(
    override val deviceProviderId: String,
    override val provider: Provider,
    val name: String,
    val features: Set<DeviceFeature>
): DeviceProviderIdentity
