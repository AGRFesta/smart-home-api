package org.agrfesta.sh.api.persistence.jdbc.repositories

import java.sql.ResultSet
import java.sql.Timestamp
import java.util.*
import org.agrfesta.sh.api.domain.Area
import org.agrfesta.sh.api.persistence.AreaNotFoundException
import org.agrfesta.sh.api.persistence.SameNameAreaException
import org.agrfesta.sh.api.persistence.jdbc.entities.AreaEntity
import org.agrfesta.sh.api.persistence.jdbc.utils.findInstant
import org.agrfesta.sh.api.persistence.jdbc.utils.getInstant
import org.agrfesta.sh.api.persistence.jdbc.utils.getUuid
import org.agrfesta.sh.api.utils.TimeService
import org.springframework.dao.DuplicateKeyException
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service

@Service
class AreasJdbcRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val timeService: TimeService
) {

    companion object {
        private val nameConflictRegex = Regex(".*duplicate key.*Area_name_key.*", RegexOption.IGNORE_CASE)
    }

    fun persist(area: Area) {
        val sql = """
            INSERT INTO smart_home.area (uuid, name, created_on, updated_on)
            VALUES (:uuid, :name, :createdOn, :updatedOn)
        """
        val params = mapOf(
            "uuid" to area.uuid,
            "name" to area.name,
            "createdOn" to Timestamp.from(timeService.now()),
            "updatedOn" to null
        )
        try {
            jdbcTemplate.update(sql, params)
        } catch (e: DuplicateKeyException) {
            val message = e.cause?.message
            if (message!=null && nameConflictRegex.containsMatchIn(message)) throw SameNameAreaException()
            throw e
        }
    }

    fun findAreaById(uuid: UUID): AreaEntity? {
        val sql = """SELECT * FROM smart_home.area WHERE uuid = :uuid"""
        val params = mapOf("uuid" to uuid)
        val area: AreaEntity? = try {
            jdbcTemplate.queryForObject(sql, params, AreaRowMapper)
        } catch (e: EmptyResultDataAccessException) {
            null
        }
        return area
    }

    fun getAreaById(uuid: UUID): AreaEntity = findAreaById(uuid) ?: throw AreaNotFoundException()

    fun findAreaByName(name: String): AreaEntity? {
        val sql = """SELECT * FROM smart_home.area WHERE name = :name"""
        val params = mapOf("name" to name)
        return try {
            jdbcTemplate.queryForObject(sql, params, AreaRowMapper)
        } catch (e: EmptyResultDataAccessException) {
            null
        }
    }

    fun getAreaByName(name: String): AreaEntity = findAreaByName(name) ?: throw AreaNotFoundException()

    fun getAll(): Collection<AreaEntity> = jdbcTemplate.query("""SELECT * FROM smart_home.area""", AreaRowMapper)

}

object AreaRowMapper: RowMapper<AreaEntity> {
    override fun mapRow(rs: ResultSet, rowNum: Int) = AreaEntity(
            uuid = rs.getUuid("uuid"),
            name = rs.getString("name"),
            isIndoor = rs.getBoolean("is_indoor"),
            createdOn = rs.getInstant("created_on"),
            updatedOn = rs.findInstant("updated_on")
        )
}
