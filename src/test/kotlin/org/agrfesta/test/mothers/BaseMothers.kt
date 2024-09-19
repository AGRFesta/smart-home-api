package org.agrfesta.test.mothers

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import java.util.*
import kotlin.random.Random

fun aRandomUniqueString(): String = UUID.randomUUID().toString()

fun anUrl(): String = "https://${aRandomUniqueString()}.org"

fun aJsonNode(): JsonNode {
    val mapper = ObjectMapper()
    val rootNode: ObjectNode = mapper.createObjectNode()
    rootNode.put("property", aRandomUniqueString())
    return rootNode
}

fun aRandomIntPercentage(): Int = Random.nextInt(from = 0, until = 101)
fun aRandomTemperature(): Float = Random.nextDouble(from = -100.0, until = 100.0).toFloat()
fun aRandomHumidity(): Int = Random.nextInt(from = 0, until = 101)
