package org.agrfesta.sh.api.core.domain.devices

import org.agrfesta.sh.api.core.domain.commons.ThermoHygroData
import org.agrfesta.sh.api.core.domain.failures.Failure

sealed interface SensorReadings

interface BatteryValue: SensorReadings {
    val battery: Int
}

interface ThermoHygroDataValue: SensorReadings {
    val thermoHygroData: ThermoHygroData
}

sealed interface SensorReadingsFailure: Failure

class FailureByException(val reason: Throwable): SensorReadingsFailure
