package org.agrfesta.sh.api.core.application.usecases

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.agrfesta.sh.api.core.application.ports.outbounds.notifications.NotificationsRepository
import org.agrfesta.sh.api.core.application.readmodels.notifications.NotificationsPageView
import org.agrfesta.sh.api.core.domain.failures.NotificationRepositoryError
import org.agrfesta.sh.api.domain.aNotification
import org.junit.jupiter.api.Test

class GetNotificationsServiceTest {

    private val notificationsRepository: NotificationsRepository = mockk()

    private val sut = GetNotificationsService(notificationsRepository)

    @Test
    fun `execute() returns the requested page translating page and size into offset and limit`() {
        // Given
        val items = listOf(aNotification(), aNotification())
        every { notificationsRepository.findAll(offset = 40, limit = 20) } returns items.right()
        every { notificationsRepository.count() } returns 42L.right()

        // When
        val result = sut.execute(page = 2, size = 20)

        // Then
        withClue("the page should carry the requested items and the overall total") {
            result.shouldBeRight() shouldBe NotificationsPageView(items = items, page = 2, size = 20, total = 42L)
        }
    }

    /**
     * Regression guard (#194): an infrastructure failure surfaces as the typed use case failure,
     * never as an empty page.
     */
    @Test
    fun `execute() returns the typed failure when the notifications cannot be read`() {
        // Given
        every { notificationsRepository.findAll(any(), any()) } returns NotificationRepositoryError.left()

        // When
        val result = sut.execute(page = 0, size = 20)

        // Then
        result.shouldBeLeft() shouldBe NotificationRepositoryError
    }
}
