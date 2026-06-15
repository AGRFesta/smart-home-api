package org.agrfesta.sh.api.core.domain.alerts

import java.time.Instant

/**
 * The lifecycle state of an [Alert]. Modelled as a sealed type so that `resolvedAt` exists **only** when the
 * alert is resolved: an `OPEN` alert cannot carry a resolution instant, nor a `RESOLVED` one lack it.
 */
sealed interface AlertLifecycle {

    /** The corresponding [AlertStatus] (derived). */
    val status: AlertStatus

    /** The condition is currently true. */
    data object Open : AlertLifecycle {
        override val status: AlertStatus get() = AlertStatus.OPEN
    }

    /** The condition has cleared at [resolvedAt]. */
    data class Resolved(val resolvedAt: Instant) : AlertLifecycle {
        override val status: AlertStatus get() = AlertStatus.RESOLVED
    }
}
