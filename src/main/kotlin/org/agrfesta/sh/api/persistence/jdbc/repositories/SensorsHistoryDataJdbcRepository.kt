package org.agrfesta.sh.api.persistence.jdbc.repositories

import java.math.BigDecimal
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.*
import org.agrfesta.sh.api.domain.devices.SensorDataType
import org.agrfesta.sh.api.persistence.jdbc.entities.SensorHistoryDataEntity
import org.agrfesta.sh.api.persistence.jdbc.utils.getInstant
import org.agrfesta.sh.api.persistence.jdbc.utils.getUuid
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service

@Service
class SensorsHistoryDataJdbcRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {

    fun persist(sensorUuid: UUID, time: Instant, dataType: SensorDataType, value: BigDecimal) {
        val sql = """
            INSERT INTO smart_home.sensor_history_data (sensor_uuid, time, data_type, value)
            VALUES (:sensorUuid, :time, :dataType, :value)
        """.trimIndent()

        val parameters = MapSqlParameterSource()
            .addValue("sensorUuid", sensorUuid)
            .addValue("time", Timestamp.from(time))
            .addValue("dataType", dataType.name)
            .addValue("value", value)

        jdbcTemplate.update(sql, parameters)
    }

    fun findAllBySensorUuid(sensorUuid: UUID): List<SensorHistoryDataEntity> {
        val sql = """
            SELECT sensor_uuid, time, data_type, value
            FROM smart_home.sensor_history_data
            WHERE sensor_uuid = :sensorUuid
            ORDER BY time ASC
        """.trimIndent()

        val parameters = MapSqlParameterSource()
            .addValue("sensorUuid", sensorUuid)

        return jdbcTemplate.query(sql, parameters, SensorHistoryDataRowMapper)
    }

}

object SensorHistoryDataRowMapper: RowMapper<SensorHistoryDataEntity> {
    override fun mapRow(rs: ResultSet, rowNum: Int) = SensorHistoryDataEntity(
        sensorUuid = rs.getUuid("sensor_uuid"),
        type = SensorDataType.valueOf(rs.getString("data_type")),
        time = rs.getInstant("time"),
        value = rs.getBigDecimal("value")
    )
}
