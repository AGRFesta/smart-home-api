package org.agrfesta.sh.api.persistence.jdbc.repositories

import org.agrfesta.sh.api.core.domain.notifications.Notification
import org.agrfesta.sh.api.core.domain.notifications.NotificationEvent
import org.agrfesta.sh.api.persistence.jdbc.utils.getInstant
import org.agrfesta.sh.api.persistence.jdbc.utils.getUuid
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant

@Repository
class NotificationsJdbcRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {

    fun persist(notification: Notification) {
        val sql = """
            INSERT INTO smart_home.notification (uuid, alert_uuid, event, sent_at, payload)
            VALUES (:uuid, :alertUuid, :event, :sentAt, :payload)
        """
        val params = mapOf(
            "uuid" to notification.uuid,
            "alertUuid" to notification.alertUuid,
            "event" to notification.event.name,
            "sentAt" to Timestamp.from(notification.sentAt),
            "payload" to notification.payload
        )
        jdbcTemplate.update(sql, params)
    }

    /** Deletes the notification rows sent before [threshold]; returns the number of deleted rows. */
    fun deleteOlderThan(threshold: Instant): Int = jdbcTemplate.update(
        "DELETE FROM smart_home.notification WHERE sent_at < :threshold;",
        mapOf("threshold" to Timestamp.from(threshold))
    )

    fun count(): Long = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM smart_home.notification;",
        emptyMap<String, Any>(),
        Long::class.java
    ) ?: 0L

    fun findAll(offset: Int, limit: Int): Collection<Notification> {
        val sql = """
            SELECT * FROM smart_home.notification
            ORDER BY sent_at DESC, uuid
            LIMIT :limit OFFSET :offset
        """
        return jdbcTemplate.query(sql, mapOf("limit" to limit, "offset" to offset), NotificationRowMapper)
    }
}

object NotificationRowMapper : RowMapper<Notification> {
    override fun mapRow(rs: ResultSet, rowNum: Int): Notification = Notification(
        uuid = rs.getUuid("uuid"),
        alertUuid = rs.getUuid("alert_uuid"),
        event = NotificationEvent.valueOf(rs.getString("event")),
        sentAt = rs.getInstant("sent_at"),
        payload = rs.getString("payload")
    )
}
