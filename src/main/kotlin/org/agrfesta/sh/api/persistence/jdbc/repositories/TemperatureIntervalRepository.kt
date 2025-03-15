package org.agrfesta.sh.api.persistence.jdbc.repositories

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Time
import java.util.*
import org.agrfesta.sh.api.persistence.jdbc.entities.TemperatureIntervalEntity

@Repository
class TemperatureIntervalRepository(private val jdbcTemplate: NamedParameterJdbcTemplate) {

    fun findAllBySetting(settingUuid: UUID): List<TemperatureIntervalEntity> {
        val sql = "SELECT * FROM smart_home.temperature_interval WHERE setting_uuid = :settingUuid"
        val params = mapOf("settingUuid" to settingUuid)
        return jdbcTemplate.query(sql, params) { rs, _ ->
            TemperatureIntervalEntity(
                uuid = UUID.fromString(rs.getString("uuid")),
                settingUuid = UUID.fromString(rs.getString("setting_uuid")),
                startTime = rs.getTime("start_time").toLocalTime(),
                endTime = rs.getTime("end_time").toLocalTime(),
                temperature = rs.getBigDecimal("temperature")
            )
        }
    }

    fun save(interval: TemperatureIntervalEntity) {
        val sql = """
            INSERT INTO smart_home.temperature_interval (uuid, setting_uuid, start_time, end_time, temperature)
            VALUES (:uuid, :settingUuid, :startTime, :endTime, :temperature)
        """.trimIndent()
        val params = mapOf(
            "uuid" to interval.uuid,
            "settingUuid" to interval.settingUuid,
            "startTime" to Time.valueOf(interval.startTime),
            "endTime" to Time.valueOf(interval.endTime),
            "temperature" to interval.temperature
        )
        jdbcTemplate.update(sql, params)
    }

}
