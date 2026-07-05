package org.agrfesta.sh.api.controllers

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import org.agrfesta.sh.api.core.application.readmodels.commons.FieldFailure
import org.agrfesta.sh.api.core.application.readmodels.commons.FieldResult
import org.agrfesta.sh.api.core.application.readmodels.commons.FieldSuccess
import org.agrfesta.sh.api.core.application.readmodels.home.AreaDashboardView
import org.agrfesta.sh.api.core.application.readmodels.home.GlobalStateView
import org.agrfesta.sh.api.core.application.readmodels.home.HeatingView
import org.agrfesta.sh.api.core.application.readmodels.home.HomeDashboardView
import org.agrfesta.sh.api.core.application.readmodels.home.HumidityView
import org.agrfesta.sh.api.core.application.readmodels.home.MeasurementsView
import org.agrfesta.sh.api.core.domain.alerts.AlertType
import org.agrfesta.sh.api.core.domain.commons.Temperature
import org.agrfesta.sh.api.core.domain.heating.SharedHeatingStrategy
import java.math.BigDecimal
import java.util.UUID

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
    val currentTemperature: FieldResultResponse<BigDecimal?>,
    val targetTemperature: FieldResultResponse<BigDecimal?>
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
    val measurements: MeasurementsResponse,
    val activeAlerts: FieldResultResponse<Set<AlertType>>
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

private fun FieldResult<Temperature?>.toTemperatureResponse(): FieldResultResponse<BigDecimal?> = when (this) {
    is FieldSuccess -> FieldResultResponse.Success(value?.value)
    is FieldFailure -> FieldResultResponse.Failure(error)
}

fun HomeDashboardView.toResponse() = HomeDashboardResponse(
    globalState = globalState.toResponse(),
    areas = areas.map { it.toResponse() }
)

private fun GlobalStateView.toResponse() = GlobalStateResponse(
    heatingActive = heatingActive.toResponse(),
    strategy = strategy.toResponse()
)

private fun AreaDashboardView.toResponse() = AreaDashboardResponse(
    id = id,
    name = name,
    measurements = measurements.toResponse(),
    activeAlerts = activeAlerts.toResponse()
)

private fun MeasurementsView.toResponse() = MeasurementsResponse(
    heating = heating?.toResponse(),
    humidity = humidity?.toResponse()
)

private fun HeatingView.toResponse() = HeatingResponse(
    currentTemperature = currentTemperature.toTemperatureResponse(),
    targetTemperature = targetTemperature.toTemperatureResponse()
)

private fun HumidityView.toResponse() = HumidityResponse(
    relative = relative.toResponse()
)
