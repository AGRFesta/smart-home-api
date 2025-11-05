package org.agrfesta.sh.api.domain.devices

import arrow.core.Either
import org.agrfesta.sh.api.domain.commons.ThermoHygroData
import org.agrfesta.sh.api.domain.failures.Failure
import org.slf4j.Logger

sealed interface SensorReadings

interface BatteryValue: SensorReadings {
    val battery: Int
}

interface ThermoHygroDataValue: SensorReadings {
    val thermoHygroData: ThermoHygroData
}

sealed interface SensorReadingsFailure: Failure

class FailureByException(val reason: Throwable): SensorReadingsFailure

interface ReadableValuesDeviceProvider {
    suspend fun fetchSensorReadings(deviceProviderId: String): Either<SensorReadingsFailure, SensorReadings>
}

fun Either<Failure, SensorReadings>.onLeftLogOn(logger: Logger) = onLeft {
    when (it) {
        is FailureByException -> logger.error("Failure fetching data", it.reason)
    }
}
