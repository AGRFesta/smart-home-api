package org.agrfesta.sh.api.core.domain.alerts

/**
 * Lifecycle status of an [Alert].
 *
 * An alert opens when its condition first becomes true and stays [OPEN] while it persists; it becomes
 * [RESOLVED] once the condition clears. There is at most one [OPEN] alert per `(type, target)`.
 */
enum class AlertStatus {
    OPEN,
    RESOLVED
}
