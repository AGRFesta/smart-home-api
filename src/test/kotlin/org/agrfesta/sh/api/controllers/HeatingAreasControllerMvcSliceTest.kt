package org.agrfesta.sh.api.controllers

import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.every
import java.util.*
import org.agrfesta.sh.api.domain.anAreaTemperatureSetting
import org.agrfesta.sh.api.domain.aTemperatureInterval
import org.agrfesta.sh.api.core.domain.failures.AreaNotFound
import org.agrfesta.sh.api.core.domain.failures.OverlappingIntervals
import org.agrfesta.sh.api.core.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.security.SecurityConfig
import org.agrfesta.sh.api.services.heating.HeatingAreasService
import org.agrfesta.test.mothers.aDailyTime
import org.agrfesta.test.mothers.aRandomTemperature
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
@Import(SecurityConfig::class)
@ActiveProfiles("test")
class HeatingAreasControllerMvcSliceTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired @MockkBean private val heatingAreasService: HeatingAreasService
) {
    private val authTestSupport = AuthTestSupport(mockMvc, objectMapper)

    ///// createHeatingSchedule ////////////////////////////////////////////////////////////////////////////////////////

    @TestFactory fun `createHeatingSchedule() auth tests`() = authTestSupport.dynamicTestsBy {
        val temp = aRandomTemperature().value
        post("/heating/areas/${UUID.randomUUID()}")
            .contentType("application/json")
            .content("""{"defaultTemperature": $temp, "temperatureSchedule": []}""")
    }

    @Test fun `createHeatingSchedule() returns 400 when intervals overlap`() {
        val areaId = UUID.randomUUID()
        val temp = aRandomTemperature().value
        every { heatingAreasService.createSetting(any()) } returns OverlappingIntervals.left()

        val responseBody: String = mockMvc.perform(
            post("/heating/areas/$areaId")
                .contentType("application/json")
                .authenticated()
                .content("""{"defaultTemperature": $temp, "temperatureSchedule": []}"""))
            .andExpect(status().isBadRequest)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "A couple of intervals overlaps, this is not allowed!"
    }

    @Test fun `createHeatingSchedule() returns 404 when area is not found`() {
        val areaId = UUID.randomUUID()
        val temp = aRandomTemperature().value
        every { heatingAreasService.createSetting(any()) } returns AreaNotFound(areaId).left()

        val responseBody: String = mockMvc.perform(
            post("/heating/areas/$areaId")
                .contentType("application/json")
                .authenticated()
                .content("""{"defaultTemperature": $temp, "temperatureSchedule": []}"""))
            .andExpect(status().isNotFound)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Area with id '$areaId' is missing!"
    }

    @Test fun `createHeatingSchedule() returns 500 when persistence fails`() {
        val areaId = UUID.randomUUID()
        val temp = aRandomTemperature().value
        every { heatingAreasService.createSetting(any()) } returns
            PersistenceFailure(Exception("db error")).left()

        val responseBody: String = mockMvc.perform(
            post("/heating/areas/$areaId")
                .contentType("application/json")
                .authenticated()
                .content("""{"defaultTemperature": $temp, "temperatureSchedule": []}"""))
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Unable to persist setting for area '$areaId'!"
    }

    @Test fun `createHeatingSchedule() returns 201 on success`() {
        val areaId = UUID.randomUUID()
        val temp = aRandomTemperature().value
        every { heatingAreasService.createSetting(any()) } returns Unit.right()

        val responseBody: String = mockMvc.perform(
            post("/heating/areas/$areaId")
                .contentType("application/json")
                .authenticated()
                .content("""{"defaultTemperature": $temp, "temperatureSchedule": []}"""))
            .andExpect(status().isCreated)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Successfully created heating schedule for area with id '$areaId'!"
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    ///// getHeatingSchedule ///////////////////////////////////////////////////////////////////////////////////////////

    @TestFactory fun `getHeatingSchedule() auth tests`() = authTestSupport.dynamicTestsBy {
        get("/heating/areas/${UUID.randomUUID()}")
    }

    @Test fun `getHeatingSchedule() returns 404 when area is not found`() {
        val areaId = UUID.randomUUID()
        every { heatingAreasService.findAreaSetting(areaId) } returns AreaNotFound(areaId).left()

        val responseBody: String = mockMvc.perform(
            get("/heating/areas/$areaId")
                .authenticated())
            .andExpect(status().isNotFound)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Area with id '$areaId' is missing!"
    }

    @Test fun `getHeatingSchedule() returns 500 when persistence fails`() {
        val areaId = UUID.randomUUID()
        every { heatingAreasService.findAreaSetting(areaId) } returns
            PersistenceFailure(Exception("db error")).left()

        val responseBody: String = mockMvc.perform(
            get("/heating/areas/$areaId")
                .authenticated())
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Unable to retrieve setting for area '$areaId'!"
    }

    @Test fun `getHeatingSchedule() returns 204 when no setting exists for the area`() {
        val areaId = UUID.randomUUID()
        every { heatingAreasService.findAreaSetting(areaId) } returns null.right()

        mockMvc.perform(
            get("/heating/areas/$areaId")
                .authenticated())
            .andExpect(status().isNoContent)
    }

    @Test fun `getHeatingSchedule() returns 200 with setting DTO when setting exists`() {
        val areaId = UUID.randomUUID()
        val setting = anAreaTemperatureSetting(
            areaId = areaId,
            temperatureSchedule = setOf(
                aTemperatureInterval(startTime = aDailyTime(hour = 8), endTime = aDailyTime(hour = 22))
            )
        )
        every { heatingAreasService.findAreaSetting(areaId) } returns setting.right()

        val responseBody: String = mockMvc.perform(
            get("/heating/areas/$areaId")
                .authenticated())
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        val response: TemperatureSettings = objectMapper.readValue(responseBody, TemperatureSettings::class.java)
        response.defaultTemperature shouldBe setting.defaultTemperature
        response.temperatureSchedule shouldContainExactlyInAnyOrder setting.temperatureSchedule
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    ///// deleteHeatingSchedule ////////////////////////////////////////////////////////////////////////////////////////

    @TestFactory fun `deleteHeatingSchedule() auth tests`() = authTestSupport.dynamicTestsBy {
        delete("/heating/areas/${UUID.randomUUID()}")
    }

    @Test fun `deleteHeatingSchedule() returns 404 when area is not found`() {
        val areaId = UUID.randomUUID()
        every { heatingAreasService.deleteSetting(areaId) } returns AreaNotFound(areaId).left()

        val responseBody: String = mockMvc.perform(
            delete("/heating/areas/$areaId")
                .authenticated())
            .andExpect(status().isNotFound)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Area with id '$areaId' is missing!"
    }

    @Test fun `deleteHeatingSchedule() returns 500 when persistence fails`() {
        val areaId = UUID.randomUUID()
        every { heatingAreasService.deleteSetting(areaId) } returns
            PersistenceFailure(Exception("db error")).left()

        val responseBody: String = mockMvc.perform(
            delete("/heating/areas/$areaId")
                .authenticated())
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Unable to delete setting for area '$areaId'!"
    }

    @Test fun `deleteHeatingSchedule() returns 200 on success`() {
        val areaId = UUID.randomUUID()
        every { heatingAreasService.deleteSetting(areaId) } returns Unit.right()

        val responseBody: String = mockMvc.perform(
            delete("/heating/areas/$areaId")
                .authenticated())
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Successfully deleted heating schedule for area with id '$areaId'!"
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

}
