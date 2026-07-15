package org.agrfesta.sh.api.controllers

import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.verify
import org.agrfesta.sh.api.core.application.ports.inbounds.notifications.GetNotificationsUseCase
import org.agrfesta.sh.api.core.application.readmodels.notifications.NotificationsPageView
import org.agrfesta.sh.api.core.domain.failures.NotificationRepositoryError
import org.agrfesta.sh.api.domain.aNotification
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

@WebMvcTest(NotificationsController::class)
@Import(SecurityConfig::class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@ActiveProfiles("test")
class NotificationsGetControllerMvcSliceTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    @MockkBean private val getNotificationsUseCase: GetNotificationsUseCase
) {
    private val authTestSupport = AuthTestSupport(mockMvc, objectMapper)

    @TestFactory fun `getNotifications() auth tests`() = authTestSupport.dynamicTestsBy {
        get("/notifications")
    }

    @Test fun `getNotifications() returns 200 with the page envelope defaulting to page 0 and size 20`() {
        // Given
        val page = NotificationsPageView(
            items = listOf(aNotification(sentAt = Instant.now().truncatedTo(ChronoUnit.SECONDS))),
            page = 0,
            size = 20,
            total = 1L
        )
        every { getNotificationsUseCase.execute(page = 0, size = 20) } returns page.right()

        // When
        val responseBody = mockMvc.perform(get("/notifications").authenticated())
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        // Then
        val response = objectMapper.readValue(responseBody, NotificationsPageResponse::class.java)
        withClue("the envelope should carry the page items and the pagination metadata") {
            response shouldBe page.toResponse()
        }
        withClue("an omitted page/size should default to page 0 and size 20") {
            verify(exactly = 1) { getNotificationsUseCase.execute(page = 0, size = 20) }
        }
    }

    /** Regression guard (#194): the explicit page/size params reach the use case as given. */
    @Test fun `getNotifications() binds page and size and forwards them to the use case`() {
        // Given
        val page = NotificationsPageView(items = emptyList(), page = 2, size = 50, total = 0L)
        every { getNotificationsUseCase.execute(page = 2, size = 50) } returns page.right()

        // When
        mockMvc.perform(get("/notifications?page=2&size=50").authenticated())
            .andExpect(status().isOk)

        // Then
        verify(exactly = 1) { getNotificationsUseCase.execute(page = 2, size = 50) }
    }

    @Test fun `getNotifications() returns 400 when page or size are out of bounds`() {
        withClue("a negative page must be rejected") {
            mockMvc.perform(get("/notifications?page=-1").authenticated())
                .andExpect(status().isBadRequest)
        }
        withClue("a non-positive size must be rejected") {
            mockMvc.perform(get("/notifications?size=0").authenticated())
                .andExpect(status().isBadRequest)
        }
        withClue("a size above the maximum (100) must be rejected") {
            mockMvc.perform(get("/notifications?size=101").authenticated())
                .andExpect(status().isBadRequest)
        }
        withClue("a page whose offset would overflow Int must be rejected, not surface as a 500") {
            mockMvc.perform(get("/notifications?page=21474837&size=100").authenticated())
                .andExpect(status().isBadRequest)
        }
    }

    @Test fun `getNotifications() returns 500 with a message when the use case fails`() {
        // Given
        every { getNotificationsUseCase.execute(any(), any()) } returns NotificationRepositoryError.left()

        // When
        val responseBody = mockMvc.perform(get("/notifications").authenticated())
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        // Then
        val response = objectMapper.readValue(responseBody, MessageResponse::class.java)
        withClue("the failure should surface as an explanatory message, not a raw error") {
            response.message shouldBe "Unable to retrieve notifications!"
        }
    }
}
