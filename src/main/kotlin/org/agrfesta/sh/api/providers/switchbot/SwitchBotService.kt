package org.agrfesta.sh.api.providers.switchbot

import kotlinx.coroutines.runBlocking
import org.agrfesta.sh.api.domain.Device
import org.agrfesta.sh.api.domain.DevicesProvider
import org.springframework.stereotype.Service

@Service
class SwitchBotService(
    private val devicesClient: SwitchBotDevicesClient
): DevicesProvider {
    override fun getAllDevices(): Collection<Device> {
        return runBlocking {
            devicesClient.getDevices()
        }
    }
}
