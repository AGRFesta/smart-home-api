package org.agrfesta.sh.api.schedulers

import org.agrfesta.sh.api.core.application.ports.inbounds.SnapshotSensorHistoryUseCase
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class DevicesDataHistoryScheduler(
    private val snapshotSensorHistory: SnapshotSensorHistoryUseCase
) {
    private val logger by LoggerDelegate()

    @Scheduled(cron = "0 */15 * * * *")
    @Async
    fun historyDevicesData() {
        logger.info("[SCHEDULED TASK] start devices history data...")
        snapshotSensorHistory.execute()
            .onLeft { logger.error("[SCHEDULED TASK] snapshot sensor history failed: $it") }
        logger.info("[SCHEDULED TASK] end devices history data")
    }

}
