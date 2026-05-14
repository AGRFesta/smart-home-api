package org.agrfesta.sh.api.core.domain.devices

import org.agrfesta.sh.api.core.domain.commons.ThermoHygroData

sealed interface SensorReadings

interface BatteryValue: SensorReadings {
    val battery: Int
}

interface ThermoHygroDataValue: SensorReadings {
    val thermoHygroData: ThermoHygroData
}

interface SensorReadingsFailure

data class FailureByException(val reason: Exception): SensorReadingsFailure
