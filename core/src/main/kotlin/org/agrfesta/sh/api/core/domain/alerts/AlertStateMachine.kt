package org.agrfesta.sh.api.core.domain.alerts

import java.time.Instant
import java.util.UUID

/**
 * Pure, idempotent alert lifecycle.
 *
 * The model is deliberately generic: it knows nothing about thresholds or hysteresis. A rule maps its
 * own logic (boolean condition, or a two-threshold band resolved with stickiness) into [conditionMet];
 * the aggregate only reacts to the resulting boolean and reports the [AlertTransition] to apply.
 *
 * @param current the currently OPEN alert for the evaluated subject, or `null` if none is open.
 * @param conditionMet whether the condition is currently true.
 * @param subject what the alert is about; used to materialise a new alert when opening.
 * @param newId the identifier to assign to a freshly opened alert.
 * @param at the instant to stamp on an open/resolve transition.
 */
@Suppress("LongParameterList")
fun evaluateAlert(
    current: Alert?,
    conditionMet: Boolean,
    subject: AlertSubject,
    newId: UUID,
    at: Instant
): AlertTransition = when {
    current == null && conditionMet -> AlertTransition.Opened(openAlert(newId, subject, at))
    current != null && !conditionMet ->
        AlertTransition.Resolved(current.copy(lifecycle = AlertLifecycle.Resolved(at)))
    else -> AlertTransition.Unchanged
}

private fun openAlert(newId: UUID, subject: AlertSubject, at: Instant): Alert = Alert(
    uuid = newId,
    type = subject.type,
    target = subject.target,
    openedAt = at,
    lifecycle = AlertLifecycle.Open,
    details = subject.details
)
