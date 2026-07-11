package org.agrfesta.sh.api.core.domain.notifications

/**
 * The alert transition a notification is a projection of.
 *
 * - [OPENED] — emitted once, the first time the alert opens.
 * - [REMINDER] — re-emitted periodically while the alert stays OPEN, at the configured cadence.
 * - [RESOLVED] — emitted when the alert resolves.
 */
enum class NotificationEvent {
    OPENED, REMINDER, RESOLVED
}
