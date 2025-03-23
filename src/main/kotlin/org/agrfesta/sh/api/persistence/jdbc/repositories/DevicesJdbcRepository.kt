package org.agrfesta.sh.api.persistence.jdbc.repositories

import arrow.core.Either
import arrow.core.right
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.*
import org.agrfesta.sh.api.domain.devices.Device
import org.agrfesta.sh.api.domain.devices.DeviceDataValue
import org.agrfesta.sh.api.domain.devices.DeviceFeature
import org.agrfesta.sh.api.domain.devices.DeviceStatus
import org.agrfesta.sh.api.domain.devices.Provider
import org.agrfesta.sh.api.domain.failures.GetDeviceFailure
import org.agrfesta.sh.api.persistence.DeviceNotFoundException
import org.agrfesta.sh.api.persistence.jdbc.entities.DeviceEntity
import org.agrfesta.sh.api.persistence.jdbc.utils.findInstant
import org.agrfesta.sh.api.persistence.jdbc.utils.getInstant
import org.agrfesta.sh.api.persistence.jdbc.utils.getProvider
import org.agrfesta.sh.api.persistence.jdbc.utils.getStatus
import org.agrfesta.sh.api.persistence.jdbc.utils.getUuid
import org.agrfesta.sh.api.utils.RandomGenerator
import org.agrfesta.sh.api.utils.TimeService
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service

@Service
class DevicesJdbcRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val randomGenerator: RandomGenerator,
    private val timeService: TimeService
) {

    fun findDeviceById(uuid: UUID): DeviceEntity? =
        try {
            jdbcTemplate.queryForObject(
                """SELECT * FROM smart_home.device WHERE uuid = :uuid;""",
                mapOf("uuid" to uuid),
                DeviceRowMapper
            )
        } catch (e: EmptyResultDataAccessException) {
            null
        }

    fun getDeviceById(uuid: UUID): DeviceEntity = findDeviceById(uuid) ?: throw DeviceNotFoundException()

    fun findByProviderAndProviderId(provider: Provider, providerId: String): Either<GetDeviceFailure, DeviceEntity?> =
        try {
            jdbcTemplate.queryForObject(
                """SELECT * FROM smart_home.device WHERE provider = :provider AND provider_id = :providerId;""",
                mapOf("provider" to provider.name, "providerId" to providerId),
                DeviceRowMapper
            )
        } catch (e: EmptyResultDataAccessException) {
            null
        }.right()

    fun getAll(): Collection<DeviceEntity> = jdbcTemplate
        .query("""SELECT * FROM smart_home.device;""", DeviceRowMapper)

    fun persist(device: DeviceDataValue, deviceStatus: DeviceStatus = DeviceStatus.PAIRED): UUID {
        val uuid = randomGenerator.uuid()
        val sql = """
            INSERT INTO smart_home.device 
            (uuid, name, provider, status, provider_id, features, created_on, updated_on)
            VALUES (:uuid, :name, :provider, :status, :providerId, :features, :createdOn, :updatedOn)
        """
        val params = mapOf(
            "uuid" to uuid,
            "name" to device.name,
            "provider" to device.provider.name,
            "status" to deviceStatus.name,
            "providerId" to device.providerId,
            "features" to device.features.map { it.name }.toTypedArray(),
            "createdOn" to Timestamp.from(timeService.now()),
            "updatedOn" to null
        )
        jdbcTemplate.update(sql, params)
        return uuid
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
            "updatedOn" to Timestamp.from(timeService.now()),
            "provider" to device.provider.name,
            "providerId" to device.providerId
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
