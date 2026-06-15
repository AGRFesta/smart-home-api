package org.agrfesta.sh.api.core.application.ports.outbounds.alerts

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.alerts.Alert
import org.agrfesta.sh.api.core.domain.alerts.AlertStatus
import org.agrfesta.sh.api.core.domain.failures.AlertCreationFailure
import org.agrfesta.sh.api.core.domain.failures.GetAlertsFailure

/**
 * Outbound Port for [Alert] persistence operations.
 *
 * Alerts are the only alert-related state that must survive a restart, so they live exclusively on the DB.
 */
interface AlertsRepository {

    /**
     * Retrieves persisted alerts, optionally filtered by [status].
     *
     * @param status when set, restricts the result to alerts with this [AlertStatus]; `null` returns all.
     * @return [Either.Right] with the matching [Alert] collection (possibly empty),
     * or [Either.Left] with [GetAlertsFailure] if a database error occurs.
     */
    fun getAlerts(status: AlertStatus? = null): Either<GetAlertsFailure, Collection<Alert>>

    /**
     * Persists a new [alert].
     *
     * Storage enforces at most one OPEN alert per `(type, target)`: attempting to create a second one
     * yields [org.agrfesta.sh.api.core.domain.failures.AlertAlreadyOpen].
     *
     * @return [Either.Right] with [Unit] on success,
     * or [Either.Left] with [AlertCreationFailure] if the alert could not be created.
     */
    fun create(alert: Alert): Either<AlertCreationFailure, Unit>
}
