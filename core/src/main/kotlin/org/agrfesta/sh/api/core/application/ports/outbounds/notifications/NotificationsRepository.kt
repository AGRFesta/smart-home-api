package org.agrfesta.sh.api.core.application.ports.outbounds.notifications

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.failures.GetNotificationsFailure
import org.agrfesta.sh.api.core.domain.failures.PruneNotificationsFailure
import org.agrfesta.sh.api.core.domain.notifications.Notification
import java.time.Instant

/**
 * Outbound Port for querying and maintaining the persisted notifications, the minimal delivery channel
 * of [NotificationDispatcher]. The table is unbounded over time, so reads are paginated and old rows are
 * pruned by the retention policy.
 */
interface NotificationsRepository {

    /**
     * Retrieves a page of persisted notifications.
     *
     * @param offset how many notifications to skip.
     * @param limit the maximum number of notifications to return.
     * @return [Either.Right] with the matching [Notification] collection (possibly empty),
     * or [Either.Left] with [GetNotificationsFailure] if a database error occurs.
     */
    fun findAll(offset: Int, limit: Int): Either<GetNotificationsFailure, Collection<Notification>>

    /**
     * Counts all the persisted notifications.
     *
     * @return [Either.Right] with the overall number of notifications,
     * or [Either.Left] with [GetNotificationsFailure] if a database error occurs.
     */
    fun count(): Either<GetNotificationsFailure, Long>

    /**
     * Deletes the notifications sent before [threshold] (retention policy).
     *
     * @return [Either.Right] with the number of deleted notifications,
     * or [Either.Left] with [PruneNotificationsFailure] if a database error occurs.
     */
    fun deleteOlderThan(threshold: Instant): Either<PruneNotificationsFailure, Int>
}
