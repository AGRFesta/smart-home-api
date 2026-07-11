package org.agrfesta.sh.api.core.application.ports.inbounds

import arrow.core.Either
import org.agrfesta.sh.api.core.application.readmodels.notifications.NotificationsPageView
import org.agrfesta.sh.api.core.domain.failures.GetNotificationsFailure

/**
 * Inbound Port: reads a page of the emitted notifications, most recent first. The notification table
 * is unbounded over time, so the read side is always paginated.
 */
interface GetNotificationsUseCase {

    /**
     * @param page the zero-based page index.
     * @param size the page size.
     * @return [Either.Right] with the requested [NotificationsPageView],
     * or [Either.Left] with [GetNotificationsFailure] if the notifications cannot be read.
     */
    fun execute(page: Int, size: Int): Either<GetNotificationsFailure, NotificationsPageView>
}
