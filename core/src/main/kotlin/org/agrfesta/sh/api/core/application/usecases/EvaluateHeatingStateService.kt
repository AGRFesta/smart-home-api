package org.agrfesta.sh.api.core.application.usecases

import arrow.core.getOrElse
import org.agrfesta.sh.api.core.application.ports.inbounds.EvaluateHeatingStateUseCase
import org.agrfesta.sh.api.core.application.ports.outbounds.TimeProvider
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.AreasWithDevicesRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DeviceDriver
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicesRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.Heater
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.ProviderDevicesFactory
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.Sensor
import org.agrfesta.sh.api.core.application.ports.outbounds.settings.PropertyRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.settings.TemperatureSettingsRepository
import org.agrfesta.sh.api.core.application.usecases.heating.HeatingStrategySelector
import org.agrfesta.sh.api.core.domain.areas.AreaDtoWithDevices
import org.agrfesta.sh.api.core.domain.commons.Temperature
import org.agrfesta.sh.api.core.domain.devices.ActuatorStatus
import org.agrfesta.sh.api.core.domain.devices.averageTemperature
import org.agrfesta.sh.api.core.domain.failures.PropertyRepositoryError
import org.agrfesta.sh.api.core.domain.heating.HeatableAreaSnapshot
import org.agrfesta.sh.api.core.domain.heating.HeaterCommand
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
     * A heatable area resolved against the device registry: it has at least one sensor driver and a heater driver.
     */
    private data class ResolvedHeatableArea(
        val areaId: UUID,
        val sensors: Collection<Sensor>,
        val heater: Heater
    )

    override fun execute() {
        if (!isEnabled()) return
        val deviceRecords = devicesRepository.getAll().onLeft { _ ->
            logger.error("Device fetch failed, skipping heating evaluation.")
        }.getOrNull() ?: return
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
        val areaDtos = areasWithDevicesRepository.getAllAreasWithDevices().onLeft {
            logger.error("Area fetch failed, skipping heating evaluation.")
        }.getOrNull() ?: return
        val decide = strategySelector.select()
        val currentTime = timeProvider.currentLocalTime()
        areaDtos.mapNotNull { it.resolveHeatable(devicesRegistry) }
            .groupBy { it.heater }
            .forEach { (heater, areaList) ->
                val heaterStatus = heater.getActuatorStatus().getOrElse { ActuatorStatus.UNDEFINED }
                val snapshots = areaList.map { area ->
                    HeatableAreaSnapshot(
                        areaId = area.areaId,
                        currentTemperature = area.currentTemperature(),
                        targetTemperature = targetTemperatureOf(area.areaId, currentTime),
                        heaterStatus = heaterStatus
                    )
                }
                val command = decide(snapshots)
                logger.info("Heating decision for heater '${heater.uuid}': $command (snapshots: $snapshots)")
                when (command) {
                    HeaterCommand.ON -> heater.on()
                    HeaterCommand.OFF -> heater.off()
                    HeaterCommand.NONE -> {}
                }
            }
    }

    private fun AreaDtoWithDevices.resolveHeatable(devicesRegistry: Map<UUID, DeviceDriver>): ResolvedHeatableArea? {
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
        val heater = resolvedHeaters.firstOrNull() ?: return null
        if (resolvedHeaters.size > 1) {
            logger.warn("Area '$uuid' has multiple heaters assigned. Using the first one: '${heater.uuid}'.")
        }
        return ResolvedHeatableArea(uuid, resolvedSensors, heater)
    }

    private fun ResolvedHeatableArea.currentTemperature(): Temperature? {
        val readings = sensors.mapNotNull { sensor ->
            sensor.fetchReadings().fold(
                ifLeft = { _ ->
                    logger.error("Unable to fetch readings by sensor '${sensor.uuid}'.")
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

    private fun isEnabled(): Boolean = propertyRepository.findEntry(HEATING_ENABLED_KEY).fold(
        ifLeft = {
            when (it) {
                PropertyRepositoryError ->
                    logger.error("Failed to fetch $HEATING_ENABLED_KEY. Considered disabled.")
            }
            false
        },
        ifRight = { it?.value?.equals("true", ignoreCase = true) ?: false }
    )
}
