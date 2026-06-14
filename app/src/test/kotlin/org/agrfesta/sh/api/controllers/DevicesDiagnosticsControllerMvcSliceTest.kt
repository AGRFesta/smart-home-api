package org.agrfesta.sh.api.controllers

import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.shouldBe
import io.mockk.every
import org.agrfesta.sh.api.core.application.ports.inbounds.GetDeviceUseCase
import org.agrfesta.sh.api.core.application.ports.inbounds.GetDevicesUseCase
import org.agrfesta.sh.api.core.application.ports.inbounds.InspectDeviceUseCase
import org.agrfesta.sh.api.core.application.ports.inbounds.RefreshDevicesUseCase
import org.agrfesta.sh.api.core.domain.failures.DeviceNotFound
import org.agrfesta.sh.api.core.domain.failures.DeviceRepositoryError
import org.agrfesta.sh.api.core.domain.failures.DiagnosticsNotSupported
import org.agrfesta.sh.api.core.domain.failures.DiagnosticsProviderFailure
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
import java.util.UUID

@WebMvcTest(DevicesController::class)
@Import(SecurityConfig::class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@ActiveProfiles("test")
class DevicesDiagnosticsControllerMvcSliceTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    @MockkBean private val inspectDeviceUseCase: InspectDeviceUseCase,
    // Required by the @WebMvcTest(DevicesController) context but not exercised by these tests
    @Suppress("UnusedPrivateProperty") @MockkBean private val getDeviceUseCase: GetDeviceUseCase,
    @Suppress("UnusedPrivateProperty") @MockkBean private val getDevicesUseCase: GetDevicesUseCase,
    @Suppress("UnusedPrivateProperty") @MockkBean private val refreshDevicesUseCase: RefreshDevicesUseCase
) {
    private val authTestSupport = AuthTestSupport(mockMvc, objectMapper)

    @TestFactory fun `diagnostics() auth tests`() = authTestSupport.dynamicTestsBy {
        get("/devices/{uuid}/diagnostics", UUID.randomUUID())
    }

    @Test fun `diagnostics() returns 200 with the raw provider body as application json`() {
        // Given
        val deviceId = UUID.randomUUID()
        val rawBody = """{"deviceId":"abc-123","battery":88,"hubDeviceId":"hub-1"}"""
        every { inspectDeviceUseCase.execute(deviceId) } returns rawBody.right()

        // When
        val response = mockMvc.perform(get("/devices/{uuid}/diagnostics", deviceId).authenticated())
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andReturn().response

        // Then
        response.contentAsString shouldBe rawBody
    }

    @Test fun `diagnostics() returns 404 when the device does not exist`() {
        // Given
        val deviceId = UUID.randomUUID()
        every { inspectDeviceUseCase.execute(deviceId) } returns DeviceNotFound(deviceId).left()

        // When / Then
        mockMvc.perform(get("/devices/{uuid}/diagnostics", deviceId).authenticated())
            .andExpect(status().isNotFound)
    }

    @Test fun `diagnostics() returns 501 when diagnostics is not supported for the device`() {
        // Given
        val deviceId = UUID.randomUUID()
        every { inspectDeviceUseCase.execute(deviceId) } returns DiagnosticsNotSupported.left()

        // When / Then
        mockMvc.perform(get("/devices/{uuid}/diagnostics", deviceId).authenticated())
            .andExpect(status().isNotImplemented)
    }

    @Test fun `diagnostics() returns 502 surfacing the provider message when the provider fails`() {
        // Given
        val deviceId = UUID.randomUUID()
        every { inspectDeviceUseCase.execute(deviceId) } returns
            DiagnosticsProviderFailure("provider exploded").left()

        // When
        val responseBody = mockMvc.perform(get("/devices/{uuid}/diagnostics", deviceId).authenticated())
            .andExpect(status().isBadGateway)
            .andReturn().response.contentAsString

        // Then
        val response = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "provider exploded"
    }

    @Test fun `diagnostics() returns 500 when the repository fails`() {
        // Given
        val deviceId = UUID.randomUUID()
        every { inspectDeviceUseCase.execute(deviceId) } returns DeviceRepositoryError.left()

        // When
        val responseBody = mockMvc.perform(get("/devices/{uuid}/diagnostics", deviceId).authenticated())
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        // Then
        val response = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Unable to retrieve device '$deviceId'!"
    }
}
