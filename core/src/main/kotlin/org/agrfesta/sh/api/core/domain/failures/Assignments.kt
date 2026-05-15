package org.agrfesta.sh.api.core.domain.failures

import java.util.*
import org.agrfesta.sh.api.core.domain.devices.DeviceFeature

sealed interface SensorAssignmentFailure
sealed interface ActuatorAssignmentFailure

data object SensorAlreadyAssigned: SensorAssignmentFailure

data class NotAnActuator(
    val deviceId: UUID,
    val features: Set<DeviceFeature>
): ActuatorAssignmentFailure

data object AssignmentRepositoryError : SensorAssignmentFailure, ActuatorAssignmentFailure

data object SameAreaAssignment: SensorAssignmentFailure, ActuatorAssignmentFailure

data class NotASensor(
    val deviceId: UUID,
    val features: Set<DeviceFeature>
): SensorAssignmentFailure
