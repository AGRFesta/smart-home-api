package org.agrfesta.sh.api.core.domain.heating

import org.agrfesta.sh.api.core.domain.commons.Percentage
import org.agrfesta.sh.api.core.domain.commons.Temperature
import org.agrfesta.sh.api.core.domain.devices.ActuatorStatus
import java.math.BigDecimal
import java.math.RoundingMode

private val HYSTERESIS: Temperature = Temperature.of(BigDecimal.ONE)

/**
 * A pure heating decision: maps the snapshots of the areas served by a shared heater to the desired
 * [HeaterCommand]. No I/O, no logging.
 */
typealias HeatingDecider = (Collection<HeatableAreaSnapshot>) -> HeaterCommand

/**
 * Pure heating decision for the COMFORT strategy.
 *
 * Returns the desired [HeaterCommand] for a shared heater given the snapshots of the areas it serves.
 * No I/O, no logging.
 */
fun decideComfort(areas: Collection<HeatableAreaSnapshot>): HeaterCommand = when {
    areas.isEmpty() -> HeaterCommand.NONE
    areas.any { it.requiresHeating() } -> HeaterCommand.ON
    else -> HeaterCommand.OFF
}

/**
 * Pure heating decision for the ECONOMY strategy.
 *
 * Returns the desired [HeaterCommand] for a shared heater given the snapshots of the areas it serves
 * and the minimum [threshold] ratio of areas that must require heating to turn the heater ON.
 * No I/O, no logging.
 */
fun decideEconomy(areas: Collection<HeatableAreaSnapshot>, threshold: Percentage): HeaterCommand = when {
    areas.isEmpty() -> HeaterCommand.NONE
    areas.any { it.isAboveRange() } -> HeaterCommand.OFF
    else -> {
        val demandRatio = areas.count { it.requiresHeating() }.toBigDecimal()
            .divide(areas.size.toBigDecimal(), 2, RoundingMode.HALF_UP)
        if (demandRatio >= threshold.value) HeaterCommand.ON else HeaterCommand.OFF
    }
}

private fun HeatableAreaSnapshot.isAboveRange(): Boolean {
    val current = currentTemperature ?: return false
    val target = targetTemperature ?: return false
    return current > target + HYSTERESIS
}

private fun HeatableAreaSnapshot.requiresHeating(): Boolean {
    val current = currentTemperature ?: return false
    val target = targetTemperature ?: return false
    return when {
        current > target + HYSTERESIS -> false
        current < target - HYSTERESIS -> true
        else -> when (heaterStatus) { // in-band
            ActuatorStatus.ON -> true
            ActuatorStatus.OFF -> false
            ActuatorStatus.UNDEFINED -> current <= target
        }
    }
}
