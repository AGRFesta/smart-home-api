package org.agrfesta.sh.api.providers.switchbot

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode

fun ObjectMapper.aSwitchBotDevicesListResponse(
    statusCode: Int = 100,
    message: String = "message",
    devices: Collection<JsonNode> = emptyList()
): JsonNode {
    val json = """
        {
            "statusCode": $statusCode,
            "message": "$message",
            "body": {
                "deviceList": [],
                "infraredRemoteList": []
            }
        }
    """.trimIndent()
    val arrayNode = createArrayNode()
    devices.forEach { jsonNode ->
        arrayNode.add(jsonNode)
    }
    val node = readTree(json) as ObjectNode
    val body = node.get("body") as ObjectNode
    body.set<ArrayNode>("deviceList", arrayNode)
    return node
}

fun ObjectMapper.aSwitchBotDevicesListSuccessResponse(devices: Collection<JsonNode> = emptyList()) =
    aSwitchBotDevicesListResponse(100, "success", devices)

fun ObjectMapper.aSwitchBotDevice(
    deviceId: String = "deviceId",
    deviceName: String = "deviceName",
    deviceType: String = "deviceType",
    enableCloudService: Boolean = true,
    hubDeviceId: String = "hubDeviceId"
): JsonNode {
    val json = """
        {
            "deviceId": "$deviceId",
            "deviceName": "$deviceName",
            "deviceType": "$deviceType",
            "enableCloudService": $enableCloudService,
            "hubDeviceId": "$hubDeviceId"
        }
    """.trimIndent()
    return readTree(json)
}
