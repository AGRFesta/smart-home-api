package org.agrfesta.sh.api.persistence.jdbc.repositories

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import java.math.BigDecimal
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.*
import org.agrfesta.sh.api.domain.devices.SensorDataType
import org.agrfesta.sh.api.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.persistence.SensorDataPersistenceSuccess
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

    fun persist(
        sensorUuid: UUID,
        time: Instant,
        dataType: SensorDataType,
        value: BigDecimal
    ): Either<PersistenceFailure, SensorDataPersistenceSuccess> {
        val sql = """
            INSERT INTO smart_home.sensor_history_data (sensor_uuid, time, data_type, value)
            VALUES (:sensorUuid, :time, :dataType, :value)
        """.trimIndent()

        val parameters = MapSqlParameterSource()
            .addValue("sensorUuid", sensorUuid)
            .addValue("time", Timestamp.from(time))
            .addValue("dataType", dataType.name)
            .addValue("value", value)

        try {
            jdbcTemplate.update(sql, parameters)
        } catch (e: Exception) {
            return PersistenceFailure(e).left()
        }

        return SensorDataPersistenceSuccess.right()
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
