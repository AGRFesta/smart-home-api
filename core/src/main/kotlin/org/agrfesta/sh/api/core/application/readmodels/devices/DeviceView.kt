package org.agrfesta.sh.api.core.application.readmodels.devices

import org.agrfesta.sh.api.core.domain.alerts.AlertType
import org.agrfesta.sh.api.core.domain.devices.DeviceModel
import org.agrfesta.sh.api.core.domain.devices.DeviceProviderIdentity
import org.agrfesta.sh.api.core.domain.devices.DeviceStatus
import org.agrfesta.sh.api.core.domain.devices.Provider
import java.time.Instant
import java.util.UUID

/**
 * Read-model for a single device: its base fields plus the relationships our model holds. Today
 * the only relationship is [assignments]; future links (heating schedules, actuator state, sensor
 * history) can be added as new fields without affecting the lean device list.
 *
 * @property activeAlerts the types of the OPEN alerts targeting this device — a minimal read-time
 * projection from the alert store (see `docs/domain/ALERTS.md`). `null` means the lookup failed
 * ("unknown"), never to be confused with an empty set ("no open alerts").
 */
@Suppress("LongParameterList")
data class DeviceView(
    val uuid: UUID,
    val status: DeviceStatus,
    override val deviceProviderId: String,
    override val provider: Provider,
    val name: String,
    val model: DeviceModel,
    val createdOn: Instant,
    val updatedOn: Instant?,
    val assignments: List<DeviceAreaAssignment>,
    val batteryLevel: Int? = null,
    val activeAlerts: Set<AlertType>? = null
) : DeviceProviderIdentity

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
