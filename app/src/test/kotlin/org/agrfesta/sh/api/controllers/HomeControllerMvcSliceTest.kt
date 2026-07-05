package org.agrfesta.sh.api.controllers

import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import org.agrfesta.sh.api.core.application.ports.inbounds.GetHomeDashboardUseCase
import org.agrfesta.sh.api.core.application.readmodels.commons.FieldFailure
import org.agrfesta.sh.api.core.application.readmodels.commons.FieldSuccess
import org.agrfesta.sh.api.core.application.readmodels.home.AreaDashboardView
import org.agrfesta.sh.api.core.application.readmodels.home.GlobalStateView
import org.agrfesta.sh.api.core.application.readmodels.home.HeatingView
import org.agrfesta.sh.api.core.application.readmodels.home.HomeDashboardView
import org.agrfesta.sh.api.core.application.readmodels.home.HumidityView
import org.agrfesta.sh.api.core.application.readmodels.home.MeasurementsView
import org.agrfesta.sh.api.core.domain.alerts.AlertType
import org.agrfesta.sh.api.core.domain.failures.GetHomeDashboardFailure
import org.agrfesta.sh.api.core.domain.heating.SharedHeatingStrategy
import org.agrfesta.sh.api.home.DefaultSseEmitterFactory
import org.agrfesta.sh.api.home.HomeStreamBroadcaster
import org.agrfesta.sh.api.security.SecurityConfig
import org.agrfesta.test.mothers.aRandomTemperature
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.util.*

@WebMvcTest(HomeController::class)
@Import(SecurityConfig::class, HomeStreamBroadcaster::class, DefaultSseEmitterFactory::class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@ActiveProfiles("test")
@TestPropertySource(properties = ["home.stream.emitter-timeout-ms=500"])
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

    @TestFactory fun `getHomeStream() auth tests`() = authTestSupport.dynamicTestsBy {
        get("/home/stream")
    }

    @Test fun `getHome() returns 200 with home dashboard on success`() {
        val areaId = UUID.randomUUID()
        val currentTemp = aRandomTemperature()
        val targetTemp = aRandomTemperature()
        val dashboard = HomeDashboardView(
            globalState = GlobalStateView(
                heatingActive = FieldSuccess(true),
                strategy = FieldSuccess(SharedHeatingStrategy.COMFORT)
            ),
            areas = listOf(
                AreaDashboardView(
                    id = areaId,
                    name = "Living Room",
                    measurements = MeasurementsView(
                        heating = HeatingView(
                            currentTemperature = FieldSuccess(currentTemp),
                            targetTemperature = FieldSuccess(targetTemp)
                        ),
                        humidity = HumidityView(relative = FieldSuccess(BigDecimal("45.5")))
                    ),
                    activeAlerts = FieldSuccess(emptySet())
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
        val dashboard = HomeDashboardView(
            globalState = GlobalStateView(
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

    @Test fun `getHome() serializes area activeAlerts in the response`() {
        val dashboard = HomeDashboardView(
            globalState = GlobalStateView(
                heatingActive = FieldSuccess(false),
                strategy = FieldSuccess(null)
            ),
            areas = listOf(
                AreaDashboardView(
                    id = UUID.randomUUID(),
                    name = "Living Room",
                    measurements = MeasurementsView(heating = null, humidity = null),
                    activeAlerts = FieldSuccess(setOf(AlertType.BATTERY_LOW))
                )
            )
        )
        every { getHomeDashboardUseCase.execute() } returns dashboard.right()

        val responseBody: String = mockMvc.perform(get("/home").authenticated())
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        val json = objectMapper.readTree(responseBody)

        val area = json["areas"][0]
        withClue("the area should expose an activeAlerts field") {
            area.has("activeAlerts") shouldBe true
        }
        withClue("activeAlerts should be a success FieldResultResponse") {
            area["activeAlerts"]["type"].asText() shouldBe "success"
        }
        withClue("activeAlerts value should carry the open alert types of the area") {
            area["activeAlerts"]["value"].map { it.asText() } shouldBe listOf("BATTERY_LOW")
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

    // /// getHomeStream ////////////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `getHomeStream() returns 200 text event-stream with the initial dashboard event`() {
        val dashboard = HomeDashboardView(
            globalState = GlobalStateView(
                heatingActive = FieldSuccess(true),
                strategy = FieldSuccess(SharedHeatingStrategy.COMFORT)
            ),
            areas = emptyList()
        )
        every { getHomeDashboardUseCase.execute() } returns dashboard.right()

        // The initial event is written synchronously while the emitter is wired, so the buffered response can be read
        // directly without an async dispatch (which would block waiting for the long-lived emitter to complete).
        val response = mockMvc.perform(get("/home/stream").authenticated())
            .andExpect(request().asyncStarted())
            .andReturn().response

        withClue("status") { response.status shouldBe 200 }
        withClue("content type") { response.contentType shouldContain TEXT_EVENT_STREAM_VALUE }
        withClue("initial event carries the current dashboard") {
            response.contentAsString shouldContain "\"heatingActive\""
            response.contentAsString shouldContain "COMFORT"
        }
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
}
