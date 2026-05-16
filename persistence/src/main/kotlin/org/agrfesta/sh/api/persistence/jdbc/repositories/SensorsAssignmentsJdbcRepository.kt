package org.agrfesta.sh.api.persistence.jdbc.repositories

import java.sql.ResultSet
import java.sql.Timestamp
import java.util.*
import org.agrfesta.sh.api.persistence.AreaNotFoundException
import org.agrfesta.sh.api.persistence.DeviceNotFoundException
import org.agrfesta.sh.api.persistence.jdbc.entities.SensorAssignmentEntity
import org.agrfesta.sh.api.persistence.jdbc.utils.findInstant
import org.agrfesta.sh.api.persistence.jdbc.utils.getInstant
import org.agrfesta.sh.api.persistence.jdbc.utils.getUuid
import org.agrfesta.sh.api.core.application.ports.outbounds.TimeProvider
import org.postgresql.util.PSQLException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service

@Service
class SensorsAssignmentsJdbcRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val timeProvider: TimeProvider
) {

    fun persistAssignment(areaId: UUID, deviceId: UUID) {
        val sql = """
            INSERT INTO smart_home.sensor_assignment (uuid, area_uuid, device_uuid, connected_on, disconnected_on)
            VALUES (:uuid, :areaUuid, :deviceUuid, :connectedOn, NULL)
        """
        val params = mapOf(
            "uuid" to UUID.randomUUID(),
            "areaUuid" to areaId,
            "deviceUuid" to deviceId,
            "connectedOn" to Timestamp.from(timeProvider.now())
        )
        try {
            jdbcTemplate.update(sql, params)
        } catch (e: DataIntegrityViolationException) {
            val cause = e.cause as? PSQLException
            val constraintName = cause?.serverErrorMessage?.constraint
            throw when (constraintName) {
                "fk_area" -> AreaNotFoundException()
                "fk_device" -> DeviceNotFoundException()
                else -> e
            }
        }
    }

    fun disconnectSensor(areaId: UUID, deviceId: UUID) {
        val sql = """
            UPDATE smart_home.sensor_assignment
            SET disconnected_on = :disconnectedOn
            WHERE area_uuid = :areaUuid AND device_uuid = :deviceUuid AND disconnected_on IS NULL
        """
        val params = mapOf(
            "areaUuid" to areaId,
            "deviceUuid" to deviceId,
            "disconnectedOn" to Timestamp.from(timeProvider.now())
        )
        jdbcTemplate.update(sql, params)
    }

    fun deleteAll(): Int = jdbcTemplate.update("DELETE FROM smart_home.sensor_assignment", emptyMap<String, Any>())

    fun findByDevice(deviceUuid: UUID): Collection<SensorAssignmentEntity> = jdbcTemplate.query(
            """SELECT * FROM smart_home.sensor_assignment WHERE device_uuid = :deviceUuid;""",
            MapSqlParameterSource(mapOf("deviceUuid" to deviceUuid)),
            SensorAssignmentRowMapper
        )

}

object SensorAssignmentRowMapper: RowMapper<SensorAssignmentEntity> {
    override fun mapRow(rs: ResultSet, rowNum: Int) = SensorAssignmentEntity(
            uuid = rs.getUuid("uuid"),
            sensorUuid = rs.getUuid("device_uuid"),
            areaUuid = rs.getUuid("area_uuid"),
            connectedOn = rs.getInstant("connected_on"),
            disconnectedOn = rs.findInstant("disconnected_on")
        )
}
