package org.agrfesta.sh.api.services.heating

import org.agrfesta.sh.api.domain.areas.HeatableArea
import org.agrfesta.sh.api.domain.commons.Temperature
import org.agrfesta.sh.api.domain.devices.ActuatorStatus
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.springframework.stereotype.Service

@Service
class HeatingService(
    private val config: HeatingConfiguration
) {
    private val logger by LoggerDelegate()

    suspend fun requiresHeatingFor(area: HeatableArea, targetTemp: Temperature): Boolean =
        area.getCurrentTemperature().fold(
            ifLeft = {
                logger.error("Unable to fetch current temperature for Area '${area.uuid}'.")
                false
            },
            ifRight = { temp ->
                when {
                    temp > targetTemp.plus(config.hysteresis) -> false
                    temp < targetTemp.minus(config.hysteresis) -> true
                    else -> area.requiresHeatingInHysteresisRange(temp, targetTemp)
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