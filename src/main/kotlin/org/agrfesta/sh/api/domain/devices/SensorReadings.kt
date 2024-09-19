package org.agrfesta.sh.api.domain.devices

import arrow.core.Either

sealed interface SensorReadings

interface BatteryValue: SensorReadings {
    val battery: Int
}

interface TemperatureValue: SensorReadings {
    val temperature: Float
}

interface HumidityValue: SensorReadings {
    val humidity: Int
}

sealed interface SensorReadingsFailure

class FailureByException(e: Exception): SensorReadingsFailure

interface ReadableValuesDeviceProvider {
    suspend fun fetchSensorReadings(deviceProviderId: String): Either<SensorReadingsFailure, SensorReadings>
}
