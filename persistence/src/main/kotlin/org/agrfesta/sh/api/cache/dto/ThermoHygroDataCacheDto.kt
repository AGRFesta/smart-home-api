package org.agrfesta.sh.api.cache.dto

import org.agrfesta.sh.api.core.domain.commons.Percentage
import org.agrfesta.sh.api.core.domain.commons.Temperature
import org.agrfesta.sh.api.core.domain.commons.ThermoHygroData
import java.math.BigDecimal

data class ThermoHygroDataCacheDto(
    val temperature: BigDecimal,
    val relativeHumidity: BigDecimal
)

fun ThermoHygroData.toCacheDto() = ThermoHygroDataCacheDto(
    temperature = temperature.value,
    relativeHumidity = relativeHumidity.value
)

fun ThermoHygroDataCacheDto.toDomain() = ThermoHygroData(
    temperature = Temperature.of(temperature),
    relativeHumidity = Percentage(relativeHumidity)
)
