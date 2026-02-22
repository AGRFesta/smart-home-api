package org.agrfesta.sh.api.domain.commons

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.matchers.shouldBe
import org.agrfesta.sh.api.controllers.AbstractIntegrationTest
import org.agrfesta.sh.api.controllers.TemperatureSettings
import org.agrfesta.sh.api.domain.TemperatureInterval
import org.agrfesta.sh.api.domain.anArea
import org.agrfesta.sh.api.persistence.AreaDao
import org.agrfesta.sh.api.persistence.TemperatureSettingsDao
import org.agrfesta.test.mothers.aDailyTime
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.junit.jupiter.Container

class TemperatureIntegrationTest(
    @Autowired private val areasDao: AreaDao,
    @Autowired private val tempSettingsDao: TemperatureSettingsDao,
    @Autowired private val objectMapper: ObjectMapper
): AbstractIntegrationTest() {

    companion object {
        @Container
        @ServiceConnection
        val postgres = createPostgresContainer()

        @Container
        @ServiceConnection
        val redis = createRedisContainer()
    }

    @Test
    fun `JSON deserialization with different scales maintains equality`() {
        val json1 = """{"defaultTemperature": 20.0, "temperatureSchedule": []}"""
        val json2 = """{"defaultTemperature": 20, "temperatureSchedule": []}"""
        
        val settings1 = objectMapper.readValue(json1, TemperatureSettings::class.java)
        val settings2 = objectMapper.readValue(json2, TemperatureSettings::class.java)
        
        settings1.defaultTemperature shouldBe settings2.defaultTemperature
        settings1.defaultTemperature shouldBe Temperature("20.0")
        settings2.defaultTemperature shouldBe Temperature("20")
    }

    @Test
    fun `JSON serialization and deserialization round-trip`() {
        val original = TemperatureSettings(
            defaultTemperature = Temperature("21.5"),
            temperatureSchedule = listOf(
                TemperatureInterval(
                    temperature = Temperature("20.00"),
                    startTime = aDailyTime(hour = 8),
                    endTime = aDailyTime(hour = 18)
                )
            )
        )
        
        val json = objectMapper.writeValueAsString(original)
        val deserialized = objectMapper.readValue(json, TemperatureSettings::class.java)
        
        deserialized.defaultTemperature shouldBe original.defaultTemperature
        deserialized.temperatureSchedule.first().temperature shouldBe Temperature("20")
    }
}
