package org.agrfesta.sh.api.core.domain.alerts

/**
 * Describes the alertable condition currently being evaluated: *what* the alert is about and the
 * payload to attach when it opens. Used to materialise a new [Alert] in an [AlertTransition.Opened].
 *
 * @property details a small, free-form payload describing what tripped the condition (e.g. the offending value).
 */
data class AlertSubject(
    val type: AlertType,
    val target: AlertTarget,
    val details: String? = null
)
