package org.agrfesta.test.mothers

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.agrfesta.sh.api.domain.commons.Percentage
import org.agrfesta.sh.api.domain.devices.Temperature
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*
import kotlin.random.Random

fun aRandomUniqueString(): String = UUID.randomUUID().toString()
fun aRandomPercentage(scale: Int = 10) = Percentage(BigDecimal(Random.nextDouble(0.0, 1.0))
    .setScale(scale, RoundingMode.CEILING)
    .stripTrailingZeros())
fun aRandomIntPercentage(): Int = Random.nextInt(from = 0, until = 101)

fun anUrl(): String = "https://${aRandomUniqueString()}.org"

fun aJsonNode(): JsonNode {
    val mapper = ObjectMapper()
    val rootNode: ObjectNode = mapper.createObjectNode()
    rootNode.put("property", aRandomUniqueString())
    return rootNode
}

fun aRandomTemperature(scale: Int = 10): Temperature = BigDecimal(Random.nextDouble(from = -100.0, until = 100.0))
    .setScale(scale, RoundingMode.CEILING)
    .stripTrailingZeros()
fun aRandomHumidity(): Percentage = aRandomPercentage()
fun aRandomIntHumidity(): Int = aRandomIntPercentage()
