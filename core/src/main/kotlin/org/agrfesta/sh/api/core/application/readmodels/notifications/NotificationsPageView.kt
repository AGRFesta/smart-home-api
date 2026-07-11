package org.agrfesta.sh.api.core.application.readmodels.notifications

import org.agrfesta.sh.api.core.domain.notifications.Notification

/**
 * Read-model: one page of the persisted notifications, most recent first.
 *
 * @property total the overall number of persisted notifications, across all pages.
 */
data class NotificationsPageView(
    val items: List<Notification>,
    val page: Int,
    val size: Int,
    val total: Long
)
