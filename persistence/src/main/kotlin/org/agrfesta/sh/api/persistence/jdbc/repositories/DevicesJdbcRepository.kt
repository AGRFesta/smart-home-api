package org.agrfesta.sh.api.persistence.jdbc.repositories

import org.agrfesta.sh.api.core.application.ports.outbounds.TimeProvider
import org.agrfesta.sh.api.core.domain.devices.Device
import org.agrfesta.sh.api.core.domain.devices.DeviceFeature
import org.agrfesta.sh.api.core.domain.devices.DeviceStatus
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.core.domain.devices.ProviderDeviceData
import org.agrfesta.sh.api.persistence.jdbc.entities.DeviceEntity
import org.agrfesta.sh.api.persistence.jdbc.utils.findInstant
import org.agrfesta.sh.api.persistence.jdbc.utils.getFeatures
import org.agrfesta.sh.api.persistence.jdbc.utils.getInstant
import org.agrfesta.sh.api.persistence.jdbc.utils.getProvider
import org.agrfesta.sh.api.persistence.jdbc.utils.getStatus
import org.agrfesta.sh.api.persistence.jdbc.utils.getUuid
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.*

@Repository
class DevicesJdbcRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val timeProvider: TimeProvider
) {

    fun findDeviceById(uuid: UUID): DeviceEntity? =
        jdbcTemplate.query(
            """SELECT * FROM smart_home.device WHERE uuid = :uuid;""",
            mapOf("uuid" to uuid),
            DeviceRowMapper
        ).singleOrNull()

    fun getAll(): Collection<DeviceEntity> = jdbcTemplate
        .query("""SELECT * FROM smart_home.device;""", DeviceRowMapper)

    fun findDevices(
        provider: Provider?,
        status: DeviceStatus?,
        feature: DeviceFeature?
    ): Collection<DeviceEntity> {
        val conditions = mutableListOf<String>()
        val params = mutableMapOf<String, Any>()
        provider?.let {
            conditions += "provider = :provider"
            params["provider"] = it.name
        }
        status?.let {
            conditions += "status = :status"
            params["status"] = it.name
        }
        feature?.let {
            conditions += ":feature = ANY(features)"
            params["feature"] = it.name
        }
        val where = if (conditions.isEmpty()) "" else " WHERE ${conditions.joinToString(" AND ")}"
        return jdbcTemplate.query("SELECT * FROM smart_home.device$where;", params, DeviceRowMapper)
    }

    fun persist(id: UUID, device: ProviderDeviceData, deviceStatus: DeviceStatus = DeviceStatus.PAIRED) {
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
            "createdOn" to Timestamp.from(timeProvider.now()),
            "updatedOn" to null
        )
        jdbcTemplate.update(sql, params)
    }

    fun update(device: Device) {
        val sql = """
            UPDATE smart_home.device
            SET name = :name, status = :status, updated_on = :updatedOn
            WHERE provider = :provider AND provider_id = :providerId
        """
        val params = mapOf(
            "name" to device.name,
            "status" to device.status.name,
            "updatedOn" to Timestamp.from(timeProvider.now()),
            "provider" to device.provider.name,
            "providerId" to device.deviceProviderId
        )
        jdbcTemplate.update(sql, params)
    }

    fun deleteAll(): Int = jdbcTemplate.update("DELETE FROM smart_home.device", emptyMap<String, Any>())
}

object DeviceRowMapper : RowMapper<DeviceEntity> {
    override fun mapRow(rs: ResultSet, rowNum: Int): DeviceEntity {
        return DeviceEntity(
            uuid = rs.getUuid("uuid"),
            provider = rs.getProvider("provider"),
            providerId = rs.getString("provider_id"),
            name = rs.getString("name"),
            status = rs.getStatus("status"),
            features = rs.getFeatures("features").toMutableSet(),
            createdOn = rs.getInstant("created_on"),
            updatedOn = rs.findInstant("updated_on")
        )
    }
}
