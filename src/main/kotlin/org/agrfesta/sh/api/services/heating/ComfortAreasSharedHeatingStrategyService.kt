package org.agrfesta.sh.api.services.heating

import org.agrfesta.sh.api.domain.areas.HeatableArea
import org.agrfesta.sh.api.domain.devices.Heater
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
class ComfortAreasSharedHeatingStrategyService: NamedSharedHeatingAreasStrategyService,
    AbstractSharedHeatingAreasStrategyService() {
    override val strategy: SharedHeatingAreasStrategy = SharedHeatingAreasStrategy.COMFORT

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
            sharedHeater.on()
        } else {
            sharedHeater.off()
        }
    }

}
