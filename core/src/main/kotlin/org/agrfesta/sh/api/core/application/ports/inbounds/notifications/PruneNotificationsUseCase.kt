package org.agrfesta.sh.api.core.application.ports.inbounds.notifications

/**
 * Inbound Port: deletes the persisted notifications older than the configured retention window
 * (`alerts.notifications.retention`), so the notification table does not grow without bound.
 *
 * Driven by a scheduler. Deliberately returns [Unit]: like `SendAlertRemindersUseCase`, a failed prune
 * is a tolerated degradation on a scheduled path — the next run retries.
 */
interface PruneNotificationsUseCase {
    fun execute()
}
