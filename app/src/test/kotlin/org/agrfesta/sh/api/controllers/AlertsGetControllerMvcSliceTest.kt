package org.agrfesta.sh.api.controllers

import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.verify
import org.agrfesta.sh.api.core.application.ports.inbounds.GetAlertsUseCase
import org.agrfesta.sh.api.core.domain.alerts.Alert
import org.agrfesta.sh.api.core.domain.alerts.AlertStatus
import org.agrfesta.sh.api.core.domain.failures.AlertRepositoryError
import org.agrfesta.sh.api.domain.anAlert
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

@WebMvcTest(AlertsController::class)
@Import(SecurityConfig::class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@ActiveProfiles("test")
class AlertsGetControllerMvcSliceTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    @MockkBean private val getAlertsUseCase: GetAlertsUseCase
) {
    private val authTestSupport = AuthTestSupport(mockMvc, objectMapper)

    @TestFactory fun `getAlerts() auth tests`() = authTestSupport.dynamicTestsBy {
        get("/alerts")
    }

    @Test fun `getAlerts() returns 200 with an empty array when the use case returns no alerts`() {
        // Given
        every { getAlertsUseCase.execute(any()) } returns emptyList<Alert>().right()

        // When
        val responseBody = mockMvc.perform(get("/alerts").authenticated())
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        // Then
        val response: List<AlertResponse> =
            objectMapper.readValue(responseBody, object : TypeReference<List<AlertResponse>>() {})
        response.shouldBeEmpty()
    }

    @Test fun `getAlerts() returns 200 with the alerts in AlertResponse shape`() {
        // Given
        val alert = anAlert(openedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS))
        every { getAlertsUseCase.execute(any()) } returns listOf(alert).right()

        // When
        val responseBody = mockMvc.perform(get("/alerts").authenticated())
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        // Then
        val response: List<AlertResponse> =
            objectMapper.readValue(responseBody, object : TypeReference<List<AlertResponse>>() {})
        response.shouldContainExactly(alert.toResponse())
    }

    @Test fun `getAlerts() without a status param invokes the use case with null leaving the default to the service`() {
        // Given
        every { getAlertsUseCase.execute(null) } returns emptyList<Alert>().right()

        // When
        mockMvc.perform(get("/alerts").authenticated()).andExpect(status().isOk)

        // Then
        verify { getAlertsUseCase.execute(null) }
    }

    @Test fun `getAlerts() binds the status query param and forwards it to the use case`() {
        // Given
        every { getAlertsUseCase.execute(AlertStatus.RESOLVED) } returns emptyList<Alert>().right()

        // When
        mockMvc.perform(get("/alerts?status=RESOLVED").authenticated()).andExpect(status().isOk)

        // Then
        verify { getAlertsUseCase.execute(AlertStatus.RESOLVED) }
    }

    @Test fun `getAlerts() returns 500 with a message when the use case fails with AlertRepositoryError`() {
        // Given
        every { getAlertsUseCase.execute(any()) } returns AlertRepositoryError.left()

        // When
        val responseBody = mockMvc.perform(get("/alerts").authenticated())
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        // Then
        val response = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Unable to retrieve alerts!"
    }

    @Test fun `getAlerts() returns 400 when the status query param is not a valid value`() {
        mockMvc.perform(get("/alerts?status=FOO").authenticated())
            .andExpect(status().isBadRequest)
    }
}
