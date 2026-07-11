package org.agrfesta.sh.api.core.application.usecases

import arrow.core.right
import io.kotest.assertions.withClue
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.agrfesta.sh.api.core.application.ports.outbounds.TimeProvider
import org.agrfesta.sh.api.core.application.ports.outbounds.notifications.NotificationsRepository
import org.agrfesta.sh.api.core.domain.notifications.RetentionPolicy
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class PruneNotificationsServiceTest {

    private val notificationsRepository: NotificationsRepository = mockk()
    private val timeProvider: TimeProvider = mockk()
    private val retention = Duration.ofDays(90)

    private val sut = PruneNotificationsService(
        notificationsRepository,
        RetentionPolicy(retention),
        timeProvider
    )

    @Test
    fun `execute() deletes the notifications older than the retention window`() {
        // Given
        val now = Instant.parse("2026-07-09T12:00:00Z")
        every { timeProvider.now() } returns now
        every { notificationsRepository.deleteOlderThan(now.minus(retention)) } returns 3.right()

        // When
        sut.execute()

        // Then
        withClue("the prune threshold should be now minus the retention window") {
            verify(exactly = 1) { notificationsRepository.deleteOlderThan(now.minus(retention)) }
        }
    }
}
