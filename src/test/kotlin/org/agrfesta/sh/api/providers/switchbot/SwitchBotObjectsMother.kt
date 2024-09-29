package org.agrfesta.sh.api.providers.switchbot

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.agrfesta.sh.api.domain.devices.DeviceFeature
import org.agrfesta.sh.api.domain.devices.Temperature
import org.agrfesta.test.mothers.aRandomIntHumidity
import org.agrfesta.test.mothers.aRandomIntPercentage
import org.agrfesta.test.mothers.aRandomTemperature

fun ObjectMapper.aSwitchBotDeviceStatusResponse(
    humidity: Int = aRandomIntHumidity(),
    temperatureText: String? = null,
    temperature: Temperature = aRandomTemperature(),
    deviceType: SwitchBotDeviceType = SwitchBotDeviceType.HUB_MINI,
    battery: Int = aRandomIntPercentage()
): JsonNode = readTree("""
        {
            "statusCode": 100,
            "body": {
                "deviceId": "F4E269B8A0E0",
                "deviceType": "${valueToTree<JsonNode>(deviceType).asText()}",
                "humidity": $humidity,
                "temperature": ${temperatureText ?: temperature},
                "version": "V0.5",
                "battery": $battery
            },
            "message": "success"
        }
    """.trimIndent())

fun Set<DeviceFeature>.toASwitchBotDeviceType(): SwitchBotDeviceType {
    if (contains(DeviceFeature.ACTUATOR)) error("At moment there is no SwitchBot ACTUATOR device mapped")
    if (isEmpty()) return SwitchBotDeviceType.HUB_MINI
    return SwitchBotDeviceType.entries.filter { it.features.contains(DeviceFeature.SENSOR) }.random()
}
