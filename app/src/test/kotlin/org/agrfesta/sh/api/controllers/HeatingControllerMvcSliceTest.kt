package org.agrfesta.sh.api.controllers

import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.verify
import org.agrfesta.sh.api.core.application.ports.inbounds.heating.SetHeatingEnabledUseCase
import org.agrfesta.sh.api.core.domain.failures.PropertyRepositoryError
import org.agrfesta.sh.api.security.SecurityConfig
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestConstructor
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(HeatingController::class)
@Import(SecurityConfig::class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@ActiveProfiles("test")
class HeatingControllerMvcSliceTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    @MockkBean private val setHeatingEnabledUseCase: SetHeatingEnabledUseCase
) {

    private fun putEnabled(content: String?): ResultActions = mockMvc.perform(
        put("/heating/enabled")
            .authenticated()
            .contentType(MediaType.APPLICATION_JSON)
            .apply { content?.let { content(it) } }
    )

    private val authTestSupport = AuthTestSupport(mockMvc, objectMapper)

    @TestFactory fun `setHeatingEnabled() auth tests`() = authTestSupport.dynamicTestsBy {
        put("/heating/enabled")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{ "enabled": true }""")
    }

    @Test fun `setHeatingEnabled() ignores a field the endpoint does not declare`() {
        // Given
        every { setHeatingEnabledUseCase.execute(true) } returns Unit.right()

        // When
        val result = putEnabled("""{ "enabled": true, "foo": 1 }""")

        // Then
        result.andExpect(status().isOk)
    }

    @Test fun `setHeatingEnabled() returns 200 with enabled true and enables heating`() {
        // Given
        every { setHeatingEnabledUseCase.execute(true) } returns Unit.right()

        // When
        val responseBody = putEnabled("""{ "enabled": true }""")
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        // Then
        withClue("the response should echo the saved flag") {
            objectMapper.readValue(responseBody, HeatingEnabledResponse::class.java) shouldBe
                HeatingEnabledResponse(enabled = true)
        }
    }

    @Test fun `setHeatingEnabled() returns 200 with enabled false and disables heating`() {
        // Given
        every { setHeatingEnabledUseCase.execute(false) } returns Unit.right()

        // When
        val responseBody = putEnabled("""{ "enabled": false }""")
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        // Then
        withClue("the response should echo the saved flag") {
            objectMapper.readValue(responseBody, HeatingEnabledResponse::class.java) shouldBe
                HeatingEnabledResponse(enabled = false)
        }
    }

    @Test fun `setHeatingEnabled() returns 500 when the flag cannot be saved`() {
        // Given
        every { setHeatingEnabledUseCase.execute(false) } returns PropertyRepositoryError.left()

        // When
        val responseBody = putEnabled("""{ "enabled": false }""")
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        // Then
        withClue("the response should explain that the flag was not saved") {
            objectMapper.readValue(responseBody, MessageResponse::class.java) shouldBe
                MessageResponse("Unable to save the heating.enabled flag")
        }
    }

    @Test fun `setHeatingEnabled() returns 415 when the body is not declared as JSON`() {
        // When
        val result = mockMvc.perform(
            put("/heating/enabled")
                .authenticated()
                .contentType(MediaType.TEXT_PLAIN)
                .content("""{ "enabled": true }""")
        )

        // Then
        result.andExpect(status().isUnsupportedMediaType)
    }

    @TestFactory fun `setHeatingEnabled() returns 400 and keeps the flag when enabled is not a JSON boolean`() =
        listOf(
            "enabled is a non-boolean string" to """{ "enabled": "yes" }""",
            "enabled is the string true" to """{ "enabled": "true" }""",
            "enabled is a number" to """{ "enabled": 1 }""",
            "enabled is null" to """{ "enabled": null }""",
            "enabled is missing" to "{}",
            "there is no body" to null,
            "the body is an array" to "[true]",
            "the body is not JSON" to "enabled=true"
        ).map { (case, body) ->
            DynamicTest.dynamicTest("when $case") {
                // When
                val responseBody = putEnabled(body)
                    .andExpect(status().isBadRequest)
                    .andReturn().response.contentAsString

                // Then
                withClue("the response should explain the expected body") {
                    objectMapper.readValue(responseBody, MessageResponse::class.java) shouldBe
                        MessageResponse("The body must be a JSON object with a boolean 'enabled' field")
                }
                withClue("the flag should stay unchanged when the body is rejected") {
                    verify(exactly = 0) { setHeatingEnabledUseCase.execute(any()) }
                }
            }
        }
}
