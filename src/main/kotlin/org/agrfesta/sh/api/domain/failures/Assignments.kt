package org.agrfesta.sh.api.domain.failures

sealed interface SensorAssignmentFailure

data object SensorAlreadyAssigned: SensorAssignmentFailure
data object SameAreaAssignment: SensorAssignmentFailure
data object NotASensor: SensorAssignmentFailure
