package org.agrfesta.sh.api.core.application.ports.inbounds

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.alerts.Alert
import org.agrfesta.sh.api.core.domain.alerts.AlertStatus
import org.agrfesta.sh.api.core.domain.failures.GetAlertsFailure

interface GetAlertsUseCase {

    /**
     * Retrieves alerts, defaulting to the currently OPEN ones (the canonical "state visible in the model").
     *
     * @param status when set, restricts the result to alerts with this [AlertStatus];
     *        when `null`, defaults to [AlertStatus.OPEN].
     * @return [Either.Right] with the matching [Alert] collection (possibly empty),
     *         or [Either.Left] with a [GetAlertsFailure] if a database error occurs.
     */
    fun execute(status: AlertStatus? = null): Either<GetAlertsFailure, Collection<Alert>>
}
