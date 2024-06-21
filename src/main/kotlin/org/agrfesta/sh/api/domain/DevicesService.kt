package org.agrfesta.sh.api.domain

import org.springframework.stereotype.Service

@Service
class DevicesService {

    fun refresh(providersDevices: Collection<Device>, devices: Collection<Device>): DevicesRefreshResult {
        return DevicesRefreshResult(
            newDevices = providersDevices
                .filter { devices.find(it.providerId) == null },
            updatedDevices = providersDevices
                .mapNotNull { devices.find(it.providerId)?.copy(name = it.name, status = DeviceStatus.PAIRED) },
            detachedDevices = devices
                .filter { providersDevices.find(it.providerId) == null }
                .map { it.copy(status = DeviceStatus.DETACHED) }
        )
    }

    private fun Collection<Device>.find(providerId: String): Device? = firstOrNull { it.providerId == providerId }

}

data class DevicesRefreshResult(
    val newDevices: Collection<Device> = emptyList(),
    val updatedDevices: Collection<Device> = emptyList(),
    val detachedDevices: Collection<Device> = emptyList()
)
