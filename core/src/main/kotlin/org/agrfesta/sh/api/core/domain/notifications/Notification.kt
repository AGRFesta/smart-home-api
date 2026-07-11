package org.agrfesta.sh.api.core.domain.notifications

import java.time.Instant
import java.util.UUID

/**
 * Ephemeral delivery projection of an alert transition — not domain state: the alert remains the source
 * of truth (see `docs/domain/ALERTS.md`).
 *
 * Timestamps are supplied by the caller (via `TimeProvider`), never read from the clock here.
 *
 * @property alertUuid the alert this notification projects.
 * @property payload a small free-form snapshot describing the alert condition (e.g. the alert details).
 */
data class Notification(
    val uuid: UUID,
    val alertUuid: UUID,
    val event: NotificationEvent,
    val sentAt: Instant,
    val payload: String? = null
)
