package org.agrfesta.sh.api.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.verify
import java.util.*
import org.agrfesta.sh.api.domain.aTemperatureInterval
import org.agrfesta.sh.api.domain.anAreaDto
import org.agrfesta.sh.api.domain.anAreaTemperatureSetting
import org.agrfesta.sh.api.domain.areas.TemperatureInterval
import org.agrfesta.sh.api.persistence.AreaDao
import org.agrfesta.sh.api.persistence.AreaNotFoundException
import org.agrfesta.sh.api.persistence.jdbc.dao.TemperatureSettingsDaoJdbcImpl
import org.agrfesta.sh.api.persistence.jdbc.entities.TemperatureIntervalEntity
import org.agrfesta.sh.api.persistence.jdbc.entities.TemperatureSettingEntity
import org.agrfesta.sh.api.persistence.jdbc.repositories.TemperatureIntervalRepository
import org.agrfesta.sh.api.persistence.jdbc.repositories.TemperatureSettingRepository
import org.agrfesta.sh.api.security.SecurityConfig
import org.agrfesta.sh.api.services.heating.HeatingAreasService
import org.agrfesta.sh.api.utils.RandomGenerator
import org.agrfesta.test.mothers.aDailyTime
import org.agrfesta.test.mothers.aRandomTemperature
import org.agrfesta.test.mothers.aRandomUniqueString
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(HeatingAreasController::class)
@ActiveProfiles("test")
@Import(HeatingAreasService::class, TemperatureSettingsDaoJdbcImpl::class, SecurityConfig::class)
class HeatingAreasControllerUnitTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired @MockkBean private val tempSettingsRepo: TemperatureSettingRepository,
    @Autowired @MockkBean private val tempIntervalsRepo: TemperatureIntervalRepository,
    @Autowired @MockkBean private val areaDao: AreaDao,
    @Autowired @MockkBean private val randomGenerator: RandomGenerator
) {
    private val area = anAreaDto()
    private val uuid = UUID.randomUUID()

    init {
        every { areaDao.getAreaById(area.uuid) } returns area
        every { tempSettingsRepo.existsSettingByAreaId(area.uuid) } returns false
        every { tempIntervalsRepo.save(any()) } returns Unit
        every { randomGenerator.uuid() } returns uuid
    }

    ///// createHeatingSchedule ////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `createHeatingSchedule() return 401 when auth is missing`() {
        val responseBody: String = mockMvc.perform(
            post("/heating/areas/${area.uuid}")
                .contentType("application/json")
                .content("""{"defaultTemperature": ${aRandomTemperature()}, "temperatureSchedule": []}"""))
            .andExpect(status().isUnauthorized)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Missing Authorization header"
    }

    @Test fun `createHeatingSchedule() return 401 when token is empty`() {
        val responseBody: String = mockMvc.perform(
            post("/heating/areas/${area.uuid}")
                .header("Authorization", "Bearer ")
                .contentType("application/json")
                .content("""{"defaultTemperature": ${aRandomTemperature()}, "temperatureSchedule": []}"""))
            .andExpect(status().isUnauthorized)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Empty token"
    }

    @Test fun `createHeatingSchedule() return 401 when token is invalid`() {
        val responseBody: String = mockMvc.perform(
            post("/heating/areas/${area.uuid}")
                .header("Authorization", "Bearer ${aRandomUniqueString()}")
                .contentType("application/json")
                .content("""{"defaultTemperature": ${aRandomTemperature()}, "temperatureSchedule": []}"""))
            .andExpect(status().isUnauthorized)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Invalid token"
    }

    @Test fun `createHeatingSchedule() returns 500 when is unable to persist setting on db`() {
        val failure = Exception("setting creation failure")
        every { tempSettingsRepo.save(any()) } throws failure

        val resultContent: String = mockMvc.perform(
            post("/heating/areas/${area.uuid}")
                .contentType("application/json")
                .authenticated()
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

                val resultContent: String = mockMvc.perform(
                    post("/heating/areas/${area.uuid}")
                        .contentType("application/json")
                        .authenticated()
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

                val resultContent: String = mockMvc.perform(
                    post("/heating/areas/${area.uuid}")
                        .contentType("application/json")
                        .authenticated()
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

        mockMvc.perform(
            post("/heating/areas/${area.uuid}")
                .contentType("application/json")
                .authenticated()
                .content("""{"defaultTemperature": ${aRandomTemperature()}, "temperatureSchedule": []}"""))
            .andExpect(status().isCreated)
    }

    @Test fun `createHeatingSchedule() returns 500 when fails to delete previous setting`() {
        val failure = Exception("setting deletion failure")
        every { tempSettingsRepo.existsSettingByAreaId(area.uuid) } returns true
        every { tempSettingsRepo.deleteByByAreaId(area.uuid) } throws failure

        val resultContent: String = mockMvc.perform(
            post("/heating/areas/${area.uuid}")
                .contentType("application/json")
                .authenticated()
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

        val resultContent: String = mockMvc.perform(
            post("/heating/areas/${area.uuid}")
                .contentType("application/json")
                .authenticated()
                .content(requestBody))
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(resultContent, MessageResponse::class.java)
        response.message shouldBe "Unable to persist setting for area '${area.uuid}'!"
        verify(exactly = 2) { tempIntervalsRepo.save(any()) }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///// getHeatingSchedule ///////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `getHeatingSchedule() return 401 when auth is missing`() {
        val responseBody: String = mockMvc.perform(
            get("/heating/areas/${area.uuid}"))
            .andExpect(status().isUnauthorized)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Missing Authorization header"
    }

    @Test fun `getHeatingSchedule() return 401 when token is empty`() {
        val responseBody: String = mockMvc.perform(
            get("/heating/areas/${area.uuid}")
                .header("Authorization", "Bearer "))
            .andExpect(status().isUnauthorized)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Empty token"
    }

    @Test fun `getHeatingSchedule() return 401 when token is invalid`() {
        val responseBody: String = mockMvc.perform(
            get("/heating/areas/${area.uuid}")
                .header("Authorization", "Bearer ${aRandomUniqueString()}"))
            .andExpect(status().isUnauthorized)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Invalid token"
    }

    @Test fun `getHeatingSchedule() returns 404 when area does not exist`() {
        val nonExistentAreaId = UUID.randomUUID()
        every { areaDao.getAreaById(nonExistentAreaId) } throws AreaNotFoundException()

        val resultContent: String = mockMvc.perform(
            get("/heating/areas/$nonExistentAreaId")
                .authenticated())
            .andExpect(status().isNotFound)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(resultContent, MessageResponse::class.java)
        response.message shouldBe "Area with id '$nonExistentAreaId' is missing!"
    }

    @Test fun `getHeatingSchedule() returns 404 when no setting exists for the area`() {
        every { tempSettingsRepo.findSettingByAreaId(area.uuid) } returns null

        val resultContent: String = mockMvc.perform(
            get("/heating/areas/${area.uuid}")
                .authenticated())
            .andExpect(status().isNotFound)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(resultContent, MessageResponse::class.java)
        response.message shouldBe "No heating schedule found for area '${area.uuid}'!"
    }

    @Test fun `getHeatingSchedule() returns 500 on persistence failure`() {
        val failure = Exception("setting fetch failure")
        every { tempSettingsRepo.findSettingByAreaId(area.uuid) } throws failure

        val resultContent: String = mockMvc.perform(
            get("/heating/areas/${area.uuid}")
                .authenticated())
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(resultContent, MessageResponse::class.java)
        response.message shouldBe "Unable to retrieve setting for area '${area.uuid}'!"
    }

    @Test fun `getHeatingSchedule() returns 200 with correct DTO when setting exists`() {
        val setting = anAreaTemperatureSetting(
            areaId = area.uuid,
            temperatureSchedule = setOf(
                aTemperatureInterval(startTime = aDailyTime(hour = 22), endTime = aDailyTime(hour = 6)),
                aTemperatureInterval(startTime = aDailyTime(hour = 6), endTime = aDailyTime(hour = 8))
            )
        )
        every { tempSettingsRepo.findSettingByAreaId(area.uuid) } returns TemperatureSettingEntity(
            uuid = uuid,
            areaUuid = area.uuid,
            defaultTemperature = setting.defaultTemperature
        )
        every { tempIntervalsRepo.findAllBySetting(uuid) } returns setting.temperatureSchedule.map {
            TemperatureIntervalEntity(
                uuid = UUID.randomUUID(),
                settingUuid = uuid,
                startTime = it.startTime,
                endTime = it.endTime,
                temperature = it.temperature
            )
        }

        val resultContent: String = mockMvc.perform(
            get("/heating/areas/${area.uuid}")
                .authenticated())
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        val response: TemperatureSettings = objectMapper.readValue(resultContent, TemperatureSettings::class.java)
        response.defaultTemperature shouldBe setting.defaultTemperature
        response.temperatureSchedule shouldContainExactlyInAnyOrder setting.temperatureSchedule
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///// deleteHeatingSchedule ////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `deleteHeatingSchedule() return 401 when auth is missing`() {
        val responseBody: String = mockMvc.perform(
            delete("/heating/areas/${area.uuid}"))
            .andExpect(status().isUnauthorized)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Missing Authorization header"
    }

    @Test fun `deleteHeatingSchedule() return 401 when token is empty`() {
        val responseBody: String = mockMvc.perform(
            delete("/heating/areas/${area.uuid}")
                .header("Authorization", "Bearer "))
            .andExpect(status().isUnauthorized)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Empty token"
    }

    @Test fun `deleteHeatingSchedule() return 401 when token is invalid`() {
        val responseBody: String = mockMvc.perform(
            delete("/heating/areas/${area.uuid}")
                .header("Authorization", "Bearer ${aRandomUniqueString()}"))
            .andExpect(status().isUnauthorized)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Invalid token"
    }

    @Test fun `deleteHeatingSchedule() returns 500 when is unable to fetch area on db`() {
        val failure = Exception("area fetch failure")
        every { areaDao.getAreaById(area.uuid) } throws failure

        val resultContent: String = mockMvc.perform(
            delete("/heating/areas/${area.uuid}")
                .authenticated())
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(resultContent, MessageResponse::class.java)
        response.message shouldBe "Unable to delete setting for area '${area.uuid}'!"
    }

    @Test fun `deleteHeatingSchedule() returns 500 when is unable to delete setting on db`() {
        val failure = Exception("setting delete failure")
        every { tempSettingsRepo.deleteByByAreaId(area.uuid) } throws failure

        val resultContent: String = mockMvc.perform(
            delete("/heating/areas/${area.uuid}")
                .authenticated())
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(resultContent, MessageResponse::class.java)
        response.message shouldBe "Unable to delete setting for area '${area.uuid}'!"
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
