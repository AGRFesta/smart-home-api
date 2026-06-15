package org.agrfesta.sh.api.core.domain.alerts

import java.time.Instant
import java.util.UUID

/**
 * Stateful, idempotent alert aggregate and source of truth for an alert condition.
 *
 * It is the only piece of alert state that must survive a restart: notifications are a projection of it.
 * Lifecycle transitions are computed by [evaluateAlert]; timestamps are supplied by the caller (via
 * `TimeProvider`), never read from the clock here, to keep the aggregate pure and testable.
 *
 * Illegal states are unrepresentable by construction: [target] couples scope and reference, and
 * [lifecycle] couples status and `resolvedAt`.
 *
 * @property details a small, free-form payload describing what tripped the alert (e.g. the offending value).
 */
data class Alert(
    val uuid: UUID,
    val type: AlertType,
    val target: AlertTarget,
    val openedAt: Instant,
    val lifecycle: AlertLifecycle,
    val details: String? = null
)
