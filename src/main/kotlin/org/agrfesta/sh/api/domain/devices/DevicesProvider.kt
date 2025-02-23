package org.agrfesta.sh.api.domain.devices

import arrow.core.Either
import org.agrfesta.sh.api.domain.failures.Failure

interface DevicesProvider {
    val provider: Provider
    fun getAllDevices(): Either<Failure, Collection<DeviceDataValue>>
}
