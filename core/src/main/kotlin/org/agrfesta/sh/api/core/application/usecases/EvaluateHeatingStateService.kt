package org.agrfesta.sh.api.core.application.usecases

import arrow.core.getOrElse
import org.agrfesta.sh.api.core.application.areas.AreasFactory
import org.agrfesta.sh.api.core.application.ports.inbounds.EvaluateHeatingStateUseCase
import org.agrfesta.sh.api.core.application.ports.outbounds.TimeProvider
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.AreasWithDevicesRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicesRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.ProviderDevicesFactory
import org.agrfesta.sh.api.core.application.ports.outbounds.settings.PropertyRepository
import org.agrfesta.sh.api.core.application.usecases.heating.HeatingStrategySelector
import org.agrfesta.sh.api.core.domain.areas.HeatableArea
import org.agrfesta.sh.api.core.domain.devices.ActuatorStatus
import org.agrfesta.sh.api.core.domain.failures.PropertyRepositoryError
import org.agrfesta.sh.api.core.domain.heating.HeatableAreaSnapshot
import org.agrfesta.sh.api.core.domain.heating.HeaterCommand
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.springframework.stereotype.Service

@Suppress("LongParameterList")
@Service
class EvaluateHeatingStateService(
    private val devicesRepository: DevicesRepository,
    providerDevicesFactories: Collection<ProviderDevicesFactory>,
    private val areasWithDevicesRepository: AreasWithDevicesRepository,
    private val areasFactory: AreasFactory,
    private val strategySelector: HeatingStrategySelector,
    private val propertyRepository: PropertyRepository,
    private val timeProvider: TimeProvider
) : EvaluateHeatingStateUseCase {

    private val logger by LoggerDelegate()
    private val mappedDevicesFactories = providerDevicesFactories.associateBy { it.provider }

    companion object {
        const val HEATING_ENABLED_KEY = "heating.enabled"
    }

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
        val areas = areasWithDevicesRepository.getAllAreasWithDevices().onLeft {
            logger.error("Area fetch failed, skipping heating evaluation.")
        }.getOrNull()?.map { areasFactory.createArea(it, devicesRegistry) } ?: return
        val decide = strategySelector.select()
        val currentTime = timeProvider.currentLocalTime()
        areas.filterIsInstance<HeatableArea>()
            .groupBy { it.heater }
            .forEach { (heater, areaList) ->
                val heaterStatus = heater.getActuatorStatus().getOrElse { ActuatorStatus.UNDEFINED }
                val snapshots = areaList.map { area ->
                    HeatableAreaSnapshot(
                        areaId = area.uuid,
                        currentTemperature = area.getCurrentTemperature()
                            .onLeft { logger.warn("Unable to fetch current temperature for area '${area.uuid}'.") }
                            .getOrNull(),
                        targetTemperature = area.getCurrentTargetTemperature(currentTime),
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
