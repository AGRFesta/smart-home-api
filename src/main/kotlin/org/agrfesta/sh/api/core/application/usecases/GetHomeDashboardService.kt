package org.agrfesta.sh.api.core.application.usecases

import arrow.core.Either
import java.math.BigDecimal
import java.time.LocalTime
import java.util.UUID
import org.agrfesta.sh.api.core.application.ports.inbounds.GetHomeDashboardUseCase
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.AreasWithDevicesRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.settings.PropertyRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.sensors.SensorsCurrentReadingsRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.settings.TemperatureSettingsRepository
import org.agrfesta.sh.api.core.domain.commons.FieldFailure
import org.agrfesta.sh.api.core.domain.commons.FieldResult
import org.agrfesta.sh.api.core.domain.commons.FieldSuccess
import org.agrfesta.sh.api.core.domain.commons.Temperature
import org.agrfesta.sh.api.core.domain.commons.ThermoHygroData
import org.agrfesta.sh.api.core.domain.commons.average
import org.agrfesta.sh.api.core.domain.failures.GetHomeDashboardFailure
import org.agrfesta.sh.api.core.domain.failures.ReadingsLookupFailure
import org.agrfesta.sh.api.core.domain.heating.SharedHeatingStrategy
import org.agrfesta.sh.api.core.domain.home.AreaDashboardDto
import org.agrfesta.sh.api.core.domain.home.GlobalStateDto
import org.agrfesta.sh.api.core.domain.home.HeatingDto
import org.agrfesta.sh.api.core.domain.home.HomeDashboardDto
import org.agrfesta.sh.api.core.domain.home.HumidityDto
import org.agrfesta.sh.api.core.domain.home.MeasurementsDto
import org.agrfesta.sh.api.services.heating.DynamicSharedHeatingStrategyService.Companion.HEATING_STRATEGY_KEY
import org.agrfesta.sh.api.core.application.usecases.EvaluateHeatingStateService.Companion.HEATING_ENABLED_KEY
import org.agrfesta.sh.api.utils.TimeService
import org.springframework.stereotype.Service

@Service
class GetHomeDashboardService(
    private val propertyRepository: PropertyRepository,
    private val areasWithDevicesRepository: AreasWithDevicesRepository,
    private val sensorsCurrentReadingsRepository: SensorsCurrentReadingsRepository,
    private val temperatureSettingsRepository: TemperatureSettingsRepository,
    private val timeService: TimeService
) : GetHomeDashboardUseCase {

    override fun execute(): Either<GetHomeDashboardFailure, HomeDashboardDto> {
        val currentTime = timeService.currentLocalTime()
        val heatingActive = resolveHeatingActive()
        return areasWithDevicesRepository.getAllAreasWithDevices().map { areas ->
            HomeDashboardDto(
                globalState = GlobalStateDto(
                    heatingActive = heatingActive,
                    strategy = resolveStrategy()
                ),
                areas = areas.map { area ->
                    val readings = area.sensors.map { sensorsCurrentReadingsRepository.findBy(it) }
                    AreaDashboardDto(
                        id = area.uuid,
                        name = area.name,
                        measurements = MeasurementsDto(
                            heating = HeatingDto(
                                currentTemperature = resolveCurrentTemperature(readings),
                                targetTemperature = if (heatingActive == FieldSuccess(true))
                                    resolveTargetTemperature(area.uuid, currentTime)
                                else
                                    FieldSuccess(null)
                            ),
                            humidity = HumidityDto(
                                relative = resolveRelativeHumidity(readings)
                            )
                        )
                    )
                }
            )
        }
    }

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
                    FieldSuccess(entry?.value?.let {
                        try { SharedHeatingStrategy.valueOf(it.uppercase()) }
                        catch (_: IllegalArgumentException) { null }
                    })
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
