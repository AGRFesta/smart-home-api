package org.agrfesta.sh.api.services.heating

import org.agrfesta.sh.api.domain.commons.SharedHeaterContext
import org.agrfesta.sh.api.domain.failures.PersistedCacheEntryNotFound
import org.agrfesta.sh.api.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.services.PersistedCacheService
import org.agrfesta.sh.api.services.heating.DynamicSharedHeatingStrategyService.Companion.HEATING_STRATEGY_KEY
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/**
 * Implementation of [SharedHeatingAreasStrategyService] that dynamically delegates to a specific strategy
 * based on the configuration stored in the persisted cache.
 *
 * It retrieves the active strategy name from the cache key defined by [HEATING_STRATEGY_KEY].
 * If the key is missing, the value is invalid, or a persistence error occurs, it falls back to
 * the configured `defaultStrategy` to ensure the system remains operational.
 */
@Service
class DynamicSharedHeatingStrategyService(
    @param:Value("\${heating.default-strategy:ECONOMY}") private val defaultStrategy: SharedHeatingAreasStrategy,
    strategyServices: Collection<NamedSharedHeatingAreasStrategyService>,
    private val persistedCacheService: PersistedCacheService
): SharedHeatingAreasStrategyService {
    private val logger by LoggerDelegate()
    private val servicesByStrategy: Map<SharedHeatingAreasStrategy, NamedSharedHeatingAreasStrategyService> =
        strategyServices.associateBy { it.strategy }

    companion object {
        /**
         * The cache key used to store the name of the active heating strategy.
         */
        const val HEATING_STRATEGY_KEY = "heating.strategy"
    }

    override suspend fun handleHeatingFor(context: SharedHeaterContext) {
        val strategy = getStrategy()
        val service = when {
            servicesByStrategy.containsKey(strategy) -> servicesByStrategy[strategy]
            servicesByStrategy.containsKey(defaultStrategy) -> {
                logger.error("Selected strategy '${strategy.name}' is missing. " +
                        "Falling back to default: '${defaultStrategy.name}'")
                servicesByStrategy[defaultStrategy]
            }
            else -> {
                logger.error("Default strategy '${defaultStrategy.name}' is also missing. " +
                        "Skipping heating control for heater ${context.heater.uuid}")
                null
            }
        }
        service?.handleHeatingFor(context)
    }

    private fun getStrategy(): SharedHeatingAreasStrategy = persistedCacheService.getEntry(HEATING_STRATEGY_KEY).fold(
        ifLeft = {
            when(it) {
                is PersistedCacheEntryNotFound ->
                    logger.error("$HEATING_STRATEGY_KEY cache entry is missing. " +
                            "Falling back to ${defaultStrategy.name}")
                is PersistenceFailure ->
                    logger.error("Unable to fetch $HEATING_STRATEGY_KEY cache entry. " +
                            "Falling back to ${defaultStrategy.name}", it.exception)
            }
            defaultStrategy
        },
        ifRight = { it.value.toSharedHeatingAreasStrategy() }
    )

    private fun String.toSharedHeatingAreasStrategy() = try {
        SharedHeatingAreasStrategy.valueOf(this.uppercase())
    } catch (e: IllegalArgumentException) {
        logger.error("Value $this is not a valid HeatingStrategy. Falling back to ${defaultStrategy.name}", e)
        defaultStrategy
    }

}
