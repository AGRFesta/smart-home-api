package org.agrfesta.sh.api.providers.switchbot

data class SwitchBotDevice(
    val deviceId: String,
    val deviceName: String,
    val deviceType: SwitchBotDeviceType,
    val enableCloudService: Boolean,
    val hubDeviceId: String,
)
