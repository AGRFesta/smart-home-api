package org.agrfesta.sh.api.core.application.ports.outbounds.devices

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.core.domain.devices.ProviderDeviceData
import org.agrfesta.sh.api.core.domain.failures.Failure

interface DevicesProvider {
    val provider: Provider
    fun getAllDevices(): Either<Failure, Collection<ProviderDeviceData>>
}
