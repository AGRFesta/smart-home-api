package org.agrfesta.sh.api.core.domain.failures

/**
 * Failure contract of the `NotificationDispatcher` outbound port.
 *
 * Deliberately non-sealed: delivery adapters live in other modules (persistence today, real channels
 * in #195) and must be able to implement it. Callers handle it generically.
 */
interface NotificationDispatchFailure

/**
 * Groups all causes of a failure while reading persisted notifications.
 */
sealed interface GetNotificationsFailure

/**
 * Groups all causes of a failure while pruning notifications older than the retention window.
 */
sealed interface PruneNotificationsFailure

/** Infrastructure-level failure while accessing notification persistence. */
data object NotificationRepositoryError :
    NotificationDispatchFailure, GetNotificationsFailure, PruneNotificationsFailure
