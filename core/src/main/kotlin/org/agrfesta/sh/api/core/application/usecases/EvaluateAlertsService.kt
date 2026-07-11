package org.agrfesta.sh.api.core.application.usecases

import arrow.core.Either
import arrow.core.getOrElse
import org.agrfesta.sh.api.core.application.ports.inbounds.EvaluateAlertsUseCase
import org.agrfesta.sh.api.core.application.ports.outbounds.RandomGenerator
import org.agrfesta.sh.api.core.application.ports.outbounds.TimeProvider
import org.agrfesta.sh.api.core.application.ports.outbounds.alerts.AlertsRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DeviceBatteryRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.notifications.NotificationDispatcher
import org.agrfesta.sh.api.core.domain.alerts.Alert
import org.agrfesta.sh.api.core.domain.alerts.AlertStatus
import org.agrfesta.sh.api.core.domain.alerts.AlertSubject
import org.agrfesta.sh.api.core.domain.alerts.AlertTarget
import org.agrfesta.sh.api.core.domain.alerts.AlertTransition
import org.agrfesta.sh.api.core.domain.alerts.AlertType
import org.agrfesta.sh.api.core.domain.alerts.BatteryLowRule
import org.agrfesta.sh.api.core.domain.alerts.evaluateAlert
import org.agrfesta.sh.api.core.domain.devices.Device
import org.agrfesta.sh.api.core.domain.failures.NotificationDispatchFailure
import org.agrfesta.sh.api.core.domain.notifications.Notification
import org.agrfesta.sh.api.core.domain.notifications.NotificationEvent
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class EvaluateAlertsService(
    private val alertsRepository: AlertsRepository,
    private val deviceBatteryRepository: DeviceBatteryRepository,
    private val batteryLowRule: BatteryLowRule,
    private val notificationDispatcher: NotificationDispatcher,
    private val randomGenerator: RandomGenerator,
    private val timeProvider: TimeProvider
) : EvaluateAlertsUseCase {

    private val logger by LoggerDelegate()

    override fun execute(devices: Collection<Device>) {
        val openAlerts = alertsRepository.getAlerts(AlertStatus.OPEN)
            .onLeft { failure ->
                logger.warn("Skipping alert evaluation: unable to read the open alerts: $failure")
            }
            .getOrElse { return }
        devices.forEach { device -> evaluateBatteryLow(device, openAlerts) }
    }

    private fun evaluateBatteryLow(device: Device, openAlerts: Collection<Alert>) {
        val level = deviceBatteryRepository.findBy(device)
            .onLeft { failure ->
                logger.warn("Failed to read cached battery for device ${device.uuid}: $failure")
            }
            .getOrElse { return }
            ?: return // skip-on-absent: no cached value ⇒ not evaluable, never "condition cleared"
        val target = AlertTarget.Device(device.uuid)
        val current = openAlerts.firstOrNull { it.type == AlertType.BATTERY_LOW && it.target == target }
        val transition = evaluateAlert(
            current = current,
            conditionMet = batteryLowRule.conditionMet(level = level, alertOpen = current != null),
            subject = AlertSubject(type = AlertType.BATTERY_LOW, target = target, details = "battery=$level%"),
            newId = randomGenerator.uuid(),
            at = timeProvider.now()
        )
        when (transition) {
            is AlertTransition.Opened -> alertsRepository.create(transition.alert)
                .onRight { notifyOpened(transition.alert) }
                .onLeft { failure -> logger.warn("Failed to open alert for device ${device.uuid}: $failure") }
            is AlertTransition.Resolved -> alertsRepository.resolve(transition.alert)
                .onRight {
                    dispatch(transition.alert, NotificationEvent.RESOLVED, at = timeProvider.now())
                        .onLeft { failure ->
                            logger.warn(
                                "Failed to dispatch RESOLVED notification for alert " +
                                    "'${transition.alert.uuid}': $failure"
                            )
                        }
                }
                .onLeft { failure -> logger.warn("Failed to resolve alert '${transition.alert.uuid}': $failure") }
            AlertTransition.Unchanged -> Unit
        }
    }

    /** Emits the `opened` notification and, only if delivered, tracks `last_notified_at`. */
    private fun notifyOpened(alert: Alert) {
        val now = timeProvider.now() // captured once: sentAt and last_notified_at document the same emission
        dispatch(alert, NotificationEvent.OPENED, at = now)
            .onRight {
                alertsRepository.updateLastNotifiedAt(alert.uuid, now)
                    .onLeft { failure ->
                        logger.warn("Failed to track last_notified_at for alert '${alert.uuid}': $failure")
                    }
            }
            .onLeft { failure ->
                logger.warn("Failed to dispatch OPENED notification for alert '${alert.uuid}': $failure")
            }
    }

    private fun dispatch(
        alert: Alert,
        event: NotificationEvent,
        at: Instant
    ): Either<NotificationDispatchFailure, Unit> =
        notificationDispatcher.dispatch(
            Notification(
                uuid = randomGenerator.uuid(),
                alertUuid = alert.uuid,
                event = event,
                sentAt = at,
                payload = alert.details
            )
        )
}
