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
import org.agrfesta.sh.api.utils.RandomGenerator
import org.agrfesta.sh.api.utils.TimeService
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service

@Service
class SensorsAssignmentsJdbcRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val randomGenerator: RandomGenerator,
    private val timeService: TimeService
) {

    companion object {
        private val violateAreaFkRegex = Regex(".*violates foreign key constraint.*fk_area.*", RegexOption.IGNORE_CASE)
        private val violateDeviceFkRegex = Regex(".*violates foreign key constraint.*fk_device.*", RegexOption.IGNORE_CASE)
    }

    fun persistAssignment(areaId: UUID, deviceId: UUID) {
        val sql = """
            INSERT INTO smart_home.sensor_assignment (uuid, area_uuid, device_uuid, connected_on, disconnected_on)
            VALUES (:uuid, :areaUuid, :deviceUuid, :connectedOn, NULL)
        """
        val params = mapOf(
            "uuid" to randomGenerator.uuid(),
            "areaUuid" to areaId,
            "deviceUuid" to deviceId,
            "connectedOn" to Timestamp.from(timeService.now())
        )
        try {
            jdbcTemplate.update(sql, params)
        } catch (e: DataIntegrityViolationException) {
            val message = e.cause?.message
            if (message!=null) {
                when {
                    violateAreaFkRegex.containsMatchIn(message) -> throw AreaNotFoundException()
                    violateDeviceFkRegex.containsMatchIn(message) -> throw DeviceNotFoundException()
                    else -> throw e
                }
            }
            throw e
        }
    }

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
