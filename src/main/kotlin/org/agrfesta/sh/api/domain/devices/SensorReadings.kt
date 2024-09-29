package org.agrfesta.sh.api.domain.devices

import arrow.core.Either
import org.slf4j.Logger

sealed interface SensorReadings

interface BatteryValue: SensorReadings {
    val battery: Int
}

interface TemperatureValue: SensorReadings {
    val temperature: Temperature
}

interface HumidityValue: SensorReadings {
    val humidity: Humidity
}

sealed interface SensorReadingsFailure

class FailureByException(val reason: Throwable): SensorReadingsFailure

interface ReadableValuesDeviceProvider {
    suspend fun fetchSensorReadings(deviceProviderId: String): Either<SensorReadingsFailure, SensorReadings>
}

fun Either<SensorReadingsFailure, SensorReadings>.onLeftLogOn(logger: Logger) = onLeft {
    when (it) {
        is FailureByException -> logger.error("Failure fetching data", it.reason)
    }
}
