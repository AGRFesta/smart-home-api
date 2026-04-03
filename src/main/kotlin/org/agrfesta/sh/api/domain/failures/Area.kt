package org.agrfesta.sh.api.domain.failures

import java.util.UUID

sealed interface AreaCreationFailure
sealed interface AreaDeletionFailure

data object AreaNameConflict: AreaCreationFailure

sealed interface AreaFetchFailure: SensorAssignmentFailure, ActuatorAssignmentFailure,
    TemperatureSettingCreationFailure, TemperatureSettingDeletionFailure, TemperatureSettingRetrievalFailure

data class AreaNotFound(
    val missingAreaId: UUID
): AreaFetchFailure, AreaDeletionFailure, TemperatureSettingCreationFailure
