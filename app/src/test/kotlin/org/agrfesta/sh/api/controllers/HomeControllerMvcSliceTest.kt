package org.agrfesta.sh.api.controllers

import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.agrfesta.sh.api.core.application.ports.inbounds.GetHomeDashboardUseCase
import org.agrfesta.sh.api.core.domain.commons.FieldFailure
import org.agrfesta.sh.api.core.domain.commons.FieldSuccess
import org.agrfesta.sh.api.core.domain.failures.GetHomeDashboardFailure
import org.agrfesta.sh.api.core.domain.heating.SharedHeatingStrategy
import org.agrfesta.sh.api.core.domain.home.AreaDashboardDto
import org.agrfesta.sh.api.core.domain.home.GlobalStateDto
import org.agrfesta.sh.api.core.domain.home.HeatingDto
import org.agrfesta.sh.api.core.domain.home.HomeDashboardDto
import org.agrfesta.sh.api.core.domain.home.HumidityDto
import org.agrfesta.sh.api.core.domain.home.MeasurementsDto
import org.agrfesta.sh.api.security.SecurityConfig
import org.agrfesta.test.mothers.aRandomTemperature
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestConstructor
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.util.*

@WebMvcTest(HomeController::class)
@Import(SecurityConfig::class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@ActiveProfiles("test")
class HomeControllerMvcSliceTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    @MockkBean private val getHomeDashboardUseCase: GetHomeDashboardUseCase
) {
    private val authTestSupport = AuthTestSupport(mockMvc, objectMapper)

    // /// getHome //////////////////////////////////////////////////////////////////////////////////////////////////////

    @TestFactory fun `getHome() auth tests`() = authTestSupport.dynamicTestsBy {
        get("/home")
    }

    @Test fun `getHome() returns 200 with home dashboard on success`() {
        val areaId = UUID.randomUUID()
        val currentTemp = aRandomTemperature()
        val targetTemp = aRandomTemperature()
        val dashboard = HomeDashboardDto(
            globalState = GlobalStateDto(
                heatingActive = FieldSuccess(true),
                strategy = FieldSuccess(SharedHeatingStrategy.COMFORT)
            ),
            areas = listOf(
                AreaDashboardDto(
                    id = areaId,
                    name = "Living Room",
                    measurements = MeasurementsDto(
                        heating = HeatingDto(
                            currentTemperature = FieldSuccess(currentTemp),
                            targetTemperature = FieldSuccess(targetTemp)
                        ),
                        humidity = HumidityDto(relative = FieldSuccess(BigDecimal("45.5")))
                    )
                )
            )
        )
        every { getHomeDashboardUseCase.execute() } returns dashboard.right()

        val responseBody: String = mockMvc.perform(get("/home").authenticated())
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        val json = objectMapper.readTree(responseBody)

        withClue("globalState.heatingActive") {
            json["globalState"]["heatingActive"]["type"].asText() shouldBe "success"
            json["globalState"]["heatingActive"]["value"].asBoolean() shouldBe true
        }
        withClue("globalState.strategy") {
            json["globalState"]["strategy"]["type"].asText() shouldBe "success"
            json["globalState"]["strategy"]["value"].asText() shouldBe "COMFORT"
        }
        withClue("areas") {
            json["areas"].size() shouldBe 1
            val area = json["areas"][0]
            area["id"].asText() shouldBe areaId.toString()
            area["name"].asText() shouldBe "Living Room"
            area["measurements"]["heating"]["currentTemperature"]["type"].asText() shouldBe "success"
            area["measurements"]["heating"]["targetTemperature"]["type"].asText() shouldBe "success"
            area["measurements"]["humidity"]["relative"]["type"].asText() shouldBe "success"
            area["measurements"]["humidity"]["relative"]["value"].asText() shouldBe "45.5"
        }
    }

    @Test fun `getHome() serializes field failures in the response`() {
        val dashboard = HomeDashboardDto(
            globalState = GlobalStateDto(
                heatingActive = FieldSuccess(false),
                strategy = FieldFailure("Unable to retrieve heating strategy")
            ),
            areas = emptyList()
        )
        every { getHomeDashboardUseCase.execute() } returns dashboard.right()

        val responseBody: String = mockMvc.perform(get("/home").authenticated())
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        val json = objectMapper.readTree(responseBody)

        withClue("globalState.strategy is a field failure") {
            json["globalState"]["strategy"]["type"].asText() shouldBe "failure"
            json["globalState"]["strategy"]["error"].asText() shouldBe "Unable to retrieve heating strategy"
        }
    }

    @Test fun `getHome() returns 500 when use case returns failure`() {
        every { getHomeDashboardUseCase.execute() } returns mockk<GetHomeDashboardFailure>().left()

        val responseBody: String = mockMvc.perform(get("/home").authenticated())
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        val response = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Unable to fetch home dashboard!"
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
}
