package org.agrfesta.sh.api.persistence.jdbc.repositories

import org.agrfesta.sh.api.core.application.ports.outbounds.TimeProvider
import org.agrfesta.sh.api.core.domain.areas.AreaDto
import org.agrfesta.sh.api.persistence.SameNameAreaException
import org.agrfesta.sh.api.persistence.jdbc.entities.AreaEntity
import org.agrfesta.sh.api.persistence.jdbc.utils.findInstant
import org.agrfesta.sh.api.persistence.jdbc.utils.getInstant
import org.agrfesta.sh.api.persistence.jdbc.utils.getUuid
import org.postgresql.util.PSQLException
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.*

@Service
class AreasJdbcRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val timeProvider: TimeProvider
) {

    fun persist(area: AreaDto) {
        val sql = """
            INSERT INTO smart_home.area (uuid, name, created_on, updated_on)
            VALUES (:uuid, :name, :createdOn, :updatedOn)
        """
        val params = mapOf(
            "uuid" to area.uuid,
            "name" to area.name,
            "createdOn" to Timestamp.from(timeProvider.now()),
            "updatedOn" to null
        )
        try {
            jdbcTemplate.update(sql, params)
        } catch (e: DuplicateKeyException) {
            val cause = e.cause as? PSQLException
            if (cause?.serverErrorMessage?.constraint == "area_name_key") {
                throw SameNameAreaException()
            }
            throw e
        }
    }

    fun findAreaById(uuid: UUID): AreaEntity? =
        jdbcTemplate.query(
            """SELECT * FROM smart_home.area WHERE uuid = :uuid;""",
            mapOf("uuid" to uuid),
            AreaRowMapper
        ).singleOrNull()

    fun findAreaByName(name: String): AreaEntity? =
        jdbcTemplate.query(
            """SELECT * FROM smart_home.area WHERE name = :name;""",
            mapOf("name" to name),
            AreaRowMapper
        ).singleOrNull()

    fun getAll(): Collection<AreaEntity> = jdbcTemplate.query("""SELECT * FROM smart_home.area""", AreaRowMapper)

    fun update(area: AreaDto): Int {
        val sql = """
            UPDATE smart_home.area
            SET name = :name, is_indoor = :isIndoor, updated_on = :updatedOn
            WHERE uuid = :uuid
        """
        val params = mapOf(
            "uuid" to area.uuid,
            "name" to area.name,
            "isIndoor" to area.isIndoor,
            "updatedOn" to Timestamp.from(timeProvider.now())
        )
        try {
            return jdbcTemplate.update(sql, params)
        } catch (e: DuplicateKeyException) {
            val cause = e.cause as? PSQLException
            if (cause?.serverErrorMessage?.constraint == "area_name_key") {
                throw SameNameAreaException()
            }
            throw e
        }
    }

    fun deleteAreaById(uuid: UUID): Int {
        val sql = """
            DELETE FROM smart_home.area
            WHERE uuid = :uuid;
        """
        return jdbcTemplate.update(sql, mapOf("uuid" to uuid))
    }

    fun deleteAll(): Int = jdbcTemplate.update("DELETE FROM smart_home.area", emptyMap<String, Any>())
}

object AreaRowMapper : RowMapper<AreaEntity> {
    override fun mapRow(rs: ResultSet, rowNum: Int) = AreaEntity(
        uuid = rs.getUuid("uuid"),
        name = rs.getString("name"),
        isIndoor = rs.getBoolean("is_indoor"),
        createdOn = rs.getInstant("created_on"),
        updatedOn = rs.findInstant("updated_on")
    )
}
