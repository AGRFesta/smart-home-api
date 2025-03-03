package org.agrfesta.sh.api.domain.devices

import com.fasterxml.jackson.annotation.JsonIgnore
import java.util.*
import org.agrfesta.sh.api.domain.devices.DeviceFeature.ACTUATOR
import org.agrfesta.sh.api.domain.devices.DeviceFeature.SENSOR

interface DeviceProviderIdentity {
    val providerId: String
    val provider: Provider
}

data class Device (
    val uuid: UUID,
    val status: DeviceStatus,
    override val providerId: String,
    override val provider: Provider,
    val name: String,
    val features: Set<DeviceFeature>
): DeviceProviderIdentity {
    constructor(uuid: UUID,  dataValue: DeviceDataValue, status: DeviceStatus = DeviceStatus.PAIRED) : this(
        uuid = uuid,
        status = status,
        providerId = dataValue.providerId,
        provider = dataValue.provider,
        name = dataValue.name,
        features = dataValue.features
    )

    fun asDataValue() = DeviceDataValue(providerId, provider, name, features)

    @JsonIgnore
    fun isSensor(): Boolean = features.contains(SENSOR)

    @JsonIgnore
    fun isActuator(): Boolean = features.contains(ACTUATOR)
}

data class DeviceDataValue(
    override val providerId: String,
    override val provider: Provider,
    val name: String,
    val features: Set<DeviceFeature>
): DeviceProviderIdentity
