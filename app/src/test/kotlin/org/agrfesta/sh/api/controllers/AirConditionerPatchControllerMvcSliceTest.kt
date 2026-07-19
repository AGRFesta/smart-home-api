package org.agrfesta.sh.api.controllers

import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.verify
import org.agrfesta.sh.api.core.application.ports.inbounds.devices.GetAcStateUseCase
import org.agrfesta.sh.api.core.application.ports.inbounds.devices.SetAcSettingsUseCase
import org.agrfesta.sh.api.core.domain.commons.Temperature
import org.agrfesta.sh.api.core.domain.devices.AcFanSpeed
import org.agrfesta.sh.api.core.domain.devices.AcMode
import org.agrfesta.sh.api.core.domain.devices.AcPowerCommand
import org.agrfesta.sh.api.core.domain.devices.AcSettingsUpdate
import org.agrfesta.sh.api.core.domain.failures.AcProviderFailure
import org.agrfesta.sh.api.core.domain.failures.DeviceNotFound
import org.agrfesta.sh.api.core.domain.failures.DeviceRepositoryError
import org.agrfesta.sh.api.core.domain.failures.InvalidAcSetting
import org.agrfesta.sh.api.core.domain.failures.NotAnAirConditioner
import org.agrfesta.sh.api.security.SecurityConfig
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestConstructor
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@WebMvcTest(AirConditionerController::class)
@Import(SecurityConfig::class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@ActiveProfiles("test")
class AirConditionerPatchControllerMvcSliceTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    @MockkBean private val setAcSettingsUseCase: SetAcSettingsUseCase,
    // Required by the @WebMvcTest(AirConditionerController) context but not exercised by these tests
    @Suppress("UnusedPrivateProperty") @MockkBean private val getAcStateUseCase: GetAcStateUseCase
) {
    private val authTestSupport = AuthTestSupport(mockMvc, objectMapper)

    @TestFactory fun `setAcSettings() auth tests`() = authTestSupport.dynamicTestsBy {
        patch("/devices/{uuid}/air-conditioner", UUID.randomUUID())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"power":"ON"}""")
    }

    @Test fun `setAcSettings() returns 204 forwarding every provided field to the use case`() {
        // Given
        val deviceId = UUID.randomUUID()
        val expectedUpdate = AcSettingsUpdate(
            power = AcPowerCommand.ON,
            mode = AcMode.COOL,
            targetTemperature = Temperature.of("22"),
            fanSpeed = AcFanSpeed.AUTO,
        )
        every { setAcSettingsUseCase.execute(deviceId, expectedUpdate) } returns Unit.right()

        // When
        mockMvc.perform(
            patch("/devices/{uuid}/air-conditioner", deviceId).authenticated()
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"power":"ON","mode":"COOL","targetTemperature":22,"fanSpeed":"AUTO"}"""),
        )
            .andExpect(status().isNoContent)

        // Then: delegating the mapped update to the use case IS the controller's contract
        verify(exactly = 1) { setAcSettingsUseCase.execute(deviceId, expectedUpdate) }
    }

    @Test fun `setAcSettings() forwards a partial body as an update carrying only that field`() {
        // Given
        val deviceId = UUID.randomUUID()
        val expectedUpdate = AcSettingsUpdate(targetTemperature = Temperature.of("21.5"))
        every { setAcSettingsUseCase.execute(deviceId, expectedUpdate) } returns Unit.right()

        // When
        mockMvc.perform(
            patch("/devices/{uuid}/air-conditioner", deviceId).authenticated()
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"targetTemperature":21.5}"""),
        )
            .andExpect(status().isNoContent)

        // Then
        verify(exactly = 1) { setAcSettingsUseCase.execute(deviceId, expectedUpdate) }
    }

    @Test fun `setAcSettings() returns 400 when no field is provided`() {
        // Given
        val deviceId = UUID.randomUUID()

        // When
        val responseBody = mockMvc.perform(
            patch("/devices/{uuid}/air-conditioner", deviceId).authenticated()
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"),
        )
            .andExpect(status().isBadRequest)
            .andReturn().response.contentAsString

        // Then
        val response = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "At least one field must be provided"
    }

    @Test fun `setAcSettings() returns 400 listing the admitted values on an unknown power`() {
        // Given
        val deviceId = UUID.randomUUID()

        // When
        val responseBody = mockMvc.perform(
            patch("/devices/{uuid}/air-conditioner", deviceId).authenticated()
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"power":"MAYBE"}"""),
        )
            .andExpect(status().isBadRequest)
            .andReturn().response.contentAsString

        // Then
        val response = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Invalid power 'MAYBE', allowed: ON, OFF"
    }

    @Test fun `setAcSettings() returns 400 listing the admitted values on an unknown mode`() {
        // Given
        val deviceId = UUID.randomUUID()

        // When
        val responseBody = mockMvc.perform(
            patch("/devices/{uuid}/air-conditioner", deviceId).authenticated()
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"mode":"FROSTY"}"""),
        )
            .andExpect(status().isBadRequest)
            .andReturn().response.contentAsString

        // Then
        val response = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Invalid mode 'FROSTY', allowed: AUTO, COOL, HEAT, DRY, FAN_ONLY"
    }

    @Test fun `setAcSettings() returns 400 listing the admitted values on an unknown fanSpeed`() {
        // Given
        val deviceId = UUID.randomUUID()

        // When
        val responseBody = mockMvc.perform(
            patch("/devices/{uuid}/air-conditioner", deviceId).authenticated()
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"fanSpeed":"TURBO"}"""),
        )
            .andExpect(status().isBadRequest)
            .andReturn().response.contentAsString

        // Then
        val response = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Invalid fanSpeed 'TURBO', allowed: AUTO, LOW, MEDIUM, HIGH"
    }

    @Test fun `setAcSettings() returns 400 on a malformed temperature`() {
        // Given: a non-numeric temperature never reaches the DTO — Jackson rejects the body
        val deviceId = UUID.randomUUID()

        // When / Then
        mockMvc.perform(
            patch("/devices/{uuid}/air-conditioner", deviceId).authenticated()
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"targetTemperature":"hot"}"""),
        )
            .andExpect(status().isBadRequest)
    }

    @Test fun `setAcSettings() returns 404 when the device does not exist`() {
        // Given
        val deviceId = UUID.randomUUID()
        every { setAcSettingsUseCase.execute(deviceId, any()) } returns DeviceNotFound(deviceId).left()

        // When / Then
        mockMvc.perform(
            patch("/devices/{uuid}/air-conditioner", deviceId).authenticated()
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"power":"ON"}"""),
        )
            .andExpect(status().isNotFound)
    }

    @Test fun `setAcSettings() returns 409 when the device is not an air conditioner`() {
        // Given
        val deviceId = UUID.randomUUID()
        every { setAcSettingsUseCase.execute(deviceId, any()) } returns NotAnAirConditioner.left()

        // When
        val responseBody = mockMvc.perform(
            patch("/devices/{uuid}/air-conditioner", deviceId).authenticated()
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"power":"ON"}"""),
        )
            .andExpect(status().isConflict)
            .andReturn().response.contentAsString

        // Then
        val response = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Device '$deviceId' is not an air conditioner!"
    }

    @Test fun `setAcSettings() returns 400 surfacing the reason when the device rejects the setting`() {
        // Given: the driver validated the request against the device's catalog and rejected it
        val deviceId = UUID.randomUUID()
        every { setAcSettingsUseCase.execute(deviceId, any()) } returns
            InvalidAcSetting("Value '35' for 'tempSel' is out of the admitted range").left()

        // When
        val responseBody = mockMvc.perform(
            patch("/devices/{uuid}/air-conditioner", deviceId).authenticated()
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"targetTemperature":35}"""),
        )
            .andExpect(status().isBadRequest)
            .andReturn().response.contentAsString

        // Then
        val response = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Value '35' for 'tempSel' is out of the admitted range"
    }

    @Test fun `setAcSettings() returns 502 surfacing the provider message when the provider fails`() {
        // Given
        val deviceId = UUID.randomUUID()
        every { setAcSettingsUseCase.execute(deviceId, any()) } returns
            AcProviderFailure("provider exploded").left()

        // When
        val responseBody = mockMvc.perform(
            patch("/devices/{uuid}/air-conditioner", deviceId).authenticated()
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"power":"ON"}"""),
        )
            .andExpect(status().isBadGateway)
            .andReturn().response.contentAsString

        // Then
        val response = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "provider exploded"
    }

    @Test fun `setAcSettings() returns 500 when the repository fails`() {
        // Given
        val deviceId = UUID.randomUUID()
        every { setAcSettingsUseCase.execute(deviceId, any()) } returns DeviceRepositoryError.left()

        // When
        val responseBody = mockMvc.perform(
            patch("/devices/{uuid}/air-conditioner", deviceId).authenticated()
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"power":"ON"}"""),
        )
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        // Then
        val response = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Unable to retrieve device '$deviceId'!"
    }
}
