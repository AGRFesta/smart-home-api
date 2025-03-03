package org.agrfesta.sh.api.persistence.jdbc.repositories

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import java.sql.ResultSet
import java.util.*
import org.agrfesta.sh.api.domain.failures.ActuatorAssignmentFailure
import org.agrfesta.sh.api.domain.failures.AreaNotFound
import org.agrfesta.sh.api.domain.failures.DeviceNotFound
import org.agrfesta.sh.api.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.persistence.AssignmentSuccess
import org.agrfesta.sh.api.persistence.jdbc.entities.ActuatorAssignmentEntity
import org.agrfesta.sh.api.persistence.jdbc.utils.getUuid
import org.agrfesta.sh.api.utils.RandomGenerator
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service

@Service
class ActuatorsAssignmentsJdbcRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val randomGenerator: RandomGenerator
) {

    companion object {
        private val violateAreaFkRegex = Regex(".*violates foreign key constraint.*fk_actuator_area.*", RegexOption.IGNORE_CASE)
        private val violateDeviceFkRegex = Regex(".*violates foreign key constraint.*fk_device.*", RegexOption.IGNORE_CASE)
    }

    fun persistAssignment(areaId: UUID, deviceId: UUID): Either<ActuatorAssignmentFailure, AssignmentSuccess> {
        val sql = """
            INSERT INTO smart_home.actuator_assignment (uuid, area_uuid, device_uuid)
            VALUES (:uuid, :areaUuid, :deviceUuid)
        """
        val params = mapOf(
            "uuid" to randomGenerator.uuid(),
            "areaUuid" to areaId,
            "deviceUuid" to deviceId
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

    fun findByDevice(deviceUuid: UUID): Either<PersistenceFailure, Collection<ActuatorAssignmentEntity>> = try {
        jdbcTemplate.query(
            """SELECT * FROM smart_home.actuator_assignment WHERE device_uuid = :deviceUuid;""",
            MapSqlParameterSource(mapOf("deviceUuid" to deviceUuid)), //TODO maybe just a map?
            ActuatorAssignmentRowMapper
        ).right()
    } catch (e: Exception) {
        PersistenceFailure(e).left()
    }

}

object ActuatorAssignmentRowMapper: RowMapper<ActuatorAssignmentEntity> {
    override fun mapRow(rs: ResultSet, rowNum: Int) = ActuatorAssignmentEntity(
        uuid = rs.getUuid("uuid"),
        actuatorUuid = rs.getUuid("device_uuid"),
        areaUuid = rs.getUuid("area_uuid")
    )
}
