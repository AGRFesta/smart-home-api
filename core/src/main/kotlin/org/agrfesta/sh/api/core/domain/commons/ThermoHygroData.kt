package org.agrfesta.sh.api.core.domain.commons

data class ThermoHygroData(
    val temperature: Temperature,
    val relativeHumidity: RelativeHumidity
) {
    fun calculateAbsoluteHumidity() = AbsoluteHumidity(temperature, relativeHumidity)
}
