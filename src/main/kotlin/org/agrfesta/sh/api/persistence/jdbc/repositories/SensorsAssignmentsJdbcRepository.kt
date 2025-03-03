package org.agrfesta.sh.api.persistence.jdbc.repositories

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.*
import org.agrfesta.sh.api.domain.failures.AreaNotFound
import org.agrfesta.sh.api.domain.failures.SensorAssignmentFailure
import org.agrfesta.sh.api.domain.failures.DeviceNotFound
import org.agrfesta.sh.api.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.persistence.AssignmentSuccess
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

    fun persistAssignment(areaId: UUID, deviceId: UUID): Either<SensorAssignmentFailure, AssignmentSuccess> {
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
                return when {
                    violateAreaFkRegex.containsMatchIn(message) -> AreaNotFound.left()
                    violateDeviceFkRegex.containsMatchIn(message) -> DeviceNotFound.left()
                    else -> PersistenceFailure(e).left()
                }
            }
            return PersistenceFailure(e).left()
        } catch (e: Exception) {
            return PersistenceFailure(e).left()
        }
        return AssignmentSuccess.right()
    }

    fun findByDevice(deviceUuid: UUID): Either<PersistenceFailure, Collection<SensorAssignmentEntity>> = try {
        jdbcTemplate.query(
            """SELECT * FROM smart_home.sensor_assignment WHERE device_uuid = :deviceUuid;""",
            MapSqlParameterSource(mapOf("deviceUuid" to deviceUuid)), //TODO maybe just a map?
            SensorAssignmentRowMapper
        ).right()
    } catch (e: Exception) {
        PersistenceFailure(e).left()
    }

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
