package org.agrfesta.sh.api.schedulers

import org.agrfesta.sh.api.core.application.ports.inbounds.PruneNotificationsUseCase
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Thin driving adapter (cron → use case, #194): once a day prunes the notifications older than the
 * configured retention window (`alerts.notifications.retention`).
 */
@Component
class NotificationsRetentionScheduler(
    private val pruneNotifications: PruneNotificationsUseCase
) {
    private val logger by LoggerDelegate()

    @Scheduled(cron = "0 0 4 * * ?")
    @Async
    fun pruneOldNotifications() {
        logger.info("[SCHEDULED TASK] start notifications retention prune...")
        pruneNotifications.execute()
        logger.info("[SCHEDULED TASK] end notifications retention prune")
    }
}
