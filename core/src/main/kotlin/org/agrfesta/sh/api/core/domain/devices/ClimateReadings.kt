package org.agrfesta.sh.api.core.domain.devices

import org.agrfesta.sh.api.core.domain.commons.Temperature
import org.agrfesta.sh.api.core.domain.commons.average

/**
 * Aggregates the temperatures carried by climate [SensorReadings] into their average.
 *
 * Pure: no I/O, no logging. Readings that do not carry climate data are ignored.
 *
 * @return the average [Temperature], or `null` when no reading carries a temperature.
 */
fun Collection<SensorReadings>.averageTemperature(): Temperature? =
    filterIsInstance<ThermoHygroDataValue>()
        .map { it.thermoHygroData.temperature }
        .average()
