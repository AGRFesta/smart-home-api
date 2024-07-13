package org.agrfesta.sh.api.providers.switchbot

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import kotlinx.coroutines.runBlocking
import org.agrfesta.sh.api.domain.Device
import org.agrfesta.sh.api.domain.DeviceStatus
import org.agrfesta.sh.api.domain.DevicesProvider
import org.agrfesta.sh.api.domain.Provider
import org.springframework.stereotype.Service

@Service
class SwitchBotService(
    private val devicesClient: SwitchBotDevicesClient,
    private val mapper: ObjectMapper
): DevicesProvider {
    override fun getAllDevices(): Collection<Device> {
        return runBlocking {
            val response = devicesClient.getDevices()
            val devices = response.at("/body/deviceList") as ArrayNode
            devices
                .map { mapper.treeToValue(it, SwitchBotDevice::class.java) }
                .map {
                    Device(
                        providerId = it.deviceId,
                        provider = Provider.SWITCHBOT,
                        status = DeviceStatus.PAIRED,
                        name = it.deviceName
                    )
                }
        }
    }
}
