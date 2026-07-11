package org.agrfesta.sh.api.persistence.jdbc.adapters

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.agrfesta.sh.api.core.application.ports.outbounds.alerts.AlertsRepository
import org.agrfesta.sh.api.core.domain.alerts.Alert
import org.agrfesta.sh.api.core.domain.alerts.AlertStatus
import org.agrfesta.sh.api.core.domain.failures.AlertAlreadyOpen
import org.agrfesta.sh.api.core.domain.failures.AlertCreationFailure
import org.agrfesta.sh.api.core.domain.failures.AlertNotificationTrackingFailure
import org.agrfesta.sh.api.core.domain.failures.AlertRepositoryError
import org.agrfesta.sh.api.core.domain.failures.AlertResolutionFailure
import org.agrfesta.sh.api.core.domain.failures.GetAlertsFailure
import org.agrfesta.sh.api.persistence.jdbc.repositories.AlertsJdbcRepository
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.springframework.dao.DataAccessException
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class AlertsJdbcAdapter(
    private val alertsRepo: AlertsJdbcRepository
) : AlertsRepository {

    private val logger by LoggerDelegate()

    override fun getAlerts(status: AlertStatus?): Either<GetAlertsFailure, Collection<Alert>> = try {
        alertsRepo.findAlerts(status).map { it.toAlert() }.right()
    } catch (e: DataAccessException) {
        logger.error("Unexpected persistence error fetching alerts", e)
        AlertRepositoryError.left()
    } catch (e: IllegalArgumentException) {
        logger.error("Corrupt alert row while fetching alerts", e)
        AlertRepositoryError.left()
    } catch (e: IllegalStateException) {
        logger.error("Corrupt alert row while fetching alerts", e)
        AlertRepositoryError.left()
    }

    override fun create(alert: Alert): Either<AlertCreationFailure, Unit> = try {
        alertsRepo.persist(alert).right()
    } catch (e: DuplicateKeyException) {
        logger.warn("An OPEN alert already exists for type '${alert.type}' and target '${alert.target}'", e)
        AlertAlreadyOpen.left()
    } catch (e: DataAccessException) {
        logger.error("Unexpected persistence error creating alert '${alert.uuid}'", e)
        AlertRepositoryError.left()
    }

    override fun updateLastNotifiedAt(uuid: UUID, at: Instant): Either<AlertNotificationTrackingFailure, Unit> = try {
        if (alertsRepo.updateLastNotifiedAt(uuid, at) == 0) {
            logger.error("Unable to track last_notified_at for alert '$uuid': no such row")
            AlertRepositoryError.left()
        } else {
            Unit.right()
        }
    } catch (e: DataAccessException) {
        logger.error("Unexpected persistence error tracking last_notified_at for alert '$uuid'", e)
        AlertRepositoryError.left()
    }

    override fun resolve(alert: Alert): Either<AlertResolutionFailure, Unit> = try {
        if (alertsRepo.updateResolution(alert) == 0) {
            logger.error("Unable to resolve alert '${alert.uuid}': no such row")
            AlertRepositoryError.left()
        } else {
            Unit.right()
        }
    } catch (e: DataAccessException) {
        logger.error("Unexpected persistence error resolving alert '${alert.uuid}'", e)
        AlertRepositoryError.left()
    }
}
