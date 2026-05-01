package org.agrfesta.sh.api.core.domain.devices

data class RefreshDevicesResult(
    val newDevices: Collection<Device> = emptyList(),
    val updatedDevices: Collection<Device> = emptyList(),
    val detachedDevices: Collection<Device> = emptyList()
)
