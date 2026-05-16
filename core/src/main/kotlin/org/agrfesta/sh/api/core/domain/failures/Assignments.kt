package org.agrfesta.sh.api.core.domain.failures

import java.util.*
import org.agrfesta.sh.api.core.domain.devices.DeviceFeature

sealed interface SensorAssignmentFailure
sealed interface ActuatorAssignmentFailure
sealed interface SensorUnassignFailure
sealed interface ActuatorUnassignFailure

data object SensorNotAssigned : SensorUnassignFailure
data object ActuatorNotAssigned : ActuatorUnassignFailure

data object SensorAlreadyAssigned: SensorAssignmentFailure

data class NotAnActuator(
    val deviceId: UUID,
    val features: Set<DeviceFeature>
): ActuatorAssignmentFailure

data object AssignmentRepositoryError : SensorAssignmentFailure, ActuatorAssignmentFailure,
    SensorUnassignFailure, ActuatorUnassignFailure

data object SameAreaAssignment: SensorAssignmentFailure, ActuatorAssignmentFailure

data class NotASensor(
    val deviceId: UUID,
    val features: Set<DeviceFeature>
): SensorAssignmentFailure
