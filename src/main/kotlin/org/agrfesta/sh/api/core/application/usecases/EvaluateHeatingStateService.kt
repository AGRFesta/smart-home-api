package org.agrfesta.sh.api.core.application.usecases

import kotlinx.coroutines.runBlocking
import org.agrfesta.sh.api.core.application.ports.inbounds.EvaluateHeatingStateUseCase
import org.agrfesta.sh.api.core.application.ports.outbounds.TimeProvider
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.AreasWithDevicesRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicesRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.ProviderDevicesFactory
import org.agrfesta.sh.api.core.application.ports.outbounds.settings.PropertyRepository
import org.agrfesta.sh.api.core.application.areas.AreasFactory
import org.agrfesta.sh.api.core.domain.areas.HeatableArea
import org.agrfesta.sh.api.core.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.core.application.usecases.heating.SharedHeatingAreasStrategyService
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.springframework.stereotype.Service

@Service
class EvaluateHeatingStateService(
    private val devicesRepository: DevicesRepository,
    providerDevicesFactories: Collection<ProviderDevicesFactory>,
    private val areasWithDevicesRepository: AreasWithDevicesRepository,
    private val areasFactory: AreasFactory,
    private val strategy: SharedHeatingAreasStrategyService,
    private val propertyRepository: PropertyRepository,
    private val timeProvider: TimeProvider
) : EvaluateHeatingStateUseCase {

    private val logger by LoggerDelegate()
    private val mappedDevicesFactories = providerDevicesFactories.associateBy { it.provider }

    companion object {
        const val HEATING_ENABLED_KEY = "heating.enabled"
    }

    override fun execute() {
        if (!isEnabled()) return
        val deviceRecords = devicesRepository.getAll().onLeft { failure ->
            logger.error("Device fetch failed, skipping heating evaluation.", failure.exception)
        }.getOrNull() ?: return
        val devicesRegistry = deviceRecords.mapNotNull { record ->
            val factory = mappedDevicesFactories[record.provider]
            if (factory == null) {
                logger.error("No ProviderDevicesFactory registered for provider '${record.provider}', skipping device '${record.uuid}'")
                null
            } else {
                factory.createDevice(record)
            }
        }.associateBy { it.uuid }
        val areas = areasWithDevicesRepository.getAllAreasWithDevices().onLeft { failure ->
            logger.error("Area fetch failed, skipping heating evaluation.", failure.exception)
        }.getOrNull()?.map { areasFactory.createArea(it, devicesRegistry) } ?: return
        val currentTime = timeProvider.currentLocalTime()
        runBlocking {
            areas.filterIsInstance<HeatableArea>()
                .groupBy { it.heater }
                .forEach { (heater, areaList) ->
                    strategy.handleHeatingFor(heater, areaList, currentTime)
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
