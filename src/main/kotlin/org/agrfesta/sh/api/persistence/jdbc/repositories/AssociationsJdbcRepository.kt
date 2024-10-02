package org.agrfesta.sh.api.persistence.jdbc.repositories

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.agrfesta.sh.api.persistence.AssociationFailure
import org.agrfesta.sh.api.persistence.AssociationSuccess
import org.agrfesta.sh.api.persistence.DeviceNotFound
import org.agrfesta.sh.api.persistence.PersistenceFailure
import org.agrfesta.sh.api.persistence.RoomNotFound
import org.agrfesta.sh.api.persistence.jdbc.entities.AssociationEntity
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
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.*

@Service
class AssociationsJdbcRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val randomGenerator: RandomGenerator,
    private val timeService: TimeService
) {

    companion object {
        private val violateRoomFkRegex = Regex(".*violates foreign key constraint.*fk_room.*", RegexOption.IGNORE_CASE)
        private val violateDeviceFkRegex = Regex(".*violates foreign key constraint.*fk_device.*", RegexOption.IGNORE_CASE)
    }

    fun persistAssociation(roomId: UUID, deviceId: UUID): Either<AssociationFailure, AssociationSuccess> {
        val sql = """
            INSERT INTO smart_home.association (uuid, room_uuid, device_uuid, connected_on, disconnected_on)
            VALUES (:uuid, :roomUuid, :deviceUuid, :connectedOn, NULL)
        """
        val params = mapOf(
            "uuid" to randomGenerator.uuid(),
            "roomUuid" to roomId,
            "deviceUuid" to deviceId,
            "connectedOn" to Timestamp.from(timeService.now())
        )
        try {
            jdbcTemplate.update(sql, params)
        } catch (e: DataIntegrityViolationException) {
            val message = e.cause?.message
            if (message!=null) {
                return when {
                    violateRoomFkRegex.containsMatchIn(message) -> RoomNotFound.left()
                    violateDeviceFkRegex.containsMatchIn(message) -> DeviceNotFound.left()
                    else -> PersistenceFailure(e).left()
                }
            }
            return PersistenceFailure(e).left()
        } catch (e: Exception) {
            return PersistenceFailure(e).left()
        }
        return AssociationSuccess.right()
    }

    fun findByDevice(deviceUuid: UUID): Either<PersistenceFailure, Collection<AssociationEntity>> = try {
        jdbcTemplate.query(
            """SELECT * FROM smart_home.association WHERE device_uuid = :deviceUuid;""",
            MapSqlParameterSource(mapOf("deviceUuid" to deviceUuid)), //TODO maybe just a map?
            AssociationRowMapper
        ).right()
    } catch (e: Exception) {
        PersistenceFailure(e).left()
    }

}

object AssociationRowMapper: RowMapper<AssociationEntity> {
    override fun mapRow(rs: ResultSet, rowNum: Int) = AssociationEntity(
            uuid = rs.getUuid("uuid"),
            deviceUuid = rs.getUuid("device_uuid"),
            roomUuid = rs.getUuid("room_uuid"),
            connectedOn = rs.getInstant("connected_on"),
            disconnectedOn = rs.findInstant("disconnected_on")
        )
}
