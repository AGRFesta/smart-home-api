package org.agrfesta.sh.api.controllers

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.math.BigDecimal
import java.util.UUID
import org.agrfesta.sh.api.core.domain.commons.FieldFailure
import org.agrfesta.sh.api.core.domain.commons.FieldResult
import org.agrfesta.sh.api.core.domain.commons.FieldSuccess
import org.agrfesta.sh.api.core.domain.commons.Temperature
import org.agrfesta.sh.api.core.domain.heating.SharedHeatingStrategy
import org.agrfesta.sh.api.core.domain.home.AreaDashboardDto
import org.agrfesta.sh.api.core.domain.home.GlobalStateDto
import org.agrfesta.sh.api.core.domain.home.HeatingDto
import org.agrfesta.sh.api.core.domain.home.HomeDashboardDto
import org.agrfesta.sh.api.core.domain.home.HumidityDto
import org.agrfesta.sh.api.core.domain.home.MeasurementsDto

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = FieldResultResponse.Success::class, name = "success"),
    JsonSubTypes.Type(value = FieldResultResponse.Failure::class, name = "failure")
)
sealed class FieldResultResponse<out T> {
    data class Success<out T>(val value: T) : FieldResultResponse<T>()
    data class Failure(val error: String) : FieldResultResponse<Nothing>()
}

data class HeatingResponse(
    val currentTemperature: FieldResultResponse<Temperature?>,
    val targetTemperature: FieldResultResponse<Temperature?>
)

data class HumidityResponse(
    val relative: FieldResultResponse<BigDecimal?>
)

data class MeasurementsResponse(
    val heating: HeatingResponse?,
    val humidity: HumidityResponse?
)

data class AreaDashboardResponse(
    val id: UUID,
    val name: String,
    val measurements: MeasurementsResponse
)

data class GlobalStateResponse(
    val heatingActive: FieldResultResponse<Boolean>,
    val strategy: FieldResultResponse<SharedHeatingStrategy?>
)

data class HomeDashboardResponse(
    val globalState: GlobalStateResponse,
    val areas: List<AreaDashboardResponse>
)

// Conversion

fun <T> FieldResult<T>.toResponse(): FieldResultResponse<T> = when (this) {
    is FieldSuccess -> FieldResultResponse.Success(value)
    is FieldFailure -> FieldResultResponse.Failure(error)
}

fun HomeDashboardDto.toResponse() = HomeDashboardResponse(
    globalState = globalState.toResponse(),
    areas = areas.map { it.toResponse() }
)

private fun GlobalStateDto.toResponse() = GlobalStateResponse(
    heatingActive = heatingActive.toResponse(),
    strategy = strategy.toResponse()
)

private fun AreaDashboardDto.toResponse() = AreaDashboardResponse(
    id = id,
    name = name,
    measurements = measurements.toResponse()
)

private fun MeasurementsDto.toResponse() = MeasurementsResponse(
    heating = heating?.toResponse(),
    humidity = humidity?.toResponse()
)

private fun HeatingDto.toResponse() = HeatingResponse(
    currentTemperature = currentTemperature.toResponse(),
    targetTemperature = targetTemperature.toResponse()
)

private fun HumidityDto.toResponse() = HumidityResponse(
    relative = relative.toResponse()
)
