package org.agrfesta.sh.api.core.domain.failures

import java.util.UUID

sealed interface AreaCreationFailure
sealed interface AreaDeletionFailure
sealed interface AreaUpdateFailure

data object AreaNameConflict: AreaCreationFailure, AreaUpdateFailure

sealed interface AreaFetchFailure: SensorAssignmentFailure, ActuatorAssignmentFailure,
    TemperatureSettingCreationFailure, TemperatureSettingDeletionFailure, TemperatureSettingRetrievalFailure

data class AreaNotFound(
    val missingAreaId: UUID
): AreaFetchFailure, AreaDeletionFailure, AreaUpdateFailure, TemperatureSettingCreationFailure
