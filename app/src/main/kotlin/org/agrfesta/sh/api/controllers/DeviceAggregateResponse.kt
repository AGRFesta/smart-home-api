package org.agrfesta.sh.api.controllers

import org.agrfesta.sh.api.core.application.devices.DeviceModelCatalog
import org.agrfesta.sh.api.core.domain.alerts.AlertType
import org.agrfesta.sh.api.core.domain.devices.AssignmentRole
import org.agrfesta.sh.api.core.domain.devices.DeviceAggregate
import org.agrfesta.sh.api.core.domain.devices.DeviceFeature
import org.agrfesta.sh.api.core.domain.devices.DeviceStatus
import org.agrfesta.sh.api.core.domain.devices.Provider
import java.time.Instant
import java.util.UUID

/**
 * Per-device detail: the base device fields plus the relationships our model holds. Today the only
 * relationship is [assignments]; future links can be added as new fields without affecting the lean
 * device list.
 *
 * @property activeAlerts the types of the device's OPEN alerts; `null` when the alert lookup failed
 * ("unknown"), empty when no alert is open. For the full alert detail clients follow `GET /alerts`.
 */
@Suppress("LongParameterList")
data class DeviceAggregateResponse(
    val uuid: UUID,
    val name: String,
    val provider: Provider,
    val deviceProviderId: String,
    val status: DeviceStatus,
    val features: Set<DeviceFeature>,
    val createdOn: Instant,
    val updatedOn: Instant?,
    val assignments: List<AssignmentResponse>,
    val batteryLevel: Int? = null,
    val activeAlerts: Set<AlertType>? = null
)

data class AssignmentResponse(
    val areaUuid: UUID,
    val areaName: String,
    val role: AssignmentRole
)

fun DeviceAggregate.toResponse(catalog: DeviceModelCatalog) = DeviceAggregateResponse(
    uuid = uuid,
    name = name,
    provider = provider,
    deviceProviderId = deviceProviderId,
    status = status,
    features = catalog.rolesOf(model),
    createdOn = createdOn,
    updatedOn = updatedOn,
    assignments = assignments.map { AssignmentResponse(it.areaUuid, it.areaName, it.role) },
    batteryLevel = batteryLevel,
    activeAlerts = activeAlerts
)
