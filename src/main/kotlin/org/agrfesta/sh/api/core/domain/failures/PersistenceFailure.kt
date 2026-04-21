package org.agrfesta.sh.api.core.domain.failures

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
