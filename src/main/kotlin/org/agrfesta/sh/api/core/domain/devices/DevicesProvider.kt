package org.agrfesta.sh.api.core.domain.devices

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.failures.Failure

interface DevicesProvider {
    val provider: Provider
    fun getAllDevices(): Either<Failure, Collection<DeviceDataValue>>
}
