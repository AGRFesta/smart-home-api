package org.agrfesta.sh.api.core.application.usecases.heating

import arrow.core.Either
import arrow.core.raise.either
import org.agrfesta.sh.api.core.application.ports.inbounds.heating.EvaluateHeatingStateUseCase
import org.agrfesta.sh.api.core.application.ports.outbounds.TimeProvider
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.AreasWithDevicesRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DeviceDriver
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicesRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.Heater
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.ProviderDevicesFactory
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.Sensor
import org.agrfesta.sh.api.core.application.ports.outbounds.settings.PropertyRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.settings.TemperatureSettingsRepository
import org.agrfesta.sh.api.core.application.readmodels.areas.AreaWithDevicesView
import org.agrfesta.sh.api.core.domain.commons.Temperature
import org.agrfesta.sh.api.core.domain.devices.ActuatorStatus
import org.agrfesta.sh.api.core.domain.devices.averageTemperature
import org.agrfesta.sh.api.core.domain.failures.ActuatorOperationFailure
import org.agrfesta.sh.api.core.domain.failures.AreasUnavailable
import org.agrfesta.sh.api.core.domain.failures.DevicesUnavailable
import org.agrfesta.sh.api.core.domain.failures.EvaluateHeatingStateFailure
import org.agrfesta.sh.api.core.domain.failures.HeatingFlagUnavailable
import org.agrfesta.sh.api.core.domain.heating.ActuationOutcome
import org.agrfesta.sh.api.core.domain.heating.HeatableAreaSnapshot
import org.agrfesta.sh.api.core.domain.heating.HeaterActionOutcome
import org.agrfesta.sh.api.core.domain.heating.HeaterCommand
import org.agrfesta.sh.api.core.domain.heating.HeatingDecider
import org.agrfesta.sh.api.core.domain.heating.HeatingEvaluationReport
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.springframework.stereotype.Service
import java.time.LocalTime
import java.util.UUID

@Suppress("LongParameterList")
@Service
class EvaluateHeatingStateService(
    private val devicesRepository: DevicesRepository,
    providerDevicesFactories: Collection<ProviderDevicesFactory>,
    private val areasWithDevicesRepository: AreasWithDevicesRepository,
    private val temperatureSettingsRepository: TemperatureSettingsRepository,
    private val strategySelector: HeatingStrategySelector,
    private val propertyRepository: PropertyRepository,
    private val timeProvider: TimeProvider
) : EvaluateHeatingStateUseCase {

    private val logger by LoggerDelegate()
    private val mappedDevicesFactories = providerDevicesFactories.associateBy { it.provider }

    companion object {
        const val HEATING_ENABLED_KEY = "heating.enabled"
    }

    /**
     * A heatable area resolved against the device registry: it has at least one sensor driver; [heaters] are the
     * heater drivers among its actuators.
     */
    private data class ResolvedHeatableArea(
        val areaId: UUID,
        val sensors: Collection<Sensor>,
        val heaters: Collection<Heater>
    )

    override fun execute(): Either<EvaluateHeatingStateFailure, HeatingEvaluationReport> = either {
        if (isEnabled().bind()) evaluateHeaters().bind() else HeatingEvaluationReport.Skipped
    }

    /** Commands every registered heater: by the strategy decision over its heatable areas, or OFF without any. */
    private fun evaluateHeaters(): Either<EvaluateHeatingStateFailure, HeatingEvaluationReport> = either {
        val deviceRecords = devicesRepository.getAll().mapLeft { DevicesUnavailable }.bind()
        val devicesRegistry = deviceRecords.mapNotNull { record ->
            val factory = mappedDevicesFactories[record.provider]
            if (factory == null) {
                logger.error(
                    "No ProviderDevicesFactory registered for provider '${record.provider}', " +
                        "skipping device '${record.uuid}'"
                )
                null
            } else {
                factory.createDevice(record)
            }
        }.associateBy { it.uuid }
        val areaDtos = areasWithDevicesRepository.getAllAreasWithDevices().mapLeft { AreasUnavailable }.bind()
        val decide = strategySelector.select()
        val currentTime = timeProvider.currentLocalTime()
        val areasByHeater = areaDtos.mapNotNull { it.resolveHeatable(devicesRegistry) }
            .flatMap { area -> area.heaters.map { heater -> heater to area } }
            .groupBy({ it.first }, { it.second })
        val heatersWithoutAreas = devicesRegistry.values.filterIsInstance<Heater>() - areasByHeater.keys
        val offOutcomes = heatersWithoutAreas.map { heater ->
            logger.info("Heater '${heater.uuid}' has no heatable area: turning it OFF")
            HeaterActionOutcome(heater.uuid, HeaterCommand.OFF, heater.off().toActuationOutcome())
        }
        val decidedOutcomes = areasByHeater.map { (heater, areas) ->
            heater.commandByDecision(areas, decide, currentTime)
        }
        HeatingEvaluationReport.Evaluated(decidedOutcomes + offOutcomes)
    }

    private fun Heater.commandByDecision(
        areas: Collection<ResolvedHeatableArea>,
        decide: HeatingDecider,
        currentTime: LocalTime
    ): HeaterActionOutcome {
        val heaterStatus = getActuatorStatus().fold(
            ifLeft = {
                logger.warn("Unable to fetch heater status for heater '$uuid', defaulting to UNDEFINED: $it")
                ActuatorStatus.UNDEFINED
            },
            ifRight = { it }
        )
        val snapshots = areas.map { area ->
            HeatableAreaSnapshot(
                areaId = area.areaId,
                currentTemperature = area.currentTemperature(),
                targetTemperature = targetTemperatureOf(area.areaId, currentTime),
                heaterStatus = heaterStatus
            )
        }
        val command = decide(snapshots)
        logger.info("Heating decision for heater '$uuid': $command (snapshots: $snapshots)")
        val outcome = when (command) {
            HeaterCommand.ON -> on().toActuationOutcome()
            HeaterCommand.OFF -> off().toActuationOutcome()
            HeaterCommand.NONE -> ActuationOutcome.NotNeeded
        }
        return HeaterActionOutcome(uuid, command, outcome)
    }

    private fun Either<ActuatorOperationFailure, Unit>.toActuationOutcome(): ActuationOutcome = fold(
        ifLeft = { ActuationOutcome.Failed(it) },
        ifRight = { ActuationOutcome.Issued }
    )

    private fun AreaWithDevicesView.resolveHeatable(devicesRegistry: Map<UUID, DeviceDriver>): ResolvedHeatableArea? {
        val resolvedSensors = sensors.mapNotNull { device ->
            (
                devicesRegistry[device.uuid] ?: run {
                    logger.error(
                        "Data inconsistency: Sensor with UUID ${device.uuid} referenced by area '$name' " +
                            "but not found in persisted records"
                    )
                    null
                }
                ) as? Sensor
        }
        if (resolvedSensors.isEmpty()) return null
        val resolvedHeaters = actuators.mapNotNull { device ->
            (
                devicesRegistry[device.uuid] ?: run {
                    logger.error(
                        "Data inconsistency: Actuator with UUID ${device.uuid} referenced by area '$name' " +
                            "but not found in persisted records"
                    )
                    null
                }
                ) as? Heater
        }
        return ResolvedHeatableArea(uuid, resolvedSensors, resolvedHeaters)
    }

    private fun ResolvedHeatableArea.currentTemperature(): Temperature? {
        val readings = sensors.mapNotNull { sensor ->
            sensor.fetchReadings().fold(
                ifLeft = {
                    logger.error("Unable to fetch readings by sensor '${sensor.uuid}': $it")
                    null
                },
                ifRight = { it }
            )
        }
        return readings.averageTemperature()
            .also { if (it == null) logger.warn("Unable to determine current temperature for area '$areaId'.") }
    }

    private fun targetTemperatureOf(areaId: UUID, currentTime: LocalTime): Temperature? =
        temperatureSettingsRepository.findAreaSetting(areaId)
            .onLeft { logger.error("Failed to retrieve temperature settings for area '$areaId': $it") }
            .getOrNull()
            ?.targetTemperatureAt(currentTime)

    private fun isEnabled(): Either<EvaluateHeatingStateFailure, Boolean> =
        propertyRepository.findEntry(HEATING_ENABLED_KEY)
            .mapLeft { HeatingFlagUnavailable }
            .map { it?.value?.equals("true", ignoreCase = true) ?: false }
}
