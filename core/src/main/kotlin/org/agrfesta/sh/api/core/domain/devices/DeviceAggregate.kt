package org.agrfesta.sh.api.core.domain.devices

import java.time.Instant
import java.util.UUID

/**
 * The persisted aggregate for a single device: its base fields plus the relationships our model
 * holds. Today the only relationship is [assignments]; future links (heating schedules, actuator
 * state, sensor history) can be added as new fields without affecting the lean device list.
 */
@Suppress("LongParameterList")
data class DeviceAggregate(
    val uuid: UUID,
    val status: DeviceStatus,
    val deviceProviderId: String,
    val provider: Provider,
    val name: String,
    val features: Set<DeviceFeature>,
    val createdOn: Instant,
    val updatedOn: Instant?,
    val assignments: List<DeviceAreaAssignment>
)

/**
 * A current area assignment of a device, scoped by the [role] under which the device participates
 * in the area.
 */
data class DeviceAreaAssignment(
    val areaUuid: UUID,
    val areaName: String,
    val role: AssignmentRole
)

enum class AssignmentRole { SENSOR, ACTUATOR }
