package org.agrfesta.sh.api.schedulers

import java.math.BigDecimal
import kotlinx.coroutines.runBlocking
import org.agrfesta.sh.api.domain.areas.HeatableArea
import org.agrfesta.sh.api.domain.devices.Heater
import org.agrfesta.sh.api.services.AreasService
import org.agrfesta.sh.api.services.DevicesService
import org.agrfesta.sh.api.services.heating.SharedHeatingAreasStrategy
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Scheduler component responsible for triggering the heating control logic periodically.
 *
 * This component fetches all devices and heatable areas, groups areas by their shared heater,
 * and delegates the decision-making process to the injected [SharedHeatingAreasStrategy].
 */
@ConditionalOnProperty(name = ["heating.enabled"], havingValue = "true")
@Component
class HeatingControlScheduler(
    private val devicesService: DevicesService,
    private val areasService: AreasService,
    private val strategy: SharedHeatingAreasStrategy
) {
    private val logger by LoggerDelegate()

    companion object {
        /**
         * The global temperature hysteresis (1.0 degree) used to prevent rapid toggling of heaters.
         */
        val HYSTERESIS: BigDecimal = BigDecimal.ONE
    }

    /**
     * Scheduled task that runs every 15 minutes to evaluate heating requirements.
     *
     * It retrieves the current state of devices and areas, groups them by heater,
     * and executes the heating strategy for each group asynchronously.
     */
    @Scheduled(cron = "0 */15 * * * *")
    @Async
    fun scheduledTask() {
        logger.info("[SCHEDULED TASK] start heating control...")
        devicesService.getAllDevices()
            .onRight {
                val heaters = it.filterIsInstance<Heater>()
                logger.info("Found ${heaters.size} Heaters")
                val devicesRegistry = it.associateBy { d -> d.uuid }
                areasService.getAllAreas(devicesRegistry).filterIsInstance<HeatableArea>()
                    .also { areas -> logger.info("Found ${areas.size} HeatableArea") }
                    .groupBy { a -> a.heater }
                    .forEach { (heater, areas) -> runBlocking {
                        strategy.handleHeatingFor(heater, areas) }
                    }
            }
            .onLeft {
                failure -> logger.error("Device fetch failed, skipping heating control task.", failure.exception)
            }
        logger.info("[SCHEDULED TASK] end heating control")
    }

}
