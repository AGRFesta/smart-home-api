package org.agrfesta.sh.api.core.application.usecases

import arrow.core.getOrElse
import org.agrfesta.sh.api.core.application.ports.inbounds.EvaluateAlertsUseCase
import org.agrfesta.sh.api.core.application.ports.outbounds.RandomGenerator
import org.agrfesta.sh.api.core.application.ports.outbounds.TimeProvider
import org.agrfesta.sh.api.core.application.ports.outbounds.alerts.AlertsRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DeviceBatteryRepository
import org.agrfesta.sh.api.core.domain.alerts.Alert
import org.agrfesta.sh.api.core.domain.alerts.AlertStatus
import org.agrfesta.sh.api.core.domain.alerts.AlertSubject
import org.agrfesta.sh.api.core.domain.alerts.AlertTarget
import org.agrfesta.sh.api.core.domain.alerts.AlertTransition
import org.agrfesta.sh.api.core.domain.alerts.AlertType
import org.agrfesta.sh.api.core.domain.alerts.BatteryLowRule
import org.agrfesta.sh.api.core.domain.alerts.evaluateAlert
import org.agrfesta.sh.api.core.domain.devices.Device
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.springframework.stereotype.Service

@Service
class EvaluateAlertsService(
    private val alertsRepository: AlertsRepository,
    private val deviceBatteryRepository: DeviceBatteryRepository,
    private val batteryLowRule: BatteryLowRule,
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
                .onLeft { failure -> logger.warn("Failed to open alert for device ${device.uuid}: $failure") }
            is AlertTransition.Resolved -> alertsRepository.resolve(transition.alert)
                .onLeft { failure -> logger.warn("Failed to resolve alert '${transition.alert.uuid}': $failure") }
            AlertTransition.Unchanged -> Unit
        }
    }
}
