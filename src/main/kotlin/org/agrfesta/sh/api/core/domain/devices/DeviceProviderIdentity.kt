package org.agrfesta.sh.api.core.domain.devices

interface DeviceProviderIdentity {
    val deviceProviderId: String
    val provider: Provider
}
