package org.agrfesta.sh.api.core.domain.devices

import org.agrfesta.sh.api.core.domain.commons.ThermoHygroData

sealed interface SensorReadings

interface ThermoHygroDataValue : SensorReadings {
    val thermoHygroData: ThermoHygroData
}
