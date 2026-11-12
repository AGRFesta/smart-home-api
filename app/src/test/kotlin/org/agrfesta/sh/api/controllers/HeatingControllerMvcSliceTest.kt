package org.agrfesta.sh.api.controllers

import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.verify
import org.agrfesta.sh.api.core.application.ports.inbounds.heating.SetHeatingEnabledUseCase
import org.agrfesta.sh.api.core.application.ports.inbounds.heating.SetHeatingStrategyUseCase
import org.agrfesta.sh.api.core.domain.failures.PropertyRepositoryError
import org.agrfesta.sh.api.core.domain.heating.SharedHeatingStrategy.COMFORT
import org.agrfesta.sh.api.core.domain.heating.SharedHeatingStrategy.ECONOMY
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
    @MockkBean private val setHeatingEnabledUseCase: SetHeatingEnabledUseCase,
    @MockkBean private val setHeatingStrategyUseCase: SetHeatingStrategyUseCase
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

    // /// setHeatingStrategy ///////////////////////////////////////////////////////////////////////////////////////////

    private fun putStrategy(content: String?): ResultActions = mockMvc.perform(
        put("/heating/strategy")
            .authenticated()
            .contentType(MediaType.APPLICATION_JSON)
            .apply { content?.let { content(it) } }
    )

    @TestFactory fun `setHeatingStrategy() auth tests`() = authTestSupport.dynamicTestsBy {
        put("/heating/strategy")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{ "strategy": "COMFORT" }""")
    }

    @Test fun `setHeatingStrategy() ignores a field the endpoint does not declare`() {
        // Given
        every { setHeatingStrategyUseCase.execute(COMFORT) } returns Unit.right()

        // When
        val result = putStrategy("""{ "strategy": "COMFORT", "foo": 1 }""")

        // Then
        result.andExpect(status().isOk)
    }

    @Test fun `setHeatingStrategy() returns 200 with strategy COMFORT and saves COMFORT`() {
        // Given
        every { setHeatingStrategyUseCase.execute(COMFORT) } returns Unit.right()

        // When
        val responseBody = putStrategy("""{ "strategy": "COMFORT" }""")
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        // Then
        withClue("the response should echo the saved strategy") {
            objectMapper.readValue(responseBody, HeatingStrategyResponse::class.java) shouldBe
                HeatingStrategyResponse(strategy = COMFORT)
        }
        withClue("the strategy should be passed to the use case") {
            verify(exactly = 1) { setHeatingStrategyUseCase.execute(COMFORT) }
        }
    }

    @TestFactory fun `setHeatingStrategy() accepts the strategy in any case and without surrounding spaces`() =
        listOf(
            "economy" to ECONOMY,
            "Comfort" to COMFORT,
            " comfort " to COMFORT
        ).map { (value, expected) ->
            DynamicTest.dynamicTest("when strategy is '$value'") {
                // Given
                clearMocks(setHeatingStrategyUseCase)
                every { setHeatingStrategyUseCase.execute(expected) } returns Unit.right()

                // When
                val responseBody = putStrategy("""{ "strategy": "$value" }""")
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                // Then
                withClue("the response should echo the canonical strategy") {
                    objectMapper.readValue(responseBody, HeatingStrategyResponse::class.java) shouldBe
                        HeatingStrategyResponse(strategy = expected)
                }
                withClue("the canonical strategy should be passed to the use case") {
                    verify(exactly = 1) { setHeatingStrategyUseCase.execute(expected) }
                }
            }
        }

    @Test fun `setHeatingStrategy() returns 500 when the strategy cannot be saved`() {
        // Given
        every { setHeatingStrategyUseCase.execute(COMFORT) } returns PropertyRepositoryError.left()

        // When
        val responseBody = putStrategy("""{ "strategy": "COMFORT" }""")
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        // Then
        withClue("the response should explain that the strategy was not saved") {
            objectMapper.readValue(responseBody, MessageResponse::class.java) shouldBe
                MessageResponse("Unable to save the heating strategy")
        }
    }

    @Test fun `setHeatingStrategy() returns 415 when the body is not declared as JSON`() {
        // Given
        every { setHeatingStrategyUseCase.execute(COMFORT) } returns Unit.right()

        // When
        val result = mockMvc.perform(
            put("/heating/strategy")
                .authenticated()
                .contentType(MediaType.TEXT_PLAIN)
                .content("""{ "strategy": "COMFORT" }""")
        )

        // Then
        result.andExpect(status().isUnsupportedMediaType)
    }

    @TestFactory fun `setHeatingStrategy() returns 400 and keeps the strategy when strategy is not an allowed value`() =
        listOf(
            "strategy is not an allowed value" to """{ "strategy": "FOO" }""",
            "strategy is empty" to """{ "strategy": "" }""",
            "strategy is a number" to """{ "strategy": 1 }""",
            "strategy is null" to """{ "strategy": null }""",
            "strategy is missing" to "{}",
            "there is no body" to null,
            "the body is an array" to """["COMFORT"]""",
            "the body is not JSON" to "strategy=COMFORT"
        ).map { (case, body) ->
            DynamicTest.dynamicTest("when $case") {
                // When
                val responseBody = putStrategy(body)
                    .andExpect(status().isBadRequest)
                    .andReturn().response.contentAsString

                // Then
                withClue("the response should list the allowed strategies") {
                    objectMapper.readValue(responseBody, MessageResponse::class.java) shouldBe MessageResponse(
                        "The body must be a JSON object with a 'strategy' field: one of ECONOMY, COMFORT"
                    )
                }
                withClue("the strategy should stay unchanged when the body is rejected") {
                    verify(exactly = 0) { setHeatingStrategyUseCase.execute(any()) }
                }
            }
        }
}
