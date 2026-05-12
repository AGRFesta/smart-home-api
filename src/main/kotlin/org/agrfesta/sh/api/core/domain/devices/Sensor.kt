package org.agrfesta.sh.api.core.domain.devices

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.failures.Failure

interface Sensor: DeviceDriver {
    fun fetchReadings(): Either<Failure, SensorReadings>
}
