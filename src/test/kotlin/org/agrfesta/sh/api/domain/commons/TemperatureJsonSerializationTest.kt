package org.agrfesta.sh.api.domain.commons

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class TemperatureJsonSerializationTest {

    private val objectMapper = ObjectMapper().registerKotlinModule()

    @Test
    fun `should deserialize JSON temperature values with different scales to equal Temperature objects`() {
        val json1 = """{"temp": 20.0}"""
        val json2 = """{"temp": 20}"""
        val json3 = """{"temp": 20.00}"""

        data class TempWrapper(val temp: Temperature)

        val result1 = objectMapper.readValue(json1, TempWrapper::class.java).temp
        val result2 = objectMapper.readValue(json2, TempWrapper::class.java).temp
        val result3 = objectMapper.readValue(json3, TempWrapper::class.java).temp

        result1 shouldBe result2
        result2 shouldBe result3
        result1 shouldBe Temperature("20")
    }

    @Test
    fun `should serialize Temperature to JSON preserving numeric value`() {
        data class TempWrapper(val temp: Temperature)
        
        val wrapper = TempWrapper(Temperature("21.5"))
        val json = objectMapper.writeValueAsString(wrapper)
        
        val deserialized = objectMapper.readValue(json, TempWrapper::class.java)
        deserialized.temp shouldBe Temperature("21.5")
    }

    @Test
    fun `should handle Temperature in complex JSON structures`() {
        data class TemperatureSetting(
            val id: Long,
            val defaultTemperature: Temperature,
            val maxTemperature: Temperature
        )

        val original = TemperatureSetting(
            id = 1,
            defaultTemperature = Temperature("20.0"),
            maxTemperature = Temperature("25.5")
        )

        val json = objectMapper.writeValueAsString(original)
        val deserialized = objectMapper.readValue(json, TemperatureSetting::class.java)

        deserialized.defaultTemperature shouldBe Temperature("20")
        deserialized.maxTemperature shouldBe Temperature("25.5")
    }
}
