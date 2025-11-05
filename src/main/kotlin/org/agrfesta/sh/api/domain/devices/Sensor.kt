package org.agrfesta.sh.api.domain.devices

import arrow.core.Either
import org.agrfesta.sh.api.domain.failures.Failure

interface Sensor: Device {
    suspend fun fetchReadings(): Either<Failure, SensorReadings>
}
