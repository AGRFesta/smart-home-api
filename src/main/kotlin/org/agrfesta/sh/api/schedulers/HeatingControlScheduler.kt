package org.agrfesta.sh.api.schedulers

import org.agrfesta.sh.api.utils.LoggerDelegate
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class HeatingControlScheduler {
    private val logger by LoggerDelegate()

    @Scheduled(cron = "0 */15 * * * *")
    @Async
    fun scheduledTask() {
        logger.info("[SCHEDULED TASK] start heating control...")

        logger.info("[SCHEDULED TASK] end heating control")
    }

}
