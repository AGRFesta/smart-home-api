package org.agrfesta.sh.api.schedulers

import org.agrfesta.sh.api.core.application.ports.inbounds.notifications.SendAlertRemindersUseCase
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Thin driving adapter (cron → use case, #194): scans every 15 minutes, but the actual reminder
 * cadence is enforced by the use case via `last_notified_at` and `alerts.notifications.reminder-interval`
 * — the scan frequency only bounds the notification latency.
 */
@Component
class AlertRemindersScheduler(
    private val sendAlertReminders: SendAlertRemindersUseCase
) {
    private val logger by LoggerDelegate()

    @Scheduled(cron = "0 */15 * * * ?")
    @Async
    fun sendReminders() {
        logger.info("[SCHEDULED TASK] start alert reminders scan...")
        sendAlertReminders.execute()
        logger.info("[SCHEDULED TASK] end alert reminders scan")
    }
}
