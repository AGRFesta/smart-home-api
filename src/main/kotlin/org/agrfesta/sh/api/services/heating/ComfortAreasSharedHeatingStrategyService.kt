package org.agrfesta.sh.api.services.heating

import org.agrfesta.sh.api.domain.commons.SharedHeaterContext
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.springframework.stereotype.Service

/**
 * Implementation of [AbstractSharedHeatingAreasStrategyService] that optimizes heating for comfort.
 *
 * This strategy controls a shared heater based on the aggregate demand of multiple areas.
 * The heater is turned ON if at least one area requires heating.
 *
 * It corresponds to the [SharedHeatingAreasStrategy.COMFORT] strategy.
 */
@Service
class ComfortAreasSharedHeatingStrategyService(
    private val heatingService: HeatingService
): NamedSharedHeatingAreasStrategyService {
    private val logger by LoggerDelegate()
    override val strategy: SharedHeatingAreasStrategy = SharedHeatingAreasStrategy.COMFORT

    override suspend fun handleHeatingFor(context: SharedHeaterContext) {
        val areasToHeat = context.areas.filter { a ->
            a.getCurrentTargetTemperature()
                ?.let { heatingService.requiresHeatingFor(a, it) }
                ?: false
        }
        if (areasToHeat.isNotEmpty()) {
            logger.info("At least one area requires heating ${areasToHeat.map { a -> a.uuid }}. Turning heater ON")
            context.heater.on()
        } else {
            logger.info("No area requires heating. Turning heater OFF")
            context.heater.off()
        }
    }

}
