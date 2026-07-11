package org.agrfesta.sh.api.core.application.usecases

import arrow.core.getOrElse
import org.agrfesta.sh.api.core.application.ports.inbounds.SendAlertRemindersUseCase
import org.agrfesta.sh.api.core.application.ports.outbounds.RandomGenerator
import org.agrfesta.sh.api.core.application.ports.outbounds.TimeProvider
import org.agrfesta.sh.api.core.application.ports.outbounds.alerts.AlertsRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.notifications.NotificationDispatcher
import org.agrfesta.sh.api.core.domain.alerts.AlertStatus
import org.agrfesta.sh.api.core.domain.notifications.Notification
import org.agrfesta.sh.api.core.domain.notifications.NotificationEvent
import org.agrfesta.sh.api.core.domain.notifications.ReminderPolicy
import org.agrfesta.sh.api.core.domain.notifications.reminderDue
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.springframework.stereotype.Service

@Service
class SendAlertRemindersService(
    private val alertsRepository: AlertsRepository,
    private val notificationDispatcher: NotificationDispatcher,
    private val reminderPolicy: ReminderPolicy,
    private val randomGenerator: RandomGenerator,
    private val timeProvider: TimeProvider
) : SendAlertRemindersUseCase {

    private val logger by LoggerDelegate()

    override fun execute() {
        val openAlerts = alertsRepository.getAlerts(AlertStatus.OPEN)
            .onLeft { failure ->
                logger.warn("Skipping reminder scan: unable to read the open alerts: $failure")
            }
            .getOrElse { return }
        val now = timeProvider.now()
        openAlerts
            .filter { reminderDue(lastNotifiedAt = it.lastNotifiedAt, now = now, cadence = reminderPolicy.cadence) }
            .forEach { alert ->
                notificationDispatcher.dispatch(
                    Notification(
                        uuid = randomGenerator.uuid(),
                        alertUuid = alert.uuid,
                        event = NotificationEvent.REMINDER,
                        sentAt = now,
                        payload = alert.details
                    )
                )
                    .onRight {
                        alertsRepository.updateLastNotifiedAt(alert.uuid, now)
                            .onLeft { failure ->
                                logger.warn(
                                    "Failed to re-arm last_notified_at for alert '${alert.uuid}': $failure"
                                )
                            }
                    }
                    .onLeft { failure ->
                        logger.warn("Failed to dispatch REMINDER notification for alert '${alert.uuid}': $failure")
                    }
            }
    }
}
