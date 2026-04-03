package org.agrfesta.sh.api.schedulers

import org.agrfesta.sh.api.services.SensorHistorySnapshotService
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class DevicesDataHistoryScheduler(
    private val sensorHistorySnapshotService: SensorHistorySnapshotService
) {
    private val logger by LoggerDelegate()

    @Scheduled(cron = "0 */15 * * * *")
    @Async
    fun historyDevicesData() {
        logger.info("[SCHEDULED TASK] start devices history data...")
        sensorHistorySnapshotService.snapshotDevicesData()
        logger.info("[SCHEDULED TASK] end devices history data")
    }

}
