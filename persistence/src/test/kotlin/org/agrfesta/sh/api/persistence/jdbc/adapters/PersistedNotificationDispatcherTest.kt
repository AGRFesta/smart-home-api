package org.agrfesta.sh.api.persistence.jdbc.adapters

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import org.agrfesta.sh.api.core.domain.alerts.AlertLifecycle
import org.agrfesta.sh.api.core.domain.failures.NotificationRepositoryError
import org.agrfesta.sh.api.core.domain.notifications.NotificationEvent
import org.agrfesta.sh.api.domain.aNotification
import org.agrfesta.sh.api.domain.anAlert
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataAccessResourceFailureException
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class PersistedNotificationDispatcherTest : AbstractJdbcAdapterTest() {

    @Autowired private lateinit var sut: PersistedNotificationDispatcher
    @Autowired private lateinit var notificationsAdapter: NotificationsJdbcAdapter
    @Autowired private lateinit var alertsAdapter: AlertsJdbcAdapter

    @Test
    fun `dispatch() persists a notification retrievable via findAll()`() {
        // Given
        val alert = anAlert(
            lifecycle = AlertLifecycle.Open,
            openedAt = Instant.now().truncatedTo(ChronoUnit.MICROS)
        )
        alertsAdapter.create(alert).shouldBeRight()
        val notification = aNotification(
            uuid = UUID.randomUUID(),
            alertUuid = alert.uuid,
            event = NotificationEvent.OPENED,
            sentAt = Instant.now().truncatedTo(ChronoUnit.MICROS),
            payload = "battery=10%"
        )

        // When
        sut.dispatch(notification).shouldBeRight()

        // Then
        withClue("the dispatched notification should be queryable with all its fields intact") {
            notificationsAdapter.findAll(offset = 0, limit = 10)
                .shouldBeRight()
                .shouldContainExactly(notification)
        }
    }

    @Test
    fun `dispatch() returns NotificationRepositoryError when persistence fails`() {
        // Given
        every { notificationsRepo.persist(any()) } throws
            DataAccessResourceFailureException("notification persistence failure")

        // When / Then
        withClue("a persistence exception must surface as the typed NotificationRepositoryError, never propagate") {
            sut.dispatch(aNotification())
                .shouldBeLeft()
                .shouldBe(NotificationRepositoryError)
        }
    }
}
