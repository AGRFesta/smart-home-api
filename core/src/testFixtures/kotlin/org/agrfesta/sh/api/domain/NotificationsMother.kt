package org.agrfesta.sh.api.domain

import org.agrfesta.sh.api.core.domain.notifications.Notification
import org.agrfesta.sh.api.core.domain.notifications.NotificationEvent
import java.time.Instant
import java.util.UUID

fun aNotification(
    uuid: UUID = UUID.randomUUID(),
    alertUuid: UUID = UUID.randomUUID(),
    event: NotificationEvent = NotificationEvent.OPENED,
    sentAt: Instant = Instant.now(),
    payload: String? = null
) = Notification(uuid, alertUuid, event, sentAt, payload)
