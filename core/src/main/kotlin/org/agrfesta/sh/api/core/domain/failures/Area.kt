package org.agrfesta.sh.api.core.domain.failures

import java.util.UUID

sealed interface AreaCreationFailure
sealed interface AreaDeletionFailure
sealed interface AreaUpdateFailure
sealed interface GetAreasFailure

data object AreaNameConflict: AreaCreationFailure, AreaUpdateFailure

sealed interface AreaFetchFailure

data class AreaNotFound(
    val missingAreaId: UUID
): AreaFetchFailure, AreaDeletionFailure, AreaUpdateFailure,
    TemperatureSettingCreationFailure, TemperatureSettingDeletionFailure, TemperatureSettingRetrievalFailure,
    SensorAssignmentFailure, ActuatorAssignmentFailure

data object AreaRepositoryError:
    AreaCreationFailure,
    AreaFetchFailure,
    AreaUpdateFailure,
    AreaDeletionFailure,
    GetAreasFailure,
    GetHomeDashboardFailure
