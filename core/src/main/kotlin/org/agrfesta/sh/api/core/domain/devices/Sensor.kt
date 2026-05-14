package org.agrfesta.sh.api.core.domain.devices

import arrow.core.Either

interface Sensor: DeviceDriver {
    fun fetchReadings(): Either<SensorReadingsFailure, SensorReadings>
}
