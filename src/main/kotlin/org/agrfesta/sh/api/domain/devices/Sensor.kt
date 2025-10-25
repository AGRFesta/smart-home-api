package org.agrfesta.sh.api.domain.devices

import arrow.core.Either

interface Sensor: Device {
    suspend fun fetchReadings(): Either<SensorReadingsFailure, SensorReadings>
}
