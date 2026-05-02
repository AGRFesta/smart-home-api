package org.agrfesta.sh.api.schedulers

import org.agrfesta.sh.api.core.application.ports.inbounds.FetchSensorReadingsUseCase
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class DevicesDataFetchScheduler(
    private val fetchSensorReadings: FetchSensorReadingsUseCase
) {
    private val logger by LoggerDelegate()

    @Scheduled(cron = "0 * * * * ?")
    @Async
    fun fetchDevicesData() {
        logger.info("[SCHEDULED TASK] start fetching devices data...")
        fetchSensorReadings.execute()
        logger.info("[SCHEDULED TASK] end fetching devices data")
    }

}
