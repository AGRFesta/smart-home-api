package org.agrfesta.sh.api.controllers

import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import java.util.*
import org.agrfesta.sh.api.core.application.ports.inbounds.DeleteHeatingScheduleUseCase
import org.agrfesta.sh.api.core.application.ports.inbounds.GetHeatingScheduleUseCase
import org.agrfesta.sh.api.core.application.ports.inbounds.ReplaceHeatingScheduleUseCase
import org.agrfesta.sh.api.core.domain.areas.HeatingScheduleDto
import org.agrfesta.sh.api.core.domain.areas.IntervalDto
import org.agrfesta.sh.api.core.domain.commons.Temperature
import org.agrfesta.sh.api.core.domain.failures.AreaNotFound
import org.agrfesta.sh.api.core.domain.failures.OverlappingIntervals
import org.agrfesta.sh.api.core.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.security.SecurityConfig
import org.agrfesta.test.mothers.aDailyTime
import org.agrfesta.test.mothers.aRandomTemperature
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.ProblemDetail
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestConstructor
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(AreaHeatingScheduleController::class)
@Import(SecurityConfig::class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@ActiveProfiles("test")
class AreaHeatingScheduleControllerMvcSliceTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    @MockkBean private val deleteHeatingScheduleUseCase: DeleteHeatingScheduleUseCase,
    @MockkBean private val replaceHeatingScheduleUseCase: ReplaceHeatingScheduleUseCase,
    @MockkBean private val getHeatingScheduleUseCase: GetHeatingScheduleUseCase
) {
    private val authTestSupport = AuthTestSupport(mockMvc, objectMapper)

    ///// getHeatingSchedule ///////////////////////////////////////////////////////////////////////////////////////////

    @TestFactory fun `getHeatingSchedule() auth tests`() = authTestSupport.dynamicTestsBy {
        get("/areas/${UUID.randomUUID()}/heating-schedule")
    }

    @Test fun `getHeatingSchedule() returns 200 with schedule when area has a heating schedule`() {
        val areaId = UUID.randomUUID()
        val returnedInterval = IntervalDto(
            temperature = aRandomTemperature(),
            startTime = aDailyTime(hour = 8),
            endTime = aDailyTime(hour = 22)
        )
        val returnedSchedule = HeatingScheduleDto(
            defaultTemperature = aRandomTemperature(),
            intervals = listOf(returnedInterval)
        )
        every { getHeatingScheduleUseCase.execute(areaId) } returns returnedSchedule.right()

        val responseBody: String = mockMvc.perform(
            get("/areas/$areaId/heating-schedule").authenticated())
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        val response: HeatingScheduleResponse = objectMapper
            .readValue(responseBody, HeatingScheduleResponse::class.java)
        response.defaultTemperature shouldBe returnedSchedule.defaultTemperature
        response.intervals shouldHaveSize 1
        response.intervals[0].temperature shouldBe returnedInterval.temperature
        response.intervals[0].startTime shouldBe returnedInterval.startTime
        response.intervals[0].endTime shouldBe returnedInterval.endTime
    }

    @Test fun `getHeatingSchedule() returns 200 with empty default structure when area has no schedule`() {
        val areaId = UUID.randomUUID()
        every { getHeatingScheduleUseCase.execute(areaId) } returns null.right()

        val responseBody: String = mockMvc.perform(
            get("/areas/$areaId/heating-schedule").authenticated())
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        val response: HeatingScheduleResponse = objectMapper
            .readValue(responseBody, HeatingScheduleResponse::class.java)
        response.defaultTemperature shouldBe Temperature.of("20.0")
        response.intervals shouldHaveSize 0
    }

    @Test fun `getHeatingSchedule() returns 404 when area is not found`() {
        val areaId = UUID.randomUUID()
        every { getHeatingScheduleUseCase.execute(areaId) } returns AreaNotFound(areaId).left()

        val responseBody: String = mockMvc.perform(
            get("/areas/$areaId/heating-schedule").authenticated())
            .andExpect(status().isNotFound)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Area with id '$areaId' is missing!"
    }

    @Test fun `getHeatingSchedule() returns 500 when persistence fails`() {
        val areaId = UUID.randomUUID()
        every { getHeatingScheduleUseCase.execute(areaId) } returns
            PersistenceFailure(Exception("db error")).left()

        val responseBody: String = mockMvc.perform(
            get("/areas/$areaId/heating-schedule").authenticated())
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Internal server error while retrieving heating schedule."
    }

    ///// replaceHeatingSchedule ///////////////////////////////////////////////////////////////////////////////////////

    @TestFactory fun `replaceHeatingSchedule() auth tests`() = authTestSupport.dynamicTestsBy {
        val temp = aRandomTemperature().value
        put("/areas/${UUID.randomUUID()}/heating-schedule")
            .contentType("application/json")
            .content("""{"defaultTemperature": $temp, "intervals": []}""")
    }

    @Test fun `replaceHeatingSchedule() returns 422 with problem detail when intervals overlap`() {
        val areaId = UUID.randomUUID()
        val temp = aRandomTemperature().value
        every { replaceHeatingScheduleUseCase.execute(any(), any(), any()) } returns OverlappingIntervals.left()

        val responseBody: String = mockMvc.perform(
            put("/areas/$areaId/heating-schedule")
                .contentType("application/json")
                .authenticated()
                .content("""{"defaultTemperature": $temp, "intervals": []}"""))
            .andExpect(status().isUnprocessableEntity)
            .andReturn().response.contentAsString

        val problem: ProblemDetail = objectMapper.readValue(responseBody, ProblemDetail::class.java)
        problem.status shouldBe 422
        problem.title shouldBe "Overlapping Intervals"
    }

    @Test fun `replaceHeatingSchedule() returns 404 when area is not found`() {
        val areaId = UUID.randomUUID()
        val temp = aRandomTemperature().value
        every { replaceHeatingScheduleUseCase.execute(any(), any(), any()) } returns AreaNotFound(areaId).left()

        val responseBody: String = mockMvc.perform(
            put("/areas/$areaId/heating-schedule")
                .contentType("application/json")
                .authenticated()
                .content("""{"defaultTemperature": $temp, "intervals": []}"""))
            .andExpect(status().isNotFound)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Area with id '$areaId' is missing!"
    }

    @Test fun `replaceHeatingSchedule() returns 500 when persistence fails`() {
        val areaId = UUID.randomUUID()
        val temp = aRandomTemperature().value
        every { replaceHeatingScheduleUseCase.execute(any(), any(), any()) } returns
            PersistenceFailure(Exception("db error")).left()

        val responseBody: String = mockMvc.perform(
            put("/areas/$areaId/heating-schedule")
                .contentType("application/json")
                .authenticated()
                .content("""{"defaultTemperature": $temp, "intervals": []}"""))
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Internal server error while saving heating schedule."
    }

    @Test fun `replaceHeatingSchedule() returns 200 with updated schedule on success`() {
        val areaId = UUID.randomUUID()
        val temp = aRandomTemperature().value
        val returnedInterval = IntervalDto(
            temperature = aRandomTemperature(),
            startTime = aDailyTime(hour = 8),
            endTime = aDailyTime(hour = 22)
        )
        val returnedSchedule = HeatingScheduleDto(
            defaultTemperature = aRandomTemperature(),
            intervals = listOf(returnedInterval)
        )
        every { replaceHeatingScheduleUseCase.execute(any(), any(), any()) } returns returnedSchedule.right()

        val responseBody: String = mockMvc.perform(
            put("/areas/$areaId/heating-schedule")
                .contentType("application/json")
                .authenticated()
                .content("""
                    {
                        "defaultTemperature": $temp,
                        "intervals": [
                            {"temperature": $temp, "startTime": "08:00", "endTime": "22:00"}
                            ]}""".trimIndent()))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        val response: HeatingScheduleResponse = objectMapper
            .readValue(responseBody, HeatingScheduleResponse::class.java)
        response.defaultTemperature shouldBe returnedSchedule.defaultTemperature
        response.intervals shouldHaveSize 1
        response.intervals[0].temperature shouldBe returnedInterval.temperature
        response.intervals[0].startTime shouldBe returnedInterval.startTime
        response.intervals[0].endTime shouldBe returnedInterval.endTime
    }

    ///// deleteHeatingSchedule ///////////////////////////////////////////////////////////////////////////////////////

    @TestFactory fun `deleteHeatingSchedule() auth tests`() = authTestSupport.dynamicTestsBy {
        delete("/areas/${UUID.randomUUID()}/heating-schedule")
    }

    @Test fun `deleteHeatingSchedule() returns 204 on success`() {
        val areaId = UUID.randomUUID()
        every { deleteHeatingScheduleUseCase.execute(areaId) } returns Unit.right()

        mockMvc.perform(
            delete("/areas/$areaId/heating-schedule").authenticated())
            .andExpect(status().isNoContent)
    }

    @Test fun `deleteHeatingSchedule() returns 404 when area is not found`() {
        val areaId = UUID.randomUUID()
        every { deleteHeatingScheduleUseCase.execute(areaId) } returns AreaNotFound(areaId).left()

        val responseBody: String = mockMvc.perform(
            delete("/areas/$areaId/heating-schedule").authenticated())
            .andExpect(status().isNotFound)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Area with id '$areaId' is missing!"
    }

    @Test fun `deleteHeatingSchedule() returns 500 when persistence fails`() {
        val areaId = UUID.randomUUID()
        every { deleteHeatingScheduleUseCase.execute(areaId) } returns
            PersistenceFailure(Exception("db error")).left()

        val responseBody: String = mockMvc.perform(
            delete("/areas/$areaId/heating-schedule").authenticated())
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Internal server error while deleting heating schedule."
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
}
