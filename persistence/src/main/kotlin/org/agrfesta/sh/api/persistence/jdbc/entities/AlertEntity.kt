package org.agrfesta.sh.api.persistence.jdbc.entities

import org.agrfesta.sh.api.core.domain.alerts.Alert
import org.agrfesta.sh.api.core.domain.alerts.AlertLifecycle
import org.agrfesta.sh.api.core.domain.alerts.AlertScope
import org.agrfesta.sh.api.core.domain.alerts.AlertStatus
import org.agrfesta.sh.api.core.domain.alerts.AlertTarget
import org.agrfesta.sh.api.core.domain.alerts.AlertType
import java.time.Instant
import java.util.UUID

@Suppress("LongParameterList")
class AlertEntity(
    val uuid: UUID,
    val type: AlertType,
    val scope: AlertScope,
    val target: String?,
    val status: AlertStatus,
    val openedAt: Instant,
    val resolvedAt: Instant?,
    val details: String?
) {
    fun toAlert() = Alert(
        uuid = uuid,
        type = type,
        target = AlertTarget.of(scope, target),
        openedAt = openedAt,
        lifecycle = when (status) {
            AlertStatus.OPEN -> AlertLifecycle.Open
            AlertStatus.RESOLVED -> AlertLifecycle.Resolved(
                requireNotNull(resolvedAt) { "RESOLVED alert '$uuid' requires resolvedAt" }
            )
        },
        details = details
    )
}
