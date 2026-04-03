package org.agrfesta.sh.api.domain.failures

data class PersistenceFailure(
    override val exception: Exception
):  ExceptionFailure,
    AreaFetchFailure,
    DeviceFetchFailure,
    AreaCreationFailure,
    AreaDeletionFailure,
    DeviceCreationFailure,
    DeviceUpdateFailure,
    GetPersistedCacheEntryFailure,
    FindPersistedCacheEntryFailure
