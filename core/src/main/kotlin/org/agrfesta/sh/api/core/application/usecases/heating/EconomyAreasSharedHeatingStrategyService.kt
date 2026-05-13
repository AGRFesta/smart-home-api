package org.agrfesta.sh.api.core.application.usecases.heating

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalTime
import org.agrfesta.sh.api.core.domain.areas.HeatableArea
import org.agrfesta.sh.api.core.domain.heating.SharedHeatingStrategy
import org.agrfesta.sh.api.core.domain.commons.Percentage
import org.agrfesta.sh.api.core.domain.commons.Temperature
import org.agrfesta.sh.api.core.domain.devices.Heater
import org.agrfesta.sh.api.core.application.usecases.heating.AbstractSharedHeatingAreasStrategyService.Companion.HYSTERESIS
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
 * It corresponds to the [SharedHeatingStrategy.ECONOMY] strategy.
 *
 * @property percentage The minimum percentage of areas that must require heating to turn the shared heater on.
 */
@Service
class EconomyAreasSharedHeatingStrategyService(
    @Value("\${heating.params.economy-areas-percentage:0.5}") percentageValue: BigDecimal
): NamedSharedHeatingAreasStrategyService, AbstractSharedHeatingAreasStrategyService() {
    private val percentage = Percentage(percentageValue)
    private val logger by LoggerDelegate()
    override val strategy: SharedHeatingStrategy = SharedHeatingStrategy.ECONOMY

    override fun internalHandleHeatingFor(
        sharedHeater: Heater,
        areas: Collection<HeatableArea>,
        currentTime: LocalTime
    ) {
        val enoughHeatingArea = areas.firstOrNull { a ->
            a.getCurrentTargetTemperature(currentTime)?.let { target ->
                a.tempAboveTargetRange(target)
            } ?: false
        }
        if (enoughHeatingArea != null) {
            logger.info("Area ${enoughHeatingArea.uuid} temp is above target range, turning heater OFF")
            sharedHeater.off()
            return
        }
        val areasToHeat = areas.filter { a ->
            a.getCurrentTargetTemperature(currentTime)
                ?.let { a.requiresHeatingFor(it) }
                ?: false
        }
        val neededHeatingPerc = Percentage(BigDecimal(areasToHeat.size)
            .divide(BigDecimal(areas.size), 2, RoundingMode.HALF_UP))
        if (neededHeatingPerc.value >= percentage.value) {
            logger.info("$neededHeatingPerc of the heatable areas require heating. " +
                    "(above $percentage) -> heater ON")
            sharedHeater.on()
        } else {
            logger.info("$neededHeatingPerc of the heatable areas require heating. " +
                    "(below $percentage) -> heater OFF")
            sharedHeater.off()
        }
    }

    private fun HeatableArea.tempAboveTargetRange(targetTemp: Temperature): Boolean =
        getCurrentTemperature().fold(
            ifLeft = {
                logger.error("Unable to fetch current temperature for Area '$uuid'.")
                false
            },
            ifRight = { temp -> temp > (targetTemp + HYSTERESIS) }
        )
}
