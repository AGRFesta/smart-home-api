package org.agrfesta.sh.api.core.application.ports.outbounds.devices

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.devices.SensorReadings

interface Sensor : DeviceDriver {
    fun fetchReadings(): Either<SensorReadingsFailure, SensorReadings>
}
