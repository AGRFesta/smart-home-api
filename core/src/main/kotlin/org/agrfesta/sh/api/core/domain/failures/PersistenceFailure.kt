package org.agrfesta.sh.api.core.domain.failures

data class PersistenceFailure(
    val exception: Exception
):  DeviceCreationFailure,
    DeviceUpdateFailure,
    GetPropertyFailure,
    FindPropertyFailure,
    GetHomeDashboardFailure,
    RefreshDevicesFailure,
    TemperatureSettingCreationFailure,
    TemperatureSettingDeletionFailure,
    TemperatureSettingRetrievalFailure
