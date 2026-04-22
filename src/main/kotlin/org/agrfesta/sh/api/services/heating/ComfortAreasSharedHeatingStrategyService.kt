package org.agrfesta.sh.api.services.heating

import org.agrfesta.sh.api.core.domain.areas.HeatableArea
import org.agrfesta.sh.api.core.domain.devices.Heater
import org.agrfesta.sh.api.core.domain.heating.SharedHeatingStrategy
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.springframework.stereotype.Service

/**
 * Implementation of [AbstractSharedHeatingAreasStrategyService] that optimizes heating for comfort.
 *
 * This strategy controls a shared heater based on the aggregate demand of multiple areas.
 * The heater is turned ON if at least one area requires heating.
 *
 * It corresponds to the [SharedHeatingStrategy.COMFORT] strategy.
 */
@Service
class ComfortAreasSharedHeatingStrategyService: NamedSharedHeatingAreasStrategyService,
    AbstractSharedHeatingAreasStrategyService() {
    private val logger by LoggerDelegate()
    override val strategy: SharedHeatingStrategy = SharedHeatingStrategy.COMFORT

    override suspend fun internalHandleHeatingFor(
        sharedHeater: Heater,
        areas: Collection<HeatableArea>
    ) {
        val areasToHeat = areas.filter { a ->
            a.getCurrentTargetTemperature()
                ?.let { a.requiresHeatingFor(it) }
                ?: false
        }
        if (areasToHeat.isNotEmpty()) {
            logger.info("At least one area requires heating ${areasToHeat.map { a -> a.uuid }}. Turning heater ON")
            sharedHeater.on()
        } else {
            logger.info("No area requires heating. Turning heater OFF")
            sharedHeater.off()
        }
    }

}
