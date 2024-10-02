package org.agrfesta.sh.api.persistence.jdbc.repositories

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import org.agrfesta.sh.api.domain.Room
import org.agrfesta.sh.api.persistence.GetRoomFailure
import org.agrfesta.sh.api.persistence.PersistenceFailure
import org.agrfesta.sh.api.persistence.RoomCreationFailure
import org.agrfesta.sh.api.persistence.RoomNameConflict
import org.agrfesta.sh.api.persistence.RoomNotFound
import org.agrfesta.sh.api.persistence.jdbc.entities.RoomEntity
import org.agrfesta.sh.api.persistence.jdbc.utils.findInstant
import org.agrfesta.sh.api.persistence.jdbc.utils.getInstant
import org.agrfesta.sh.api.persistence.jdbc.utils.getUuid
import org.agrfesta.sh.api.utils.TimeService
import org.springframework.dao.DuplicateKeyException
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.*

@Service
class RoomsJdbcRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val timeService: TimeService
) {

    companion object {
        private val nameConflictRegex = Regex(".*duplicate key.*room_name_key.*", RegexOption.IGNORE_CASE)
    }

    fun persist(room: Room): Either<RoomCreationFailure, Room> {
        val sql = """
            INSERT INTO smart_home.room (uuid, name, created_on, updated_on)
            VALUES (:uuid, :name, :createdOn, :updatedOn)
        """
        val params = mapOf(
            "uuid" to room.uuid,
            "name" to room.name,
            "createdOn" to Timestamp.from(timeService.now()),
            "updatedOn" to null
        )
        try {
            jdbcTemplate.update(sql, params)
        } catch (e: DuplicateKeyException) {
            val message = e.cause?.message
            return if (message!=null && nameConflictRegex.containsMatchIn(message)) RoomNameConflict.left()
            else PersistenceFailure(e).left()
        }
        return room.right()
    }

    fun findRoomById(uuid: UUID): Either<GetRoomFailure, RoomEntity?> {
        val sql = """SELECT * FROM smart_home.room WHERE uuid = :uuid"""
        val params = mapOf("uuid" to uuid)
        val room: RoomEntity? = try {
            jdbcTemplate.queryForObject(sql, params, RoomRowMapper)
        } catch (e: EmptyResultDataAccessException) {
            null
        }
        return room.right()
    }

    fun getRoomById(uuid: UUID): Either<GetRoomFailure, RoomEntity> = findRoomById(uuid)
        .flatMap { it?.right() ?: RoomNotFound.left() }

    fun findRoomByName(name: String): Either<PersistenceFailure, RoomEntity?> {
        val sql = """SELECT * FROM smart_home.room WHERE name = :name"""
        val params = mapOf("name" to name)
        val room: RoomEntity? = try {
            jdbcTemplate.queryForObject(sql, params, RoomRowMapper)
        } catch (e: EmptyResultDataAccessException) {
            null
        }
        return room.right()
    }

    fun getRoomByName(name: String): Either<GetRoomFailure, RoomEntity> = findRoomByName(name)
        .flatMap { it?.right() ?: RoomNotFound.left() }

    fun getAll(): Either<PersistenceFailure, Collection<RoomEntity>> {
        val sql = """SELECT * FROM smart_home.room"""
        return try {
            jdbcTemplate.query(sql, RoomRowMapper).right()
        } catch (e: Exception) {
            PersistenceFailure(e).left()
        }
    }

}

object RoomRowMapper: RowMapper<RoomEntity> {
    override fun mapRow(rs: ResultSet, rowNum: Int) = RoomEntity(
            uuid = rs.getUuid("uuid"),
            name = rs.getString("name"),
            createdOn = rs.getInstant("created_on"),
            updatedOn = rs.findInstant("updated_on")
        )
}
