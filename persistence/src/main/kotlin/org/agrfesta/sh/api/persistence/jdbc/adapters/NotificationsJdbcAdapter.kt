package org.agrfesta.sh.api.persistence.jdbc.adapters

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.agrfesta.sh.api.core.application.ports.outbounds.notifications.NotificationsRepository
import org.agrfesta.sh.api.core.domain.failures.GetNotificationsFailure
import org.agrfesta.sh.api.core.domain.failures.NotificationRepositoryError
import org.agrfesta.sh.api.core.domain.failures.PruneNotificationsFailure
import org.agrfesta.sh.api.core.domain.notifications.Notification
import org.agrfesta.sh.api.persistence.jdbc.repositories.NotificationsJdbcRepository
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class NotificationsJdbcAdapter(
    private val notificationsRepo: NotificationsJdbcRepository
) : NotificationsRepository {

    private val logger by LoggerDelegate()

    override fun findAll(offset: Int, limit: Int): Either<GetNotificationsFailure, Collection<Notification>> = try {
        notificationsRepo.findAll(offset, limit).right()
    } catch (e: DataAccessException) {
        logger.error("Unexpected persistence error fetching notifications", e)
        NotificationRepositoryError.left()
    } catch (e: IllegalArgumentException) {
        logger.error("Corrupt notification row while fetching notifications", e)
        NotificationRepositoryError.left()
    }

    override fun count(): Either<GetNotificationsFailure, Long> = try {
        notificationsRepo.count().right()
    } catch (e: DataAccessException) {
        logger.error("Unexpected persistence error counting notifications", e)
        NotificationRepositoryError.left()
    }

    override fun deleteOlderThan(threshold: Instant): Either<PruneNotificationsFailure, Int> = try {
        notificationsRepo.deleteOlderThan(threshold).right()
    } catch (e: DataAccessException) {
        logger.error("Unexpected persistence error pruning notifications older than $threshold", e)
        NotificationRepositoryError.left()
    }
}
