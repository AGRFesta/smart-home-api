package org.agrfesta.sh.api.controllers

import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.mockk.every
import org.agrfesta.sh.api.core.application.ports.inbounds.diagnostics.InspectProviderUseCase
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.core.domain.failures.MissingProbeParams
import org.agrfesta.sh.api.core.domain.failures.ProviderDiagnosticsNotSupported
import org.agrfesta.sh.api.core.domain.failures.ProviderInspectionFailure
import org.agrfesta.sh.api.core.domain.failures.UnknownProbe
import org.agrfesta.sh.api.security.SecurityConfig
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestConstructor
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(ProvidersController::class)
@Import(SecurityConfig::class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@ActiveProfiles("test")
class ProvidersDiagnosticsControllerMvcSliceTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    @MockkBean private val inspectProviderUseCase: InspectProviderUseCase
) {
    private val authTestSupport = AuthTestSupport(mockMvc, objectMapper)

    @TestFactory fun `diagnostics() auth tests`() = authTestSupport.dynamicTestsBy {
        get("/providers/{provider}/diagnostics", "hon").param("probe", "appliance-list")
    }

    @Test fun `diagnostics() returns 200 with the raw body as application json, forwarding the probe params`() {
        // Given
        val rawBody = """{"payload": {"whatever": 1},  "extra": true}"""
        every {
            inspectProviderUseCase.execute(
                Provider.HON,
                "context",
                mapOf("macAddress" to "AA-BB-01", "applianceType" to "WM")
            )
        } returns rawBody.right()

        // When
        val response = mockMvc.perform(
            get("/providers/{provider}/diagnostics", "hon")
                .param("probe", "context")
                .param("macAddress", "AA-BB-01")
                .param("applianceType", "WM")
                .authenticated()
        )
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andReturn().response

        // Then
        response.contentAsString shouldBe rawBody
    }

    @Test fun `diagnostics() returns 404 when the provider is unknown`() {
        // When / Then
        mockMvc.perform(
            get("/providers/{provider}/diagnostics", "tuya")
                .param("probe", "devices")
                .authenticated()
        )
            .andExpect(status().isNotFound)
    }

    /** Guard (green on arrival): `probe` is a required request param, Spring answers 400 itself. */
    @Test fun `diagnostics() returns 400 when the probe param is missing`() {
        // When / Then
        mockMvc.perform(
            get("/providers/{provider}/diagnostics", "hon")
                .authenticated()
        )
            .andExpect(status().isBadRequest)
    }

    @Test fun `diagnostics() returns 400 with the available probes when the probe is unknown`() {
        // Given
        val failure = UnknownProbe("send-command", availableProbes = setOf("appliance-list", "context"))
        every { inspectProviderUseCase.execute(Provider.HON, "send-command", emptyMap()) } returns failure.left()

        // When
        val responseBody = mockMvc.perform(
            get("/providers/{provider}/diagnostics", "hon")
                .param("probe", "send-command")
                .authenticated()
        )
            .andExpect(status().isBadRequest)
            .andReturn().response.contentAsString

        // Then
        val response = objectMapper.readTree(responseBody)
        withClue("the 400 body should carry the available probes hint") {
            response.at("/message").asText() shouldBe "Unknown probe 'send-command' for provider 'HON'!"
            response.at("/availableProbes").map { it.asText() } shouldBe listOf("appliance-list", "context")
        }
    }

    @Test fun `diagnostics() returns 400 with the expected params when a required probe param is missing`() {
        // Given
        val failure = MissingProbeParams("context", expectedParams = setOf("macAddress", "applianceType"))
        every { inspectProviderUseCase.execute(Provider.HON, "context", emptyMap()) } returns failure.left()

        // When
        val responseBody = mockMvc.perform(
            get("/providers/{provider}/diagnostics", "hon")
                .param("probe", "context")
                .authenticated()
        )
            .andExpect(status().isBadRequest)
            .andReturn().response.contentAsString

        // Then
        val response = objectMapper.readTree(responseBody)
        withClue("the 400 body should carry the expected params hint") {
            response.at("/message").asText() shouldBe "Probe 'context' is missing required params!"
            response.at("/expectedParams").map { it.asText() } shouldBe listOf("macAddress", "applianceType")
        }
    }

    @Test fun `diagnostics() returns 501 when diagnostics is not supported for the provider`() {
        // Given
        every {
            inspectProviderUseCase.execute(Provider.NETATMO, "home-status", emptyMap())
        } returns ProviderDiagnosticsNotSupported.left()

        // When
        val responseBody = mockMvc.perform(
            get("/providers/{provider}/diagnostics", "netatmo")
                .param("probe", "home-status")
                .authenticated()
        )
            .andExpect(status().isNotImplemented)
            .andReturn().response.contentAsString

        // Then
        val response = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Diagnostics is not available for provider 'NETATMO'!"
    }

    @Test fun `diagnostics() returns 502 surfacing the cause when the provider inspection fails`() {
        // Given
        every {
            inspectProviderUseCase.execute(Provider.HON, "appliance-list", emptyMap())
        } returns ProviderInspectionFailure("HonServerError(statusCode=500)").left()

        // When
        val responseBody = mockMvc.perform(
            get("/providers/{provider}/diagnostics", "hon")
                .param("probe", "appliance-list")
                .authenticated()
        )
            .andExpect(status().isBadGateway)
            .andReturn().response.contentAsString

        // Then
        val response = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "HonServerError(statusCode=500)"
    }
}
