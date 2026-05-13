package org.agrfesta.sh.api.core.domain.devices

data class ProviderDeviceData(
    override val deviceProviderId: String,
    override val provider: Provider,
    val name: String,
    val features: Set<DeviceFeature>
): DeviceProviderIdentity
