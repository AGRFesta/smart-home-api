package org.agrfesta.sh.api.core.domain.home

import org.agrfesta.sh.api.core.domain.commons.FieldResult
import org.agrfesta.sh.api.core.domain.commons.Temperature
import org.agrfesta.sh.api.core.domain.heating.SharedHeatingStrategy
import java.math.BigDecimal
import java.util.UUID

data class HeatingDto(
    val currentTemperature: FieldResult<Temperature?>,
    val targetTemperature: FieldResult<Temperature?>
)

data class HumidityDto(
    val relative: FieldResult<BigDecimal?>
)

data class MeasurementsDto(
    val heating: HeatingDto?,
    val humidity: HumidityDto?
)

data class AreaDashboardDto(
    val id: UUID,
    val name: String,
    val measurements: MeasurementsDto
)

data class GlobalStateDto(
    val heatingActive: FieldResult<Boolean>,
    val strategy: FieldResult<SharedHeatingStrategy?>
)

data class HomeDashboardDto(
    val globalState: GlobalStateDto,
    val areas: List<AreaDashboardDto>
)
