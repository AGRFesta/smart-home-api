package org.agrfesta.sh.api.persistence.jdbc.repositories

import java.sql.ResultSet
import java.util.*
import org.agrfesta.sh.api.persistence.AreaNotFoundException
import org.agrfesta.sh.api.persistence.DeviceNotFoundException
import org.agrfesta.sh.api.persistence.jdbc.entities.ActuatorAssignmentEntity
import org.agrfesta.sh.api.persistence.jdbc.utils.getUuid
import org.postgresql.util.PSQLException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service

@Service
class ActuatorsAssignmentsJdbcRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {

    fun persistAssignment(areaId: UUID, deviceId: UUID) {
        val sql = """
            INSERT INTO smart_home.actuator_assignment (uuid, area_uuid, device_uuid)
            VALUES (:uuid, :areaUuid, :deviceUuid)
        """
        val params = mapOf(
            "uuid" to UUID.randomUUID(),
            "areaUuid" to areaId,
            "deviceUuid" to deviceId
        )
        try {
            jdbcTemplate.update(sql, params)
        } catch (e: DataIntegrityViolationException) {
            val cause = e.cause
            if (cause is PSQLException) {
                val constraintName = cause.serverErrorMessage?.constraint
                throw when (constraintName) {
                    "fk_actuator_area" -> AreaNotFoundException()
                    "fk_actuator_device" -> DeviceNotFoundException()
                    else -> e // something else
                }
            }
            throw e
        }
    }

    fun deleteAssignment(areaId: UUID, deviceId: UUID) {
        val sql = """
            DELETE FROM smart_home.actuator_assignment
            WHERE area_uuid = :areaUuid AND device_uuid = :deviceUuid
        """
        jdbcTemplate.update(sql, mapOf("areaUuid" to areaId, "deviceUuid" to deviceId))
    }

    fun findByDevice(deviceUuid: UUID): Collection<ActuatorAssignmentEntity> = jdbcTemplate.query(
            """SELECT * FROM smart_home.actuator_assignment WHERE device_uuid = :deviceUuid;""",
            MapSqlParameterSource(mapOf("deviceUuid" to deviceUuid)), //TODO maybe just a map?
            ActuatorAssignmentRowMapper
        )

}

object ActuatorAssignmentRowMapper: RowMapper<ActuatorAssignmentEntity> {
    override fun mapRow(rs: ResultSet, rowNum: Int) = ActuatorAssignmentEntity(
        uuid = rs.getUuid("uuid"),
        actuatorUuid = rs.getUuid("device_uuid"),
        areaUuid = rs.getUuid("area_uuid")
    )
}
