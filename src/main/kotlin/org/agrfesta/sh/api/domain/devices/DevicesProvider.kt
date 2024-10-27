package org.agrfesta.sh.api.domain.devices

import arrow.core.Either

interface DevicesProvider {
    val provider: Provider
    fun getAllDevices(): Either<ProviderFailure, Collection<DeviceDataValue>>
}

data class ProviderFailure(val exception: Exception)
