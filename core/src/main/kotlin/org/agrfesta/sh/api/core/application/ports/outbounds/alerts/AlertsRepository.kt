package org.agrfesta.sh.api.core.application.ports.outbounds.alerts

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.alerts.Alert
import org.agrfesta.sh.api.core.domain.alerts.AlertStatus
import org.agrfesta.sh.api.core.domain.failures.AlertCreationFailure
import org.agrfesta.sh.api.core.domain.failures.AlertNotificationTrackingFailure
import org.agrfesta.sh.api.core.domain.failures.AlertResolutionFailure
import org.agrfesta.sh.api.core.domain.failures.GetAlertsFailure
import java.time.Instant
import java.util.UUID

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

    /**
     * Persists the resolution of an existing alert.
     *
     * @param alert the alert in its resolved state (its lifecycle must be
     * [org.agrfesta.sh.api.core.domain.alerts.AlertLifecycle.Resolved]).
     * @return [Either.Right] with [Unit] on success,
     * or [Either.Left] with [AlertResolutionFailure] if the resolution could not be persisted.
     */
    fun resolve(alert: Alert): Either<AlertResolutionFailure, Unit>

    /**
     * Persists [at] as the instant the alert [uuid] was last successfully notified, enforcing the
     * reminder cadence (see `docs/domain/ALERTS.md`).
     *
     * @return [Either.Right] with [Unit] on success,
     * or [Either.Left] with [AlertNotificationTrackingFailure] if the update could not be persisted.
     */
    fun updateLastNotifiedAt(uuid: UUID, at: Instant): Either<AlertNotificationTrackingFailure, Unit>
}
