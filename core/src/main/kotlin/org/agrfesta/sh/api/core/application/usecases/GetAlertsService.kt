package org.agrfesta.sh.api.core.application.usecases

import arrow.core.Either
import org.agrfesta.sh.api.core.application.ports.inbounds.GetAlertsUseCase
import org.agrfesta.sh.api.core.application.ports.outbounds.alerts.AlertsRepository
import org.agrfesta.sh.api.core.domain.alerts.Alert
import org.agrfesta.sh.api.core.domain.alerts.AlertStatus
import org.agrfesta.sh.api.core.domain.failures.GetAlertsFailure
import org.springframework.stereotype.Service

@Service
class GetAlertsService(
    private val alertsRepository: AlertsRepository
) : GetAlertsUseCase {

    override fun execute(status: AlertStatus?): Either<GetAlertsFailure, Collection<Alert>> =
        alertsRepository.getAlerts(status ?: AlertStatus.OPEN)
}
