package org.agrfesta.sh.api.services.heating

import java.math.BigDecimal
import java.math.RoundingMode
import org.agrfesta.sh.api.domain.areas.HeatableArea
import org.agrfesta.sh.api.domain.commons.Percentage
import org.agrfesta.sh.api.domain.commons.SharedHeaterContext
import org.agrfesta.sh.api.domain.commons.Temperature
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/**
 * Implementation of [AbstractSharedHeatingAreasStrategyService] that optimizes heating for economy.
 *
 * This strategy controls a shared heater based on the aggregate demand of multiple areas.
 * The heater is turned ON only if the percentage of areas requiring heating meets or exceeds
 * the configured threshold ([percentage]).
 *
 * Additionally, to prevent overheating, the heater is forced OFF if any single area exceeds
 * its target temperature by the defined hysteresis margin.
 *
 * It corresponds to the [SharedHeatingAreasStrategy.ECONOMY] strategy.
 *
 * @property percentage The minimum percentage of areas that must require heating to turn the shared heater on.
 */
@Service
class EconomyAreasSharedHeatingStrategyService(
    private val heatingConfiguration: HeatingConfiguration,
    private val heatingService: HeatingService,
    @param:Value("\${heating.params.economy-areas-percentage:0.5}") private val percentage: Percentage
): NamedSharedHeatingAreasStrategyService {
    private val logger by LoggerDelegate()
    override val strategy: SharedHeatingAreasStrategy = SharedHeatingAreasStrategy.ECONOMY

    override suspend fun handleHeatingFor(context: SharedHeaterContext) {
        val enoughHeatingArea = context.areas.firstOrNull { a ->
            a.getCurrentTargetTemperature()?.let { target ->
                a.tempAboveTargetRange(target)
            } ?: false
        }
        if (enoughHeatingArea != null) {
            logger.info("Area ${enoughHeatingArea.uuid} temp is above target range, turning heater OFF")
            context.heater.off()
            return
        }
        val areasToHeat = context.areas.filter { a ->
            a.getCurrentTargetTemperature()
                ?.let { heatingService.requiresHeatingFor(a, it) }
                ?: false
        }
        val neededHeatingPerc = Percentage(BigDecimal(areasToHeat.size)
            .divide(BigDecimal(context.areas.size), 2, RoundingMode.HALF_UP))
        if (neededHeatingPerc.value >= percentage.value) {
            logger.info("${neededHeatingPerc.toHundreds()} of the heatable areas require heating. " +
                    "(above ${percentage.toHundreds()}) -> heater ON")
            context.heater.on()
        } else {
            logger.info("${neededHeatingPerc.toHundreds()} of the heatable areas require heating. " +
                    "(below ${percentage.toHundreds()}) -> heater OFF")
            context.heater.off()
        }
    }

    private suspend fun HeatableArea.tempAboveTargetRange(targetTemp: Temperature): Boolean =
        getCurrentTemperature().fold(
            ifLeft = {
                logger.error("Unable to fetch current temperature for Area '$uuid'.")
                false
            },
            ifRight = { temp -> temp > targetTemp.plus(heatingConfiguration.hysteresis) }
        )
}
