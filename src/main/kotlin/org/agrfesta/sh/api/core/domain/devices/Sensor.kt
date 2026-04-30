package org.agrfesta.sh.api.core.domain.devices

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.failures.Failure

interface Sensor: DeviceDriver {
    suspend fun fetchReadings(): Either<Failure, SensorReadings>
}
