package org.agrfesta.sh.api.core.application.readmodels.home

import org.agrfesta.sh.api.core.application.readmodels.commons.FieldResult
import org.agrfesta.sh.api.core.domain.alerts.AlertType
import org.agrfesta.sh.api.core.domain.commons.Temperature
import org.agrfesta.sh.api.core.domain.heating.SharedHeatingStrategy
import java.math.BigDecimal
import java.util.UUID

data class HeatingView(
    val currentTemperature: FieldResult<Temperature?>,
    val targetTemperature: FieldResult<Temperature?>
)

data class HumidityView(
    val relative: FieldResult<BigDecimal?>
)

data class MeasurementsView(
    val heating: HeatingView?,
    val humidity: HumidityView?
)

data class AreaDashboardView(
    val id: UUID,
    val name: String,
    val measurements: MeasurementsView,
    val activeAlerts: FieldResult<Set<AlertType>>
)

data class GlobalStateView(
    val heatingActive: FieldResult<Boolean>,
    val strategy: FieldResult<SharedHeatingStrategy?>
)

data class HomeDashboardView(
    val globalState: GlobalStateView,
    val areas: List<AreaDashboardView>
)
