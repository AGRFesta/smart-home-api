package org.agrfesta.sh.api.providers.switchbot

import kotlinx.coroutines.runBlocking
import org.agrfesta.sh.api.domain.DevicesProvider
import org.agrfesta.sh.api.domain.ProviderDevice
import org.springframework.stereotype.Service

@Service
class SwitchBotService(
    private val devicesClient: SwitchBotDevicesClient
): DevicesProvider {
    override fun getAllDevices(): Collection<ProviderDevice> {
        return runBlocking {
            devicesClient.getDevices()
        }
    }
}
