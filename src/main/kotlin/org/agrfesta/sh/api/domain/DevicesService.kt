package org.agrfesta.sh.api.domain

import org.springframework.stereotype.Service

@Service
class DevicesService {

    fun refresh(providersDevices: Collection<ProviderDevice>, devices: Collection<Device>): DevicesRefreshResult {
        return DevicesRefreshResult(
            newDevices = providersDevices
                .filter { devices.find(it.id) == null }
                .map { it.toDevice() },
            updatedDevices = providersDevices
                .mapNotNull { devices.find(it.id)?.copy(name = it.name) },
            detachedDevices = devices
                .filter { providersDevices.find(it.providerId) == null }
        )
    }

    private fun Collection<Device>.find(providerId: String): Device? = firstOrNull { it.providerId == providerId }
    private fun Collection<ProviderDevice>.find(providerId: String): ProviderDevice? =
        firstOrNull { it.id == providerId }

}

data class DevicesRefreshResult(
    val newDevices: Collection<Device> = emptyList(),
    val updatedDevices: Collection<Device> = emptyList(),
    val detachedDevices: Collection<Device> = emptyList()
)
