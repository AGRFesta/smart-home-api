package org.agrfesta.sh.api.persistence.jdbc.repositories

import java.util.UUID
import org.agrfesta.sh.api.persistence.AreaNotFoundException
import org.agrfesta.sh.api.persistence.jdbc.entities.TemperatureSettingEntity
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class TemperatureSettingRepository(private val jdbcTemplate: NamedParameterJdbcTemplate) {

    companion object {
        private val violateAreaFkRegex = Regex(
            ".*violates foreign key constraint.*fk_temperature_area.*",
            RegexOption.IGNORE_CASE)
    }

    fun findSettingByAreaId(areaId: UUID): TemperatureSettingEntity? {
        val sql = "SELECT * FROM smart_home.temperature_setting WHERE area_uuid = :areaId"
        val params = mapOf("areaId" to areaId)
        return jdbcTemplate.query(sql, params) { rs, _ ->
            TemperatureSettingEntity(
                areaUuid = UUID.fromString(rs.getString("area_uuid")),
                defaultTemperature = rs.getBigDecimal("default_temperature")
            )
        }.firstOrNull()
    }

    fun existsSettingByAreaId(areaId: UUID): Boolean {
        val sql = "SELECT EXISTS(SELECT 1 FROM smart_home.temperature_setting WHERE area_uuid = :areaId)"
        val params = mapOf("areaId" to areaId)

        return jdbcTemplate.queryForObject(sql, params, Boolean::class.java) ?: false
    }

    fun save(setting: TemperatureSettingEntity) {
        val sql = """
            INSERT INTO smart_home.temperature_setting (area_uuid, default_temperature)
            VALUES (:areaUuid, :defaultTemperature)
        """.trimIndent()
        val params = mapOf(
            "areaUuid" to setting.areaUuid,
            "defaultTemperature" to setting.defaultTemperature
        )
        try {
            jdbcTemplate.update(sql, params)
        } catch (e: DataIntegrityViolationException) {
            val message = e.cause?.message
            if (message!=null) {
                when {
                    violateAreaFkRegex.containsMatchIn(message) -> throw AreaNotFoundException()
                    else -> throw e
                }
            }
            throw e
        }
    }

    fun deleteByByAreaId(areaId: UUID) {
        val sql = "DELETE FROM smart_home.temperature_setting WHERE area_uuid = :areaId"
        val params = mapOf("areaId" to areaId)
        jdbcTemplate.update(sql, params)
    }
}
