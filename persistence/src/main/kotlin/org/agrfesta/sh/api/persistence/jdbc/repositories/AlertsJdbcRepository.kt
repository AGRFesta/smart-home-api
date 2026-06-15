package org.agrfesta.sh.api.persistence.jdbc.repositories

import org.agrfesta.sh.api.core.domain.alerts.Alert
import org.agrfesta.sh.api.core.domain.alerts.AlertLifecycle
import org.agrfesta.sh.api.core.domain.alerts.AlertStatus
import org.agrfesta.sh.api.persistence.jdbc.entities.AlertEntity
import org.agrfesta.sh.api.persistence.jdbc.utils.findInstant
import org.agrfesta.sh.api.persistence.jdbc.utils.getAlertScope
import org.agrfesta.sh.api.persistence.jdbc.utils.getAlertStatus
import org.agrfesta.sh.api.persistence.jdbc.utils.getAlertType
import org.agrfesta.sh.api.persistence.jdbc.utils.getInstant
import org.agrfesta.sh.api.persistence.jdbc.utils.getUuid
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp

@Repository
class AlertsJdbcRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {

    fun findAlerts(status: AlertStatus?): Collection<AlertEntity> {
        val where = status?.let { " WHERE status = :status" } ?: ""
        val params = status?.let { mapOf("status" to it.name) } ?: emptyMap<String, Any>()
        return jdbcTemplate.query("SELECT * FROM smart_home.alert$where;", params, AlertRowMapper)
    }

    fun persist(alert: Alert) {
        val sql = """
            INSERT INTO smart_home.alert
            (uuid, type, scope, target, status, opened_at, resolved_at, details)
            VALUES (:uuid, :type, :scope, :target, :status, :openedAt, :resolvedAt, :details)
        """
        val resolvedAt = (alert.lifecycle as? AlertLifecycle.Resolved)?.resolvedAt
        val params = mapOf(
            "uuid" to alert.uuid,
            "type" to alert.type.name,
            "scope" to alert.target.scope.name,
            "target" to alert.target.reference,
            "status" to alert.lifecycle.status.name,
            "openedAt" to Timestamp.from(alert.openedAt),
            "resolvedAt" to resolvedAt?.let { Timestamp.from(it) },
            "details" to alert.details
        )
        jdbcTemplate.update(sql, params)
    }
}

object AlertRowMapper : RowMapper<AlertEntity> {
    override fun mapRow(rs: ResultSet, rowNum: Int): AlertEntity = AlertEntity(
        uuid = rs.getUuid("uuid"),
        type = rs.getAlertType("type"),
        scope = rs.getAlertScope("scope"),
        target = rs.getString("target"),
        status = rs.getAlertStatus("status"),
        openedAt = rs.getInstant("opened_at"),
        resolvedAt = rs.findInstant("resolved_at"),
        details = rs.getString("details")
    )
}
