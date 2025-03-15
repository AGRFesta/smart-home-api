package org.agrfesta.sh.api.domain.failures

sealed interface GetAreaFailure: SensorAssignmentFailure, ActuatorAssignmentFailure
sealed interface AreaCreationFailure

data object AreaNotFound: GetAreaFailure, TemperatureSettingCreationFailure
data object AreaNameConflict: AreaCreationFailure
