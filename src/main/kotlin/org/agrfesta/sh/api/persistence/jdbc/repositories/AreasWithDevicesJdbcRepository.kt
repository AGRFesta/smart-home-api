package org.agrfesta.sh.api.persistence.jdbc.repositories

import java.util.*
import org.agrfesta.sh.api.domain.areas.AreaDtoWithDevices
import org.agrfesta.sh.api.domain.devices.DeviceDto
import org.agrfesta.sh.api.domain.devices.DeviceFeature
import org.agrfesta.sh.api.domain.devices.DeviceStatus
import org.agrfesta.sh.api.domain.devices.Provider
import org.springframework.jdbc.core.ResultSetExtractor
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import kotlin.collections.LinkedHashMap

@Service
class AreasWithDevicesJdbcRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {

    fun getAll(): Collection<AreaDtoWithDevices> {
        val sql = """
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

        return jdbcTemplate.query(sql, ResultSetExtractor<Collection<AreaDtoWithDevices>> { rs ->
            // local mutable builder holding separate lists for sensors and actuators
            data class MutableAreaBuilder(
                val uuid: UUID,
                val name: String,
                val isIndoor: Boolean,
                val sensors: MutableList<DeviceDto>,
                val actuators: MutableList<DeviceDto>
            )

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

                val deviceUuid = rs.getString("device_uuid")
                if (deviceUuid != null) {
                    val device = DeviceDto(
                        uuid = UUID.fromString(deviceUuid),
                        name = rs.getString("device_name"),
                        provider = Provider.valueOf(rs.getString("provider")),
                        deviceProviderId = rs.getString("provider_id"),
                        status = DeviceStatus.valueOf(rs.getString("status")),
                        features = parseFeatures(rs.getArray("features"))
                    )

                    val kind = rs.getString("device_kind")
                    if (kind == "ACTUATOR") {
                        builder.actuators.add(device)
                    } else { // treat SENSOR and any other/NULL as sensor
                        builder.sensors.add(device)
                    }
                }
            }

            // map builders to the new AreaDtoWithDevices model
            areaMap.values.map { b ->
                AreaDtoWithDevices(
                    uuid = b.uuid,
                    name = b.name,
                    sensors = b.sensors.toList(),
                    actuators = b.actuators.toList(),
                    isIndoor = b.isIndoor
                )
            }
        }) ?: emptyList()
    }


    private fun parseFeatures(array: java.sql.Array): Set<DeviceFeature> {
        val objArray = array.array as Array<*>
        return objArray.mapNotNull { it?.toString()?.let(DeviceFeature::valueOf) }.toSet()
    }

}
