package org.agrfesta.sh.api.domain.failures

sealed interface SensorAssignmentFailure

data object SensorAlreadyAssigned: SensorAssignmentFailure
data object NotASensor: SensorAssignmentFailure

sealed interface ActuatorAssignmentFailure

data object NotAnActuator: ActuatorAssignmentFailure

data object SameAreaAssignment: SensorAssignmentFailure, ActuatorAssignmentFailure
