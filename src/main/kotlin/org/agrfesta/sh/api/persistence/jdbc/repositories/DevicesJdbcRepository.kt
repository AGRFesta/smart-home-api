package org.agrfesta.sh.api.persistence.jdbc.repositories

import java.sql.ResultSet
import java.sql.Timestamp
import java.util.*
import org.agrfesta.sh.api.domain.devices.DeviceDataValue
import org.agrfesta.sh.api.domain.devices.DeviceDto
import org.agrfesta.sh.api.domain.devices.DeviceFeature
import org.agrfesta.sh.api.domain.devices.DeviceStatus
import org.agrfesta.sh.api.persistence.jdbc.entities.DeviceEntity
import org.agrfesta.sh.api.persistence.jdbc.utils.findInstant
import org.agrfesta.sh.api.persistence.jdbc.utils.getInstant
import org.agrfesta.sh.api.persistence.jdbc.utils.getProvider
import org.agrfesta.sh.api.persistence.jdbc.utils.getStatus
import org.agrfesta.sh.api.persistence.jdbc.utils.getUuid
import org.agrfesta.sh.api.utils.TimeService
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class DevicesJdbcRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val timeService: TimeService
) {

    fun findDeviceById(uuid: UUID): DeviceEntity? =
        jdbcTemplate.query(
            """SELECT * FROM smart_home.device WHERE uuid = :uuid;""",
            mapOf("uuid" to uuid),
            DeviceRowMapper
        ).singleOrNull()

    fun getAll(): Collection<DeviceEntity> = jdbcTemplate
        .query("""SELECT * FROM smart_home.device;""", DeviceRowMapper)

    fun persist(id: UUID, device: DeviceDataValue, deviceStatus: DeviceStatus = DeviceStatus.PAIRED) {
        val sql = """
            INSERT INTO smart_home.device
            (uuid, name, provider, status, provider_id, features, created_on, updated_on)
            VALUES (:uuid, :name, :provider, :status, :providerId, :features, :createdOn, :updatedOn)
        """
        val params = mapOf(
            "uuid" to id,
            "name" to device.name,
            "provider" to device.provider.name,
            "status" to deviceStatus.name,
            "providerId" to device.deviceProviderId,
            "features" to device.features.map { it.name }.toTypedArray(),
            "createdOn" to Timestamp.from(timeService.now()),
            "updatedOn" to null
        )
        jdbcTemplate.update(sql, params)
    }

    fun update(device: DeviceDto) {
        val sql = """
            UPDATE smart_home.device
            SET name = :name, status = :status, updated_on = :updatedOn
            WHERE provider = :provider AND provider_id = :providerId
        """
        val params = mapOf(
            "name" to device.name,
            "status" to device.status.name,
            "updatedOn" to Timestamp.from(timeService.now()),
            "provider" to device.provider.name,
            "providerId" to device.deviceProviderId
        )
        jdbcTemplate.update(sql, params)
    }

    fun deleteAll(): Int = jdbcTemplate.update("DELETE FROM smart_home.device", emptyMap<String, Any>())

}

object DeviceRowMapper: RowMapper<DeviceEntity> {
    override fun mapRow(rs: ResultSet, rowNum: Int): DeviceEntity {
        return DeviceEntity(
            uuid = rs.getUuid("uuid"),
            provider = rs.getProvider("provider"),
            providerId = rs.getString("provider_id"),
            name = rs.getString("name"),
            status = rs.getStatus("status"),
            features = (rs.getArray("features").array as Array<*>)
                .map { DeviceFeature.valueOf(it as String) }.toMutableSet(),
            createdOn = rs.getInstant("created_on"),
            updatedOn = rs.findInstant("updated_on")
        )
    }
}
