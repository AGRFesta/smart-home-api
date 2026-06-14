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
import org.agrfesta.sh.api.core.domain.devices.AssignmentRole
import org.agrfesta.sh.api.core.domain.devices.DeviceAreaAssignment
import org.agrfesta.sh.api.core.domain.devices.DeviceFeature.SENSOR
import org.agrfesta.sh.api.core.domain.failures.DeviceNotFound
import org.agrfesta.sh.api.core.domain.failures.DeviceRepositoryError
import org.agrfesta.sh.api.domain.aDeviceAggregate
import org.agrfesta.sh.api.security.SecurityConfig
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestConstructor
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@WebMvcTest(DevicesController::class)
@Import(SecurityConfig::class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@ActiveProfiles("test")
class DevicesGetByIdControllerMvcSliceTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    @MockkBean private val getDeviceUseCase: GetDeviceUseCase,
    // Required by the @WebMvcTest(DevicesController) context but not exercised by these tests
    @Suppress("UnusedPrivateProperty") @MockkBean private val getDevicesUseCase: GetDevicesUseCase,
    @Suppress("UnusedPrivateProperty") @MockkBean private val refreshDevicesUseCase: RefreshDevicesUseCase,
    @Suppress("UnusedPrivateProperty") @MockkBean private val inspectDeviceUseCase: InspectDeviceUseCase
) {
    private val authTestSupport = AuthTestSupport(mockMvc, objectMapper)

    @TestFactory fun `getById() auth tests`() = authTestSupport.dynamicTestsBy {
        get("/devices/{uuid}", UUID.randomUUID())
    }

    @Test fun `getById() returns 200 with the device aggregate in DeviceAggregateResponse shape`() {
        // Given
        val deviceId = UUID.randomUUID()
        val aggregate = aDeviceAggregate(
            uuid = deviceId,
            features = setOf(SENSOR),
            createdOn = Instant.now().truncatedTo(ChronoUnit.SECONDS),
            assignments = listOf(
                DeviceAreaAssignment(UUID.randomUUID(), "Living Room", AssignmentRole.SENSOR)
            )
        )
        every { getDeviceUseCase.execute(deviceId) } returns aggregate.right()

        // When
        val responseBody = mockMvc.perform(get("/devices/{uuid}", deviceId).authenticated())
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        // Then
        val response = objectMapper.readValue(responseBody, DeviceAggregateResponse::class.java)
        response shouldBe aggregate.toResponse()
    }

    @Test fun `getById() returns 404 when the device does not exist`() {
        // Given
        val deviceId = UUID.randomUUID()
        every { getDeviceUseCase.execute(deviceId) } returns DeviceNotFound(deviceId).left()

        // When / Then
        mockMvc.perform(get("/devices/{uuid}", deviceId).authenticated())
            .andExpect(status().isNotFound)
    }

    @Test fun `getById() returns 500 with error message when the repository fails`() {
        // Given
        val deviceId = UUID.randomUUID()
        every { getDeviceUseCase.execute(deviceId) } returns DeviceRepositoryError.left()

        // When
        val responseBody = mockMvc.perform(get("/devices/{uuid}", deviceId).authenticated())
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        // Then
        val response = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Unable to retrieve device '$deviceId'!"
    }
}
