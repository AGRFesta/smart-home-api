package org.agrfesta.sh.api.core.application.usecases.heating

import java.math.BigDecimal
import java.time.LocalTime
import org.agrfesta.sh.api.core.domain.areas.HeatableArea
import org.agrfesta.sh.api.core.domain.commons.Temperature
import org.agrfesta.sh.api.core.domain.devices.ActuatorStatus
import org.agrfesta.sh.api.core.domain.devices.Heater
import org.agrfesta.sh.api.utils.LoggerDelegate

/**
 * Strategy interface for controlling a shared heater across multiple heatable areas.
 *
 * Implementations define specific logic to determine when the shared heater should be turned ON or OFF
 * based on the state and requirements of the associated areas.
 */
sealed interface SharedHeatingAreasStrategyService {
    suspend fun handleHeatingFor(sharedHeater: Heater, areas: Collection<HeatableArea>, currentTime: LocalTime)
}

/**
 * Base implementation of [SharedHeatingAreasStrategyService] providing common validation and utility methods.
 *
 * This class handles preliminary checks (e.g., ensuring areas exist and share the same heater)
 * before delegating the specific heating logic to [internalHandleHeatingFor].
 *
 * It also provides helper methods to determine if a specific area requires heating based on
 * current temperature, target temperature, and hysteresis logic.
 */
abstract class AbstractSharedHeatingAreasStrategyService: SharedHeatingAreasStrategyService {
    private val logger by LoggerDelegate()

    companion object {
        val HYSTERESIS: Temperature = Temperature.of(BigDecimal.ONE)
    }

    protected abstract suspend fun internalHandleHeatingFor(
        sharedHeater: Heater,
        areas: Collection<HeatableArea>,
        currentTime: LocalTime
    )

    override suspend fun handleHeatingFor(
        sharedHeater: Heater,
        areas: Collection<HeatableArea>,
        currentTime: LocalTime
    ) {
        if (areas.isEmpty()) {
            logger.warn("There are no areas! Skipping heating control task.")
            return
        }
        val heaters = areas.map { it.heater }.toSet()
        // This check is kept to ensure the strategy's robustness if it were to be used
        // outside HeatingControlScheduler, where the 'areas' collection might not
        // be pre-grouped by a single shared heater.
        if (heaters.size > 1) {
            logger.error("Areas are not sharing same heater! Skipping heating control task.")
            return
        }
        internalHandleHeatingFor(sharedHeater, areas, currentTime)
    }

    suspend fun HeatableArea.requiresHeatingFor(targetTemp: Temperature): Boolean =
        getCurrentTemperature().fold(
            ifLeft = {
                logger.error("Unable to fetch current temperature for Area '$uuid'.")
                false
            },
            ifRight = { temp ->
                when {
                    temp > targetTemp + HYSTERESIS -> false
                    temp < targetTemp - HYSTERESIS -> true
                    else -> requiresHeatingInHysteresisRange(temp, targetTemp)
                }
            }
        )

    private suspend fun HeatableArea.requiresHeatingInHysteresisRange(
        temp: Temperature,
        targetTemp: Temperature
    ): Boolean = heater.getActuatorStatus().fold(
        ifLeft = {
            logger.warn("Unable to fetch Heater '${heater.uuid}' status")
            temp <= targetTemp
        },
        ifRight = { status ->
            when(status) {
                ActuatorStatus.ON -> true
                ActuatorStatus.OFF -> false
                ActuatorStatus.UNDEFINED -> temp <= targetTemp
            }
        }
    )

}
