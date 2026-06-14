package org.agrfesta.sh.api.controllers

import arrow.core.right
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.mockk.every
import io.mockk.verify
import org.agrfesta.sh.api.core.application.ports.inbounds.GetDeviceUseCase
import org.agrfesta.sh.api.core.application.ports.inbounds.GetDevicesUseCase
import org.agrfesta.sh.api.core.application.ports.inbounds.InspectDeviceUseCase
import org.agrfesta.sh.api.core.application.ports.inbounds.RefreshDevicesUseCase
import org.agrfesta.sh.api.core.domain.devices.Device
import org.agrfesta.sh.api.core.domain.devices.DeviceFeature.SENSOR
import org.agrfesta.sh.api.core.domain.devices.DeviceStatus.PAIRED
import org.agrfesta.sh.api.core.domain.devices.Provider.SWITCHBOT
import org.agrfesta.sh.api.domain.aDevice
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

@WebMvcTest(DevicesController::class)
@Import(SecurityConfig::class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@ActiveProfiles("test")
class DevicesGetControllerMvcSliceTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    @MockkBean private val getDevicesUseCase: GetDevicesUseCase,
    // Required by the @WebMvcTest(DevicesController) context but not exercised by these tests
    @Suppress("UnusedPrivateProperty") @MockkBean private val refreshDevicesUseCase: RefreshDevicesUseCase,
    @Suppress("UnusedPrivateProperty") @MockkBean private val getDeviceUseCase: GetDeviceUseCase,
    @Suppress("UnusedPrivateProperty") @MockkBean private val inspectDeviceUseCase: InspectDeviceUseCase
) {
    private val authTestSupport = AuthTestSupport(mockMvc, objectMapper)

    @TestFactory fun `getDevices() auth tests`() = authTestSupport.dynamicTestsBy {
        get("/devices")
    }

    @Test fun `getDevices() returns 200 with an empty array when the use case returns no devices`() {
        // Given
        every { getDevicesUseCase.execute(any(), any(), any()) } returns emptyList<Device>().right()

        // When
        val responseBody = mockMvc.perform(get("/devices").authenticated())
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        // Then
        val response: List<DeviceResponse> =
            objectMapper.readValue(responseBody, object : TypeReference<List<DeviceResponse>>() {})
        response.shouldBeEmpty()
    }

    @Test fun `getDevices() returns 200 with the devices in DeviceResponse shape`() {
        // Given
        val device = aDevice(features = setOf(SENSOR))
        every { getDevicesUseCase.execute(any(), any(), any()) } returns listOf(device).right()

        // When
        val responseBody = mockMvc.perform(get("/devices").authenticated())
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        // Then
        val response: List<DeviceResponse> =
            objectMapper.readValue(responseBody, object : TypeReference<List<DeviceResponse>>() {})
        response.shouldContainExactly(device.toResponse())
    }

    @Test fun `getDevices() binds provider, status and feature query params and forwards them to the use case`() {
        // Given
        every { getDevicesUseCase.execute(SWITCHBOT, PAIRED, SENSOR) } returns emptyList<Device>().right()

        // When
        mockMvc.perform(get("/devices?provider=SWITCHBOT&status=PAIRED&feature=SENSOR").authenticated())
            .andExpect(status().isOk)

        // Then
        verify { getDevicesUseCase.execute(SWITCHBOT, PAIRED, SENSOR) }
    }
}
