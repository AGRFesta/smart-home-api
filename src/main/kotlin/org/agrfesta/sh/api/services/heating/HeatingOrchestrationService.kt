package org.agrfesta.sh.api.services.heating

import java.math.BigDecimal
import kotlinx.coroutines.runBlocking
import org.agrfesta.sh.api.core.domain.areas.HeatableArea
import org.agrfesta.sh.api.core.domain.commons.Temperature
import org.agrfesta.sh.api.core.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.core.application.ports.outbounds.settings.PropertyRepository
import org.agrfesta.sh.api.services.AreasService
import org.agrfesta.sh.api.services.DevicesService
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.springframework.stereotype.Service

@Service
class HeatingOrchestrationService(
    private val devicesService: DevicesService,
    private val areasService: AreasService,
    private val strategy: DynamicSharedHeatingStrategyService,
    private val propertyRepository: PropertyRepository
) {
    private val logger by LoggerDelegate()

    companion object {
        const val HEATING_ENABLED_KEY = "heating.enabled"
        val HYSTERESIS: Temperature = Temperature.of(BigDecimal.ONE)
    }

    fun evaluateHeatingState() {
        if (!isEnabled()) return
        devicesService.getAllDevices()
            .onRight { devices ->
                val devicesRegistry = devices.associateBy { it.uuid }
                areasService.getAllAreas(devicesRegistry)
                    .onRight { areas ->
                        runBlocking {
                            areas.filterIsInstance<HeatableArea>()
                                .groupBy { it.heater }
                                .forEach { (heater, areaList) ->
                                    strategy.handleHeatingFor(heater, areaList)
                                }
                        }
                    }
                    .onLeft { failure ->
                        logger.error("Area fetch failed, skipping heating evaluation.", failure.exception)
                    }
            }
            .onLeft { failure ->
                logger.error("Device fetch failed, skipping heating evaluation.", failure.exception)
            }
    }

    private fun isEnabled(): Boolean = propertyRepository.findEntry(HEATING_ENABLED_KEY).fold(
        ifLeft = {
            when (it) {
                is PersistenceFailure ->
                    logger.error("Failed to fetch $HEATING_ENABLED_KEY cache entry. Considered disabled", it.exception)
            }
            false
        },
        ifRight = { it?.value?.equals("true", ignoreCase = true) ?: false }
    )

}
