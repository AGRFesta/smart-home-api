package org.agrfesta.sh.api.core.domain.devices

import java.util.*

data class Device(
    val uuid: UUID,
    val status: DeviceStatus,
    override val deviceProviderId: String,
    override val provider: Provider,
    val name: String,
    val features: Set<DeviceFeature>,
    val model: DeviceModel? = null
) : DeviceProviderIdentity {
    constructor(uuid: UUID, providerData: ProviderDeviceData, status: DeviceStatus = DeviceStatus.PAIRED) : this(
        uuid = uuid,
        status = status,
        deviceProviderId = providerData.deviceProviderId,
        provider = providerData.provider,
        name = providerData.name,
        features = providerData.features,
        model = providerData.model
    )

    fun isSensor(): Boolean = features.contains(DeviceFeature.SENSOR)

    fun isActuator(): Boolean = features.contains(DeviceFeature.ACTUATOR)
}
