package org.agrfesta.sh.api.controllers

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.runBlocking
import org.agrfesta.sh.api.providers.switchbot.SwitchBotDevicesClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/switchbot")
class SwitchBotController(
    private val switchBotDevicesClient: SwitchBotDevicesClient
) {

    @GetMapping("/devices/{deviceId}/status")
    fun getDeviceStatus(@PathVariable deviceId: String): JsonNode =
        runBlocking { switchBotDevicesClient.getDeviceStatus(deviceId) }

    @GetMapping("/devices")
    fun getDevices(): JsonNode =
        runBlocking { switchBotDevicesClient.getDevices() }

}
