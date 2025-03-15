package org.agrfesta.sh.api.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.verify
import java.util.*
import org.agrfesta.sh.api.domain.TemperatureInterval
import org.agrfesta.sh.api.domain.aTemperatureInterval
import org.agrfesta.sh.api.domain.anArea
import org.agrfesta.sh.api.domain.anAreaTemperatureSetting
import org.agrfesta.sh.api.persistence.jdbc.dao.TemperatureSettingsDaoJdbcImpl
import org.agrfesta.sh.api.persistence.jdbc.repositories.TemperatureIntervalRepository
import org.agrfesta.sh.api.persistence.jdbc.repositories.TemperatureSettingRepository
import org.agrfesta.sh.api.services.HeatingAreasService
import org.agrfesta.sh.api.utils.RandomGenerator
import org.agrfesta.test.mothers.aDailyTime
import org.agrfesta.test.mothers.aRandomTemperature
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(HeatingAreasController::class)
@ActiveProfiles("test")
@Import(HeatingAreasService::class, TemperatureSettingsDaoJdbcImpl::class)
class HeatingAreasControllerUnitTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired @MockkBean private val tempSettingsRepo: TemperatureSettingRepository,
    @Autowired @MockkBean private val tempIntervalsRepo: TemperatureIntervalRepository,
    @Autowired @MockkBean private val randomGenerator: RandomGenerator
) {
    private val area = anArea()
    private val uuid = UUID.randomUUID()

    init {
        every { tempSettingsRepo.existsSettingByAreaId(area.uuid) } returns false
        every { tempIntervalsRepo.save(any()) } returns Unit
        every { randomGenerator.uuid() } returns uuid
    }

    ///// createHeatingSchedule ////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `createHeatingSchedule() returns 500 when is unable to persist setting on db`() {
        val failure = Exception("setting creation failure")
        every { tempSettingsRepo.save(any()) } throws failure

        val resultContent: String = mockMvc.perform(post("/heating/areas/${area.uuid}")
            .contentType("application/json")
            .content("""{"defaultTemperature": ${aRandomTemperature()}, "temperatureSchedule": []}"""))
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(resultContent, MessageResponse::class.java)
        response.message shouldBe "Unable to persist setting for area '${area.uuid}'!"
    }

    @TestFactory
    fun `createHeatingSchedule() returns 201 when there are no interval overlaps`() = listOf(
        listOf(),
        listOf(aTemperatureInterval(startTime = aDailyTime(hour = 22), endTime = aDailyTime(hour = 3))),
        listOf(
            aTemperatureInterval(startTime = aDailyTime(hour = 22), endTime = aDailyTime(hour = 3, minutes = 0)),
            aTemperatureInterval(startTime = aDailyTime(hour = 3, minutes = 0), endTime = aDailyTime(hour = 17))
        ),
        listOf(
            aTemperatureInterval(startTime = aDailyTime(hour = 22), endTime = aDailyTime(hour = 3)),
            aTemperatureInterval(startTime = aDailyTime(hour = 4), endTime = aDailyTime(hour = 17))
        ),
        listOf(
            aTemperatureInterval(startTime = aDailyTime(hour = 1), endTime = aDailyTime(hour = 2)),
            aTemperatureInterval(startTime = aDailyTime(hour = 3), endTime = aDailyTime(hour = 4)),
            aTemperatureInterval(startTime = aDailyTime(hour = 5), endTime = aDailyTime(hour = 6)),
            aTemperatureInterval(startTime = aDailyTime(hour = 7), endTime = aDailyTime(hour = 8)),
            aTemperatureInterval(startTime = aDailyTime(hour = 9), endTime = aDailyTime(hour = 10))
        ),
        listOf(
            aTemperatureInterval(startTime = aDailyTime(hour = 0, minutes = 0), endTime = aDailyTime(hour = 4)),
            aTemperatureInterval(startTime = aDailyTime(hour = 5), endTime = aDailyTime(hour = 0, minutes = 0))
        ),
        listOf(
            aTemperatureInterval(startTime = aDailyTime(hour = 1, minutes = 0), endTime = aDailyTime(hour = 4)),
            aTemperatureInterval(startTime = aDailyTime(hour = 5), endTime = aDailyTime(hour = 1, minutes = 0))
        )
    )
        .map { intervals ->
            dynamicTest("$intervals") {
                every { tempSettingsRepo.save(any()) } returns UUID.randomUUID()
                val body = """
                    {
                        "defaultTemperature": ${aRandomTemperature()},
                        "temperatureSchedule": [${intervals.joinToString(separator = ",") { it.toJson() }}]
                    }
                """.trimIndent()

                val resultContent: String = mockMvc.perform(post("/heating/areas/${area.uuid}")
                    .contentType("application/json")
                    .content(body))
                    .andExpect(status().isCreated)
                    .andReturn().response.contentAsString

                val response: MessageResponse = objectMapper.readValue(resultContent, MessageResponse::class.java)
                response.message shouldBe "Successfully created heating schedule for area with id '${area.uuid}'!"
            }
        }

    @TestFactory
    fun `createHeatingSchedule() returns 400 when an interval overlaps with another`() = listOf(
        listOf(
            aTemperatureInterval(startTime = aDailyTime(hour = 0), endTime = aDailyTime(hour = 9)),
            aTemperatureInterval(startTime = aDailyTime(hour = 5), endTime = aDailyTime(hour = 6))
        ),
        listOf(
            aTemperatureInterval(startTime = aDailyTime(hour = 22), endTime = aDailyTime(hour = 3)),
            aTemperatureInterval(startTime = aDailyTime(hour = 10), endTime = aDailyTime(hour = 23))
        ),
        listOf(
            aTemperatureInterval(startTime = aDailyTime(hour = 22), endTime = aDailyTime(hour = 3)),
            aTemperatureInterval(startTime = aDailyTime(hour = 1), endTime = aDailyTime(hour = 23))
        ),
        listOf(
            aTemperatureInterval(startTime = aDailyTime(hour = 22), endTime = aDailyTime(hour = 3)),
            aTemperatureInterval(startTime = aDailyTime(hour = 1), endTime = aDailyTime(hour = 17))
        ),
        listOf(
            aTemperatureInterval(startTime = aDailyTime(hour = 1), endTime = aDailyTime(hour = 2)),
            aTemperatureInterval(startTime = aDailyTime(hour = 3), endTime = aDailyTime(hour = 4)),
            aTemperatureInterval(startTime = aDailyTime(hour = 5), endTime = aDailyTime(hour = 6)),
            aTemperatureInterval(startTime = aDailyTime(hour = 7), endTime = aDailyTime(hour = 8)),
            aTemperatureInterval(startTime = aDailyTime(hour = 1), endTime = aDailyTime(hour = 17))
        ),
        listOf(
            aTemperatureInterval(startTime = aDailyTime(hour = 1), endTime = aDailyTime(hour = 22)),
            aTemperatureInterval(startTime = aDailyTime(hour = 3), endTime = aDailyTime(hour = 20)),
            aTemperatureInterval(startTime = aDailyTime(hour = 5), endTime = aDailyTime(hour = 6))
        ),
        listOf(
            aTemperatureInterval(startTime = aDailyTime(hour = 0), endTime = aDailyTime(hour = 9)),
            aTemperatureInterval(startTime = aDailyTime(hour = 5), endTime = aDailyTime(hour = 16))
        )
    )
        .map { intervals ->
            dynamicTest("$intervals") {
                val body = """
                    {
                        "defaultTemperature": ${aRandomTemperature()},
                        "temperatureSchedule": [${intervals.joinToString(separator = ",") { it.toJson() }}]
                    }
                """.trimIndent()

                val resultContent: String = mockMvc.perform(post("/heating/areas/${area.uuid}")
                    .contentType("application/json")
                    .content(body))
                    .andExpect(status().isBadRequest)
                    .andReturn().response.contentAsString

                val response: MessageResponse = objectMapper.readValue(resultContent, MessageResponse::class.java)
                response.message shouldBe "A couple of intervals overlaps, this is not allowed!"
            }
        }

    @Test fun `createHeatingSchedule() ignores failures checking area's setting existence`() {
        val failure = Exception("setting existence check failure")
        every { tempSettingsRepo.existsSettingByAreaId(area.uuid) } throws failure
        every { tempSettingsRepo.save(any()) } returns uuid

        mockMvc.perform(post("/heating/areas/${area.uuid}")
            .contentType("application/json")
            .content("""{"defaultTemperature": ${aRandomTemperature()}, "temperatureSchedule": []}"""))
            .andExpect(status().isCreated)
    }

    @Test fun `createHeatingSchedule() returns 500 when fails to delete previous setting`() {
        val failure = Exception("setting deletion failure")
        every { tempSettingsRepo.existsSettingByAreaId(area.uuid) } returns true
        every { tempSettingsRepo.deleteByByAreaId(area.uuid) } throws failure

        val resultContent: String = mockMvc.perform(post("/heating/areas/${area.uuid}")
            .contentType("application/json")
            .content("""{"defaultTemperature": ${aRandomTemperature()}, "temperatureSchedule": []}"""))
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(resultContent, MessageResponse::class.java)
        response.message shouldBe "Unable to persist setting for area '${area.uuid}'!"
    }

    @Test fun `createHeatingSchedule() returns 500 when fails to persist a setting interval`() {
        val setting = anAreaTemperatureSetting(
            areaId = area.uuid,
            temperatureSchedule = setOf(
                aTemperatureInterval(startTime = aDailyTime(hour = 1), endTime = aDailyTime(hour = 3)),
                aTemperatureInterval(startTime = aDailyTime(hour = 5), endTime = aDailyTime(hour = 7)),
                aTemperatureInterval(startTime = aDailyTime(hour = 10), endTime = aDailyTime(hour = 20))
            )
        )
        val requestBody = objectMapper.writeValueAsString(setting)
        val failure = Exception("interval creation failure")
        every { tempSettingsRepo.save(any()) } returns uuid
        every { tempIntervalsRepo.save(any()) } returns Unit andThenThrows failure

        val resultContent: String = mockMvc.perform(post("/heating/areas/${area.uuid}")
            .contentType("application/json")
            .content(requestBody))
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(resultContent, MessageResponse::class.java)
        response.message shouldBe "Unable to persist setting for area '${area.uuid}'!"
        verify(exactly = 2) { tempIntervalsRepo.save(any()) }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private fun TemperatureInterval.toJson() = """
            {
                "temperature": $temperature,
                "startTime": "$startTime",
                "endTime": "$endTime"
            }
        """.trimIndent()

}
