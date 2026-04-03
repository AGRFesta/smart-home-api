package org.agrfesta.sh.api.schedulers

import kotlinx.coroutines.runBlocking
import org.agrfesta.sh.api.services.SensorReadingsSyncService
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class DevicesDataFetchScheduler(
    private val syncService: SensorReadingsSyncService
) {
    private val logger by LoggerDelegate()

    @Scheduled(cron = "0 * * * * ?")
    @Async
    fun fetchDevicesData() {
        logger.info("[SCHEDULED TASK] start fetching devices data...")
        runBlocking { syncService.fetchAndCacheSensorData() }
        logger.info("[SCHEDULED TASK] end fetching devices data")
    }

}
