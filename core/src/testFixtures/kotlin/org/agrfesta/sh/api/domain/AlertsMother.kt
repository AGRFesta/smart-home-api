package org.agrfesta.sh.api.domain

import org.agrfesta.sh.api.core.domain.alerts.Alert
import org.agrfesta.sh.api.core.domain.alerts.AlertLifecycle
import org.agrfesta.sh.api.core.domain.alerts.AlertSubject
import org.agrfesta.sh.api.core.domain.alerts.AlertTarget
import org.agrfesta.sh.api.core.domain.alerts.AlertType
import java.time.Instant
import java.util.UUID

fun anAlert(
    uuid: UUID = UUID.randomUUID(),
    type: AlertType = AlertType.BATTERY_LOW,
    target: AlertTarget = AlertTarget.Device(UUID.randomUUID()),
    openedAt: Instant = Instant.now(),
    lifecycle: AlertLifecycle = AlertLifecycle.Open,
    details: String? = null
) = Alert(uuid, type, target, openedAt, lifecycle, details)

fun anAlertSubject(
    type: AlertType = AlertType.BATTERY_LOW,
    target: AlertTarget = AlertTarget.Device(UUID.randomUUID()),
    details: String? = null
) = AlertSubject(type, target, details)
