package org.agrfesta.sh.api.core.application.ports.outbounds.notifications

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.failures.NotificationDispatchFailure
import org.agrfesta.sh.api.core.domain.notifications.Notification

/**
 * Outbound port abstracting the notification delivery channel, so it can be swapped without touching
 * the core (mirroring how `HomeStateRefreshPublisher` abstracts its delivery mechanism).
 *
 * The minimal implementation persists the notification (queryable via API); real channels
 * (push / email / Telegram) are #195 implementations of this same port.
 */
interface NotificationDispatcher {

    /**
     * Delivers a [notification].
     *
     * @return [Either.Right] with [Unit] on success,
     * or [Either.Left] with [NotificationDispatchFailure] if delivery failed.
     */
    fun dispatch(notification: Notification): Either<NotificationDispatchFailure, Unit>
}
