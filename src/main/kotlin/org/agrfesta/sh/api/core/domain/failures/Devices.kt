package org.agrfesta.sh.api.core.domain.failures

import java.util.UUID
import org.agrfesta.sh.api.core.domain.devices.DeviceDto

/**
 * Groups all causes of a failure fetching a [DeviceDto].
 */
sealed interface GetDeviceFailure: SensorAssignmentFailure, ActuatorAssignmentFailure

sealed interface DeviceFetchFailure: SensorAssignmentFailure, ActuatorAssignmentFailure
sealed interface DeviceCreationFailure
sealed interface DeviceUpdateFailure

data class DeviceNotFound(
    val missingDeviceId: UUID
): DeviceFetchFailure, DeviceUpdateFailure
