package org.agrfesta.sh.api.core.application.usecases

import kotlinx.coroutines.runBlocking
import org.agrfesta.sh.api.core.application.ports.inbounds.EvaluateHeatingStateUseCase
import org.agrfesta.sh.api.core.application.ports.outbounds.settings.PropertyRepository
import org.agrfesta.sh.api.core.domain.areas.HeatableArea
import org.agrfesta.sh.api.core.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.services.AreasService
import org.agrfesta.sh.api.services.DevicesService
import org.agrfesta.sh.api.services.heating.SharedHeatingAreasStrategyService
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.springframework.stereotype.Service

@Service
class EvaluateHeatingStateService(
    private val devicesService: DevicesService,
    private val areasService: AreasService,
    private val strategy: SharedHeatingAreasStrategyService,
    private val propertyRepository: PropertyRepository
) : EvaluateHeatingStateUseCase {

    private val logger by LoggerDelegate()

    companion object {
        const val HEATING_ENABLED_KEY = "heating.enabled"
    }

    override fun execute() {
        if (!isEnabled()) return
        val devicesRegistry = devicesService.getAllDevices().onLeft { failure ->
            logger.error("Device fetch failed, skipping heating evaluation.", failure.exception)
        }.getOrNull()?.associateBy { it.uuid } ?: return
        val areas = areasService.getAllAreas(devicesRegistry).onLeft { failure ->
            logger.error("Area fetch failed, skipping heating evaluation.", failure.exception)
        }.getOrNull() ?: return
        runBlocking {
            areas.filterIsInstance<HeatableArea>()
                .groupBy { it.heater }
                .forEach { (heater, areaList) ->
                    strategy.handleHeatingFor(heater, areaList)
                }
        }
    }

    private fun isEnabled(): Boolean = propertyRepository.findEntry(HEATING_ENABLED_KEY).fold(
        ifLeft = {
            when (it) {
                is PersistenceFailure ->
                    logger.error("Failed to fetch $HEATING_ENABLED_KEY. Considered disabled.", it.exception)
            }
            false
        },
        ifRight = { it?.value?.equals("true", ignoreCase = true) ?: false }
    )

}
