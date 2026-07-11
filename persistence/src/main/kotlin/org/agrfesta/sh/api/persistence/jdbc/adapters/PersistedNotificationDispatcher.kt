package org.agrfesta.sh.api.persistence.jdbc.adapters

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.agrfesta.sh.api.core.application.ports.outbounds.notifications.NotificationDispatcher
import org.agrfesta.sh.api.core.domain.failures.NotificationDispatchFailure
import org.agrfesta.sh.api.core.domain.failures.NotificationRepositoryError
import org.agrfesta.sh.api.core.domain.notifications.Notification
import org.agrfesta.sh.api.persistence.jdbc.repositories.NotificationsJdbcRepository
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service

/**
 * Minimal delivery channel (#194): "delivering" a notification means persisting it, queryable via API.
 * Real channels (push / email / Telegram) are #195 implementations of the same port.
 */
@Service
class PersistedNotificationDispatcher(
    private val notificationsRepo: NotificationsJdbcRepository
) : NotificationDispatcher {

    private val logger by LoggerDelegate()

    override fun dispatch(notification: Notification): Either<NotificationDispatchFailure, Unit> = try {
        notificationsRepo.persist(notification).right()
    } catch (e: DataAccessException) {
        logger.error("Unexpected persistence error dispatching notification '${notification.uuid}'", e)
        NotificationRepositoryError.left()
    }
}
