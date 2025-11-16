package org.agrfesta.test.mothers

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.*
import org.agrfesta.sh.api.domain.commons.Percentage
import org.agrfesta.sh.api.domain.commons.RelativeHumidity
import org.agrfesta.sh.api.domain.commons.Temperature
import org.agrfesta.sh.api.domain.commons.ThermoHygroData
import org.agrfesta.sh.api.domain.devices.Provider
import kotlin.random.Random

fun aRandomBoolean(): Boolean = Random.nextBoolean()
fun aRandomUniqueString(): String = UUID.randomUUID().toString()
fun aRandomPercentage(scale: Int = 10) = Percentage(BigDecimal(Random.nextDouble(0.0, 1.0))
    .setScale(scale, RoundingMode.CEILING)
    .stripTrailingZeros())
fun aRandomNonNegativeInt(): Int = Random.nextInt(from = 0, until = Int.MAX_VALUE)
fun aRandomIntPercentage(): Int = Random.nextInt(from = 0, until = 101)
fun aRandomTtl(): Long = Random.nextLong(from = 1, until = 2_628_000) // max five years
fun nowNoMills(): Instant = Instant.now().truncatedTo(ChronoUnit.SECONDS)

fun anUrl(): String = "https://${aRandomUniqueString()}.org"

fun aJsonNode(): JsonNode {
    val mapper = jacksonObjectMapper()
    val rootNode: ObjectNode = mapper.createObjectNode()
    rootNode.put("property", aRandomUniqueString())
    return rootNode
}

fun aProvider() = Provider.entries.random()

fun aRandomTemperature(scale: Int = 10): Temperature = BigDecimal(Random.nextDouble(from = -100.0, until = 100.0))
    .setScale(scale, RoundingMode.CEILING)
    .stripTrailingZeros()
fun aRandomHumidity(): Percentage = aRandomPercentage()
fun aRandomIntHumidity(): Int = aRandomIntPercentage()
fun aRandomThermoHygroData(
    temperature: Temperature = aRandomTemperature(),
    relativeHumidity: RelativeHumidity = aRandomHumidity()
) = ThermoHygroData(temperature, relativeHumidity)

fun aRandomHour(): Int = Random.nextInt(24)
fun aRandomMinute(): Int = Random.nextInt(60)
fun aDailyTime(
    hour: Int = aRandomHour(),
    minutes: Int = aRandomMinute()
): LocalTime = LocalTime.parse("${hour.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}")
