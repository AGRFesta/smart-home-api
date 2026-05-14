package org.agrfesta.sh.api.core.domain.failures

data class PersistenceFailure(
    val exception: Exception
):  AreaFetchFailure,
    DeviceFetchFailure,
    AreaCreationFailure,
    AreaDeletionFailure,
    AreaUpdateFailure,
    DeviceCreationFailure,
    DeviceUpdateFailure,
    GetPropertyFailure,
    FindPropertyFailure,
    GetHomeDashboardFailure,
    RefreshDevicesFailure
