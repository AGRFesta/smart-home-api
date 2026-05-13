package org.agrfesta.sh.api.persistence.jdbc.repositories

import java.sql.Time
import java.util.*
import org.agrfesta.sh.api.persistence.jdbc.entities.TemperatureIntervalEntity
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class TemperatureIntervalRepository(private val jdbcTemplate: NamedParameterJdbcTemplate) {

    fun findAllByArea(areaId: UUID): List<TemperatureIntervalEntity> {
        val sql = "SELECT * FROM smart_home.temperature_interval WHERE area_uuid = :areaId ORDER BY start_time"
        val params = mapOf("areaId" to areaId)
        return jdbcTemplate.query(sql, params) { rs, _ ->
            TemperatureIntervalEntity(
                uuid = UUID.fromString(rs.getString("uuid")),
                areaUuid = UUID.fromString(rs.getString("area_uuid")),
                startTime = rs.getTime("start_time").toLocalTime(),
                endTime = rs.getTime("end_time").toLocalTime(),
                temperature = rs.getBigDecimal("temperature")
            )
        }
    }

    fun save(interval: TemperatureIntervalEntity) {
        val sql = """
            INSERT INTO smart_home.temperature_interval (uuid, area_uuid, start_time, end_time, temperature)
            VALUES (:uuid, :areaUuid, :startTime, :endTime, :temperature)
        """.trimIndent()
        val params = mapOf(
            "uuid" to interval.uuid,
            "areaUuid" to interval.areaUuid,
            "startTime" to Time.valueOf(interval.startTime),
            "endTime" to Time.valueOf(interval.endTime),
            "temperature" to interval.temperature
        )
        jdbcTemplate.update(sql, params)
    }

}
