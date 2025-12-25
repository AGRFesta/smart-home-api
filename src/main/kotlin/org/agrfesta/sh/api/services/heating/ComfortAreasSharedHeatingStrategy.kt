package org.agrfesta.sh.api.services.heating

import org.agrfesta.sh.api.domain.areas.HeatableArea
import org.agrfesta.sh.api.domain.devices.Heater
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

/**
 * Implementation of [AbstractSharedHeatingAreasStrategy] that optimizes heating for comfort.
 *
 * This strategy controls a shared heater based on the aggregate demand of multiple areas.
 * The heater is turned ON if at least one area requires heating.
 *
 * Activated when `heating.strategy` is set to `COMFORT`.
 */
@Service
@ConditionalOnProperty(name = ["heating.strategy"], havingValue = "COMFORT")
class ComfortAreasSharedHeatingStrategy: AbstractSharedHeatingAreasStrategy() {

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
