package org.agrfesta.sh.api.core.application.usecases

import arrow.core.Either
import org.agrfesta.sh.api.core.application.ports.inbounds.GetHomeDashboardUseCase
import org.agrfesta.sh.api.core.application.ports.outbounds.TimeProvider
import org.agrfesta.sh.api.core.application.ports.outbounds.alerts.AlertsRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.AreasWithDevicesRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.sensors.SensorsCurrentReadingsRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.settings.PropertyRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.settings.TemperatureSettingsRepository
import org.agrfesta.sh.api.core.application.readmodels.areas.AreaWithDevicesView
import org.agrfesta.sh.api.core.application.readmodels.commons.FieldFailure
import org.agrfesta.sh.api.core.application.readmodels.commons.FieldResult
import org.agrfesta.sh.api.core.application.readmodels.commons.FieldSuccess
import org.agrfesta.sh.api.core.application.readmodels.home.AreaDashboardView
import org.agrfesta.sh.api.core.application.readmodels.home.GlobalStateView
import org.agrfesta.sh.api.core.application.readmodels.home.HeatingView
import org.agrfesta.sh.api.core.application.readmodels.home.HomeDashboardView
import org.agrfesta.sh.api.core.application.readmodels.home.HumidityView
import org.agrfesta.sh.api.core.application.readmodels.home.MeasurementsView
import org.agrfesta.sh.api.core.application.usecases.EvaluateHeatingStateService.Companion.HEATING_ENABLED_KEY
import org.agrfesta.sh.api.core.application.usecases.heating.HeatingStrategySelector.Companion.HEATING_STRATEGY_KEY
import org.agrfesta.sh.api.core.domain.alerts.Alert
import org.agrfesta.sh.api.core.domain.alerts.AlertStatus
import org.agrfesta.sh.api.core.domain.alerts.AlertTarget
import org.agrfesta.sh.api.core.domain.alerts.AlertType
import org.agrfesta.sh.api.core.domain.commons.Temperature
import org.agrfesta.sh.api.core.domain.commons.ThermoHygroData
import org.agrfesta.sh.api.core.domain.commons.average
import org.agrfesta.sh.api.core.domain.failures.DashboardRepositoryError
import org.agrfesta.sh.api.core.domain.failures.GetAlertsFailure
import org.agrfesta.sh.api.core.domain.failures.GetHomeDashboardFailure
import org.agrfesta.sh.api.core.domain.failures.ReadingsLookupFailure
import org.agrfesta.sh.api.core.domain.heating.SharedHeatingStrategy
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalTime
import java.util.UUID

@Service
class GetHomeDashboardService(
    private val propertyRepository: PropertyRepository,
    private val areasWithDevicesRepository: AreasWithDevicesRepository,
    private val sensorsCurrentReadingsRepository: SensorsCurrentReadingsRepository,
    private val temperatureSettingsRepository: TemperatureSettingsRepository,
    private val timeProvider: TimeProvider,
    private val alertsRepository: AlertsRepository
) : GetHomeDashboardUseCase {

    override fun execute(): Either<GetHomeDashboardFailure, HomeDashboardView> {
        val currentTime = timeProvider.currentLocalTime()
        val heatingActive = resolveHeatingActive()
        return areasWithDevicesRepository.getAllAreasWithDevices()
            .mapLeft { DashboardRepositoryError }
            .map { areas ->
                val openAlerts = alertsRepository.getAlerts(AlertStatus.OPEN)
                HomeDashboardView(
                    globalState = GlobalStateView(
                        heatingActive = heatingActive,
                        strategy = resolveStrategy()
                    ),
                    areas = areas.map { area ->
                        val readings = area.sensors.map { sensorsCurrentReadingsRepository.findBy(it) }
                        AreaDashboardView(
                            id = area.uuid,
                            name = area.name,
                            measurements = MeasurementsView(
                                heating = HeatingView(
                                    currentTemperature = resolveCurrentTemperature(readings),
                                    targetTemperature = if (heatingActive == FieldSuccess(true)) {
                                        resolveTargetTemperature(area.uuid, currentTime)
                                    } else {
                                        FieldSuccess(null)
                                    }
                                ),
                                humidity = HumidityView(
                                    relative = resolveRelativeHumidity(readings)
                                )
                            ),
                            activeAlerts = resolveActiveAlerts(area, openAlerts)
                        )
                    }
                )
            }
    }

    private fun resolveActiveAlerts(
        area: AreaWithDevicesView,
        openAlerts: Either<GetAlertsFailure, Collection<Alert>>
    ): FieldResult<Set<AlertType>> =
        openAlerts.fold(
            { FieldFailure("Unable to retrieve active alerts") },
            { alerts ->
                val areaDeviceIds = (area.sensors + area.actuators).map { it.uuid }.toSet()
                FieldSuccess(
                    alerts
                        .filter { (it.target as? AlertTarget.Device)?.deviceId in areaDeviceIds }
                        .map { it.type }
                        .toSet()
                )
            }
        )

    private fun resolveHeatingActive(): FieldResult<Boolean> =
        propertyRepository.findEntry(HEATING_ENABLED_KEY)
            .fold(
                { FieldFailure("Unable to retrieve heating status") },
                { entry -> FieldSuccess(entry?.value?.equals("true", ignoreCase = true) ?: false) }
            )

    private fun resolveStrategy(): FieldResult<SharedHeatingStrategy?> =
        propertyRepository.findEntry(HEATING_STRATEGY_KEY)
            .fold(
                { FieldFailure("Unable to retrieve heating strategy") },
                { entry ->
                    FieldSuccess(
                        entry?.value?.let {
                            try {
                                SharedHeatingStrategy.valueOf(it.uppercase())
                            } catch (_: IllegalArgumentException) { null }
                        }
                    )
                }
            )

    private fun resolveCurrentTemperature(
        readings: List<Either<ReadingsLookupFailure, ThermoHygroData?>>
    ): FieldResult<Temperature?> {
        if (readings.isEmpty()) return FieldSuccess(null)
        val successful = readings.filter { it.isRight() }
        if (successful.isEmpty()) return FieldFailure("Unable to retrieve area temperature readings")
        val temperatures = successful.mapNotNull { it.getOrNull()?.temperature }
        return FieldSuccess(temperatures.average())
    }

    private fun resolveRelativeHumidity(
        readings: List<Either<ReadingsLookupFailure, ThermoHygroData?>>
    ): FieldResult<BigDecimal?> {
        if (readings.isEmpty()) return FieldSuccess(null)
        val successful = readings.filter { it.isRight() }
        if (successful.isEmpty()) return FieldFailure("Unable to retrieve area humidity readings")
        val humidities = successful.mapNotNull { it.getOrNull()?.relativeHumidity }
        return FieldSuccess(humidities.average())
    }

    private fun resolveTargetTemperature(areaId: UUID, currentTime: LocalTime): FieldResult<Temperature?> =
        temperatureSettingsRepository.findAreaSetting(areaId)
            .fold(
                { FieldFailure("Unable to retrieve area target temperature") },
                { setting ->
                    FieldSuccess(
                        setting?.temperatureSchedule?.firstNotNullOfOrNull { it.temperatureAt(currentTime) }
                            ?: setting?.defaultTemperature
                    )
                }
            )
}
