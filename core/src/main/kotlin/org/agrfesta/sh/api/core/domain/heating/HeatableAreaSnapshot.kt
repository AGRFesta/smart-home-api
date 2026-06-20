package org.agrfesta.sh.api.core.domain.heating

import org.agrfesta.sh.api.core.domain.commons.Temperature
import org.agrfesta.sh.api.core.domain.devices.ActuatorStatus
import java.util.UUID

/**
 * Immutable snapshot of a heatable area, used as input to the pure heating decision functions.
 *
 * @property areaId the area this snapshot refers to.
 * @property currentTemperature the latest reading, or `null` when unavailable.
 * @property targetTemperature the configured target, or `null` when no target applies.
 * @property heaterStatus the shared heater status; [ActuatorStatus.UNDEFINED] when unknown.
 */
data class HeatableAreaSnapshot(
    val areaId: UUID,
    val currentTemperature: Temperature?,
    val targetTemperature: Temperature?,
    val heaterStatus: ActuatorStatus
)
