package org.agrfesta.sh.api.core.domain.alerts

/**
 * The observable outcome of evaluating an alert condition against its current persisted state.
 *
 * It carries the resulting alert state so that the caller (the evaluator) only has to
 * project it onto storage: persist on [Opened], update on [Resolved], do nothing on [Unchanged].
 */
sealed interface AlertTransition {

    /** The condition became true while no alert was open: [alert] is a fresh OPEN alert to persist. */
    data class Opened(val alert: Alert) : AlertTransition

    /** The condition cleared while an alert was open: [alert] is that alert, now RESOLVED. */
    data class Resolved(val alert: Alert) : AlertTransition

    /** Idempotent no-op: storage must stay unchanged. */
    data object Unchanged : AlertTransition
}
