package org.agrfesta.sh.api.core.application.usecases.heating

import org.agrfesta.sh.api.core.application.ports.outbounds.settings.PropertyRepository
import org.agrfesta.sh.api.core.domain.commons.Percentage
import org.agrfesta.sh.api.core.domain.failures.PropertyNotFound
import org.agrfesta.sh.api.core.domain.failures.PropertyRepositoryError
import org.agrfesta.sh.api.core.domain.heating.HeatingDecider
import org.agrfesta.sh.api.core.domain.heating.SharedHeatingStrategy
import org.agrfesta.sh.api.core.domain.heating.SharedHeatingStrategy.COMFORT
import org.agrfesta.sh.api.core.domain.heating.SharedHeatingStrategy.ECONOMY
import org.agrfesta.sh.api.core.domain.heating.decideComfort
import org.agrfesta.sh.api.core.domain.heating.decideEconomy
import org.agrfesta.sh.api.utils.LoggerDelegate
import java.util.Locale

/**
 * Thin selector that resolves the active [SharedHeatingStrategy] from the property store and exposes
 * it as a pure [HeatingDecider].
 *
 * It reads the strategy name from the [HEATING_STRATEGY_KEY] property; when the key is missing, the
 * value is invalid, or a persistence error occurs, it falls back to [defaultStrategy] so the system
 * remains operational. The selector performs no heating I/O: it only chooses which pure decision to apply.
 */
class HeatingStrategySelector(
    private val defaultStrategy: SharedHeatingStrategy,
    private val economyThreshold: Percentage,
    private val propertyRepository: PropertyRepository
) {
    private val logger by LoggerDelegate()

    companion object {
        /**
         * The property key used to store the name of the active heating strategy.
         */
        const val HEATING_STRATEGY_KEY = "heating.strategy"
    }

    fun select(): HeatingDecider = when (resolveStrategy()) {
        COMFORT -> ::decideComfort
        ECONOMY -> { areas -> decideEconomy(areas, economyThreshold) }
    }

    private fun resolveStrategy(): SharedHeatingStrategy =
        propertyRepository.getEntry(HEATING_STRATEGY_KEY).fold(
            ifLeft = {
                when (it) {
                    is PropertyNotFound ->
                        logger.error("$HEATING_STRATEGY_KEY property is missing. Falling back to default.")
                    PropertyRepositoryError ->
                        logger.error("Unable to fetch $HEATING_STRATEGY_KEY property. Falling back to default.")
                }
                defaultStrategy
            },
            ifRight = { it.value.toSharedHeatingStrategyOrDefault() }
        )

    private fun String.toSharedHeatingStrategyOrDefault(): SharedHeatingStrategy = try {
        SharedHeatingStrategy.valueOf(trim().uppercase(Locale.ROOT))
    } catch (_: IllegalArgumentException) {
        logger.error("'$this' is not a valid heating strategy. Falling back to ${defaultStrategy.name}")
        defaultStrategy
    }
}
