package org.agrfesta.sh.api.controllers

import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import org.agrfesta.sh.api.core.application.ports.inbounds.RefreshDevicesUseCase
import org.agrfesta.sh.api.core.domain.devices.RefreshDevicesResult
import org.agrfesta.sh.api.core.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.domain.aDevice
import org.agrfesta.sh.api.security.SecurityConfig
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestConstructor
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(DevicesController::class)
@Import(SecurityConfig::class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@ActiveProfiles("test")
class DevicesSynchronizeControllerMvcSliceTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    @MockkBean private val refreshDevicesUseCase: RefreshDevicesUseCase
) {
    private val authTestSupport = AuthTestSupport(mockMvc, objectMapper)

    @TestFactory fun `synchronize() auth tests`() = authTestSupport.dynamicTestsBy {
        post("/devices/synchronizations")
    }

    @Test fun `synchronize() returns 200 with empty body when use case returns empty result`() {
        // Given
        every { refreshDevicesUseCase.execute() } returns RefreshDevicesResult().right()

        // When
        val responseBody = mockMvc.perform(
            post("/devices/synchronizations").authenticated())
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        // Then
        val response = objectMapper.readValue(responseBody, DevicesRefreshResponse::class.java)
        response.newDevices.shouldBeEmpty()
        response.updatedDevices.shouldBeEmpty()
        response.detachedDevices.shouldBeEmpty()
    }

    @Test fun `synchronize() returns 200 with new devices in response body`() {
        // Given
        val newDevice = aDevice()
        every { refreshDevicesUseCase.execute() } returns RefreshDevicesResult(newDevices = listOf(newDevice)).right()

        // When
        val responseBody = mockMvc.perform(
            post("/devices/synchronizations").authenticated())
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        // Then
        val response = objectMapper.readValue(responseBody, DevicesRefreshResponse::class.java)
        response.newDevices shouldHaveSize 1
        response.updatedDevices.shouldBeEmpty()
        response.detachedDevices.shouldBeEmpty()
    }

    @Test fun `synchronize() returns 200 with updated devices in response body`() {
        // Given
        val updatedDevice = aDevice()
        every { refreshDevicesUseCase.execute() } returns RefreshDevicesResult(updatedDevices = listOf(updatedDevice)).right()

        // When
        val responseBody = mockMvc.perform(
            post("/devices/synchronizations").authenticated())
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        // Then
        val response = objectMapper.readValue(responseBody, DevicesRefreshResponse::class.java)
        response.newDevices.shouldBeEmpty()
        response.updatedDevices shouldHaveSize 1
        response.detachedDevices.shouldBeEmpty()
    }

    @Test fun `synchronize() returns 200 with detached devices in response body`() {
        // Given
        val detachedDevice = aDevice()
        every { refreshDevicesUseCase.execute() } returns RefreshDevicesResult(detachedDevices = listOf(detachedDevice)).right()

        // When
        val responseBody = mockMvc.perform(
            post("/devices/synchronizations").authenticated())
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        // Then
        val response = objectMapper.readValue(responseBody, DevicesRefreshResponse::class.java)
        response.newDevices.shouldBeEmpty()
        response.updatedDevices.shouldBeEmpty()
        response.detachedDevices shouldHaveSize 1
    }

    @Test fun `synchronize() returns 500 with error message when use case returns Left`() {
        // Given
        every { refreshDevicesUseCase.execute() } returns PersistenceFailure(Exception("db failure")).left()

        // When
        val responseBody = mockMvc.perform(
            post("/devices/synchronizations").authenticated())
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        // Then
        val response = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Device synchronization failed!"
    }

}
