package org.agrfesta.sh.api.persistence.jdbc.repositories

import org.agrfesta.sh.api.core.domain.devices.AssignmentRole
import org.agrfesta.sh.api.core.domain.devices.DeviceAggregate
import org.agrfesta.sh.api.core.domain.devices.DeviceAreaAssignment
import org.agrfesta.sh.api.persistence.jdbc.utils.findInstant
import org.agrfesta.sh.api.persistence.jdbc.utils.getFeatures
import org.agrfesta.sh.api.persistence.jdbc.utils.getInstant
import org.agrfesta.sh.api.persistence.jdbc.utils.getProvider
import org.agrfesta.sh.api.persistence.jdbc.utils.getStatus
import org.agrfesta.sh.api.persistence.jdbc.utils.getUuid
import org.springframework.jdbc.core.ResultSetExtractor
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.UUID

@Repository
class DeviceAggregateJdbcRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {

    /**
     * Loads the [DeviceAggregate] for [deviceId] — base device fields plus its current area
     * assignments — or `null` when no device matches [deviceId].
     */
    fun findAggregateById(deviceId: UUID): DeviceAggregate? = jdbcTemplate.query(
        QUERY,
        mapOf("deviceUuid" to deviceId),
        extractAggregate()
    )

    private fun extractAggregate() = ResultSetExtractor<DeviceAggregate?> { rs ->
        var base: DeviceAggregate? = null
        val assignments = mutableListOf<DeviceAreaAssignment>()
        while (rs.next()) {
            if (base == null) base = DeviceAggregateRowMapper.mapRow(rs, 0)
            addAssignment(rs, assignments)
        }
        base?.copy(assignments = assignments)
    }

    private fun addAssignment(rs: ResultSet, assignments: MutableList<DeviceAreaAssignment>) {
        val areaUuid = rs.getString("assignment_area_uuid") ?: return
        assignments += DeviceAreaAssignment(
            areaUuid = UUID.fromString(areaUuid),
            areaName = rs.getString("assignment_area_name"),
            role = AssignmentRole.valueOf(rs.getString("assignment_role"))
        )
    }

    companion object {
        private val QUERY = """
            SELECT
                d.uuid,
                d.name,
                d.provider,
                d.provider_id,
                d.status,
                d.features,
                d.created_on,
                d.updated_on,
                asg.area_uuid AS assignment_area_uuid,
                asg.role AS assignment_role,
                ar.name AS assignment_area_name
            FROM smart_home.device d
            LEFT JOIN (
                SELECT device_uuid, area_uuid, 'SENSOR' AS role FROM smart_home.sensor_assignment
                WHERE disconnected_on IS NULL
                UNION ALL
                SELECT device_uuid, area_uuid, 'ACTUATOR' AS role FROM smart_home.actuator_assignment
            ) asg ON d.uuid = asg.device_uuid
            LEFT JOIN smart_home.area ar ON asg.area_uuid = ar.uuid
            WHERE d.uuid = :deviceUuid
            ORDER BY assignment_role, assignment_area_name
        """.trimIndent()
    }
}

object DeviceAggregateRowMapper : RowMapper<DeviceAggregate> {
    override fun mapRow(rs: ResultSet, rowNum: Int) = DeviceAggregate(
        uuid = rs.getUuid("uuid"),
        status = rs.getStatus("status"),
        deviceProviderId = rs.getString("provider_id"),
        provider = rs.getProvider("provider"),
        name = rs.getString("name"),
        features = rs.getFeatures("features"),
        createdOn = rs.getInstant("created_on"),
        updatedOn = rs.findInstant("updated_on"),
        assignments = emptyList()
    )
}
