package org.agrfesta.sh.api.core.domain.failures

sealed interface TemperatureSettingCreationFailure

data object OverlappingIntervals : TemperatureSettingCreationFailure

sealed interface TemperatureSettingDeletionFailure

sealed interface TemperatureSettingRetrievalFailure

data object HeatingScheduleRepositoryError :
    TemperatureSettingCreationFailure,
    TemperatureSettingDeletionFailure,
    TemperatureSettingRetrievalFailure
