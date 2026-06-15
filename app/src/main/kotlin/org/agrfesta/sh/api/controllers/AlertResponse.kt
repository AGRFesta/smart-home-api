package org.agrfesta.sh.api.controllers

import org.agrfesta.sh.api.core.domain.alerts.Alert
import org.agrfesta.sh.api.core.domain.alerts.AlertLifecycle
import org.agrfesta.sh.api.core.domain.alerts.AlertScope
import org.agrfesta.sh.api.core.domain.alerts.AlertStatus
import org.agrfesta.sh.api.core.domain.alerts.AlertType
import java.time.Instant
import java.util.UUID

data class AlertResponse(
    val uuid: UUID,
    val type: AlertType,
    val scope: AlertScope,
    val target: String?,
    val status: AlertStatus,
    val openedAt: Instant,
    val resolvedAt: Instant?,
    val details: String?
)

fun Alert.toResponse() = AlertResponse(
    uuid = uuid,
    type = type,
    scope = target.scope,
    target = target.reference,
    status = lifecycle.status,
    openedAt = openedAt,
    resolvedAt = (lifecycle as? AlertLifecycle.Resolved)?.resolvedAt,
    details = details
)
