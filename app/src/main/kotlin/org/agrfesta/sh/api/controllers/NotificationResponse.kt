package org.agrfesta.sh.api.controllers

import org.agrfesta.sh.api.core.application.readmodels.notifications.NotificationsPageView
import org.agrfesta.sh.api.core.domain.notifications.Notification
import org.agrfesta.sh.api.core.domain.notifications.NotificationEvent
import java.time.Instant
import java.util.UUID

data class NotificationResponse(
    val uuid: UUID,
    val alertUuid: UUID,
    val event: NotificationEvent,
    val sentAt: Instant,
    val payload: String?
)

data class NotificationsPageResponse(
    val items: List<NotificationResponse>,
    val page: Int,
    val size: Int,
    val total: Long
)

fun Notification.toResponse() = NotificationResponse(
    uuid = uuid,
    alertUuid = alertUuid,
    event = event,
    sentAt = sentAt,
    payload = payload
)

fun NotificationsPageView.toResponse() = NotificationsPageResponse(
    items = items.map { it.toResponse() },
    page = page,
    size = size,
    total = total
)
