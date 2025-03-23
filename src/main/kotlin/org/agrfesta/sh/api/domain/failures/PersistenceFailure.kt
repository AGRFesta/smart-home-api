package org.agrfesta.sh.api.domain.failures

data class PersistenceFailure(override val exception: Exception): ExceptionFailure,
    GetDeviceFailure,
    GetAreaFailure,
    AreaCreationFailure,
    GetPersistedCacheEntryFailure,
    TemperatureSettingCreationFailure,
    TemperatureSettingDeletionFailure
