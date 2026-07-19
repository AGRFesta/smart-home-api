package org.agrfesta.sh.api.controllers

import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.shouldBe
import io.mockk.every
import org.agrfesta.sh.api.core.application.ports.inbounds.devices.GetAcStateUseCase
import org.agrfesta.sh.api.core.application.ports.inbounds.devices.SetAcSettingsUseCase
import org.agrfesta.sh.api.core.domain.commons.Temperature
import org.agrfesta.sh.api.core.domain.devices.AcFanSpeed
import org.agrfesta.sh.api.core.domain.devices.AcMode
import org.agrfesta.sh.api.core.domain.devices.AcState
import org.agrfesta.sh.api.core.domain.devices.ActuatorStatus
import org.agrfesta.sh.api.core.domain.failures.AcProviderFailure
import org.agrfesta.sh.api.core.domain.failures.DeviceNotFound
import org.agrfesta.sh.api.core.domain.failures.DeviceRepositoryError
import org.agrfesta.sh.api.core.domain.failures.NotAnAirConditioner
import org.agrfesta.sh.api.security.SecurityConfig
import org.hamcrest.CoreMatchers.nullValue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestConstructor
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@WebMvcTest(AirConditionerController::class)
@Import(SecurityConfig::class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@ActiveProfiles("test")
class AirConditionerGetControllerMvcSliceTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    @MockkBean private val getAcStateUseCase: GetAcStateUseCase,
    // Required by the @WebMvcTest(AirConditionerController) context but not exercised by these tests
    @Suppress("UnusedPrivateProperty") @MockkBean private val setAcSettingsUseCase: SetAcSettingsUseCase
) {
    private val authTestSupport = AuthTestSupport(mockMvc, objectMapper)

    @TestFactory fun `getAcState() auth tests`() = authTestSupport.dynamicTestsBy {
        get("/devices/{uuid}/air-conditioner", UUID.randomUUID())
    }

    @Test fun `getAcState() returns 200 with the driver control state`() {
        // Given
        val deviceId = UUID.randomUUID()
        val state = AcState(
            power = ActuatorStatus.OFF,
            mode = AcMode.COOL,
            targetTemperature = Temperature.of("26"),
            fanSpeed = AcFanSpeed.LOW,
        )
        every { getAcStateUseCase.execute(deviceId) } returns state.right()

        // When / Then
        mockMvc.perform(get("/devices/{uuid}/air-conditioner", deviceId).authenticated())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.power").value("OFF"))
            .andExpect(jsonPath("$.mode").value("COOL"))
            .andExpect(jsonPath("$.targetTemperature").value(26))
            .andExpect(jsonPath("$.fanSpeed").value("LOW"))
    }

    @Test fun `getAcState() serializes an unknown state as UNDEFINED power and null fields`() {
        // Given: the driver could not read what the device reports
        val deviceId = UUID.randomUUID()
        val state = AcState(
            power = ActuatorStatus.UNDEFINED,
            mode = null,
            targetTemperature = null,
            fanSpeed = null,
        )
        every { getAcStateUseCase.execute(deviceId) } returns state.right()

        // When / Then: "unknown" travels as-is — never masked as a default
        mockMvc.perform(get("/devices/{uuid}/air-conditioner", deviceId).authenticated())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.power").value("UNDEFINED"))
            .andExpect(jsonPath("$.mode").value(nullValue()))
            .andExpect(jsonPath("$.targetTemperature").value(nullValue()))
            .andExpect(jsonPath("$.fanSpeed").value(nullValue()))
    }

    @Test fun `getAcState() returns 404 when the device does not exist`() {
        // Given
        val deviceId = UUID.randomUUID()
        every { getAcStateUseCase.execute(deviceId) } returns DeviceNotFound(deviceId).left()

        // When / Then
        mockMvc.perform(get("/devices/{uuid}/air-conditioner", deviceId).authenticated())
            .andExpect(status().isNotFound)
    }

    @Test fun `getAcState() returns 409 when the device is not an air conditioner`() {
        // Given
        val deviceId = UUID.randomUUID()
        every { getAcStateUseCase.execute(deviceId) } returns NotAnAirConditioner.left()

        // When
        val responseBody = mockMvc.perform(get("/devices/{uuid}/air-conditioner", deviceId).authenticated())
            .andExpect(status().isConflict)
            .andReturn().response.contentAsString

        // Then
        val response = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Device '$deviceId' is not an air conditioner!"
    }

    @Test fun `getAcState() returns 502 surfacing the provider message when the provider fails`() {
        // Given
        val deviceId = UUID.randomUUID()
        every { getAcStateUseCase.execute(deviceId) } returns AcProviderFailure("provider exploded").left()

        // When
        val responseBody = mockMvc.perform(get("/devices/{uuid}/air-conditioner", deviceId).authenticated())
            .andExpect(status().isBadGateway)
            .andReturn().response.contentAsString

        // Then
        val response = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "provider exploded"
    }

    @Test fun `getAcState() returns 500 when the repository fails`() {
        // Given
        val deviceId = UUID.randomUUID()
        every { getAcStateUseCase.execute(deviceId) } returns DeviceRepositoryError.left()

        // When
        val responseBody = mockMvc.perform(get("/devices/{uuid}/air-conditioner", deviceId).authenticated())
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        // Then
        val response = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Unable to retrieve device '$deviceId'!"
    }
}
