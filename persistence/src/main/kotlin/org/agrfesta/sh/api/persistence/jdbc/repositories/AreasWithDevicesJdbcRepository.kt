package org.agrfesta.sh.api.persistence.jdbc.repositories

import org.agrfesta.sh.api.core.domain.areas.AreaDtoWithDevices
import org.agrfesta.sh.api.core.domain.devices.Device
import org.agrfesta.sh.api.core.domain.devices.DeviceFeature
import org.agrfesta.sh.api.core.domain.devices.DeviceStatus
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.springframework.jdbc.core.ResultSetExtractor
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import java.sql.ResultSet
import java.util.*

@Service
class AreasWithDevicesJdbcRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {
    private data class MutableAreaBuilder(
        val uuid: UUID,
        val name: String,
        val isIndoor: Boolean,
        val sensors: MutableList<Device>,
        val actuators: MutableList<Device>
    )

    fun getAll(): Collection<AreaDtoWithDevices> =
        jdbcTemplate.query(QUERY, extractAreas()) ?: emptyList()

    private fun extractAreas() = ResultSetExtractor<Collection<AreaDtoWithDevices>> { rs ->
        val areaMap = LinkedHashMap<UUID, MutableAreaBuilder>()
        while (rs.next()) {
            val areaUuid = UUID.fromString(rs.getString("area_uuid"))
            val builder = areaMap.computeIfAbsent(areaUuid) {
                MutableAreaBuilder(
                    uuid = areaUuid,
                    name = rs.getString("area_name"),
                    isIndoor = rs.getBoolean("is_indoor"),
                    sensors = mutableListOf(),
                    actuators = mutableListOf()
                )
            }
            addDeviceToBuilder(rs, builder)
        }
        areaMap.values.map { b ->
            AreaDtoWithDevices(
                uuid = b.uuid,
                name = b.name,
                sensors = b.sensors.toList(),
                actuators = b.actuators.toList(),
                isIndoor = b.isIndoor
            )
        }
    }

    private fun addDeviceToBuilder(rs: ResultSet, builder: MutableAreaBuilder) {
        val deviceUuid = rs.getString("device_uuid") ?: return
        val device = Device(
            uuid = UUID.fromString(deviceUuid),
            name = rs.getString("device_name"),
            provider = Provider.valueOf(rs.getString("provider")),
            deviceProviderId = rs.getString("provider_id"),
            status = DeviceStatus.valueOf(rs.getString("status")),
            features = parseFeatures(rs.getArray("features"))
        )
        if (rs.getString("device_kind") == "ACTUATOR") {
            builder.actuators.add(device)
        } else { // treat SENSOR and any other/NULL as sensor
            builder.sensors.add(device)
        }
    }

    private fun parseFeatures(array: java.sql.Array): Set<DeviceFeature> {
        val objArray = array.array as Array<*>
        return objArray.mapNotNull { it?.toString()?.let(DeviceFeature::valueOf) }.toSet()
    }

    companion object {
        private val QUERY = """
            SELECT
                a.uuid as area_uuid,
                a.name as area_name,
                a.is_indoor,
                d.uuid as device_uuid,
                d.name as device_name,
                d.provider,
                d.provider_id,
                d.status,
                d.features,
                ad.device_kind
            FROM smart_home.area a
            LEFT JOIN (
                SELECT area_uuid, device_uuid, 'SENSOR' AS device_kind FROM smart_home.sensor_assignment
                UNION ALL
                SELECT area_uuid, device_uuid, 'ACTUATOR' AS device_kind FROM smart_home.actuator_assignment
            ) ad ON a.uuid = ad.area_uuid
            LEFT JOIN smart_home.device d ON ad.device_uuid = d.uuid
        """.trimIndent()
    }
}
