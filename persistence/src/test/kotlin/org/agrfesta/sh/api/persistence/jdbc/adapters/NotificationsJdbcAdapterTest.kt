package org.agrfesta.sh.api.persistence.jdbc.adapters

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import org.agrfesta.sh.api.core.domain.alerts.AlertLifecycle
import org.agrfesta.sh.api.core.domain.failures.NotificationRepositoryError
import org.agrfesta.sh.api.domain.aNotification
import org.agrfesta.sh.api.domain.anAlert
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class NotificationsJdbcAdapterTest : AbstractJdbcAdapterTest() {

    @Autowired private lateinit var sut: NotificationsJdbcAdapter
    @Autowired private lateinit var dispatcher: PersistedNotificationDispatcher
    @Autowired private lateinit var alertsAdapter: AlertsJdbcAdapter
    @Autowired private lateinit var jdbcTemplate: NamedParameterJdbcTemplate

    @Test
    fun `findAll() returns the notifications most recent first honouring offset and limit`() {
        // Given
        val alert = anAlert(
            lifecycle = AlertLifecycle.Open,
            openedAt = Instant.now().truncatedTo(ChronoUnit.MICROS)
        )
        alertsAdapter.create(alert).shouldBeRight()
        val base = Instant.now().truncatedTo(ChronoUnit.MICROS)
        val oldest = aNotification(alertUuid = alert.uuid, sentAt = base.minusSeconds(120))
        val middle = aNotification(alertUuid = alert.uuid, sentAt = base.minusSeconds(60))
        val newest = aNotification(alertUuid = alert.uuid, sentAt = base)
        // insertion order deliberately differs from the sent_at order
        dispatcher.dispatch(oldest).shouldBeRight()
        dispatcher.dispatch(newest).shouldBeRight()
        dispatcher.dispatch(middle).shouldBeRight()

        // When / Then
        withClue("the first page must hold the most recent notifications, most recent first") {
            sut.findAll(offset = 0, limit = 2)
                .shouldBeRight()
                .shouldContainExactly(newest, middle)
        }
        withClue("the offset must skip the most recent notifications") {
            sut.findAll(offset = 2, limit = 2)
                .shouldBeRight()
                .shouldContainExactly(oldest)
        }
    }

    @Test
    fun `findAll() breaks sent_at ties by uuid so pagination is stable`() {
        // Given three notifications sharing the exact same sent_at
        val alert = anAlert(
            lifecycle = AlertLifecycle.Open,
            openedAt = Instant.now().truncatedTo(ChronoUnit.MICROS)
        )
        alertsAdapter.create(alert).shouldBeRight()
        val sentAt = Instant.now().truncatedTo(ChronoUnit.MICROS)
        val tied = List(3) { aNotification(alertUuid = alert.uuid, sentAt = sentAt) }
            .sortedBy { it.uuid.toString() }
        // insertion order deliberately reversed w.r.t. the expected uuid tiebreaker
        tied.reversed().forEach { dispatcher.dispatch(it).shouldBeRight() }

        // When / Then
        withClue("equal-sent_at rows must come back in a deterministic uuid order, not insertion order") {
            sut.findAll(offset = 0, limit = 10)
                .shouldBeRight()
                .shouldContainExactly(tied)
        }
    }

    @Test
    fun `count() returns the overall number of notifications across all pages`() {
        // Given
        val alert = anAlert(
            lifecycle = AlertLifecycle.Open,
            openedAt = Instant.now().truncatedTo(ChronoUnit.MICROS)
        )
        alertsAdapter.create(alert).shouldBeRight()
        repeat(3) {
            dispatcher.dispatch(aNotification(alertUuid = alert.uuid)).shouldBeRight()
        }

        // When / Then
        withClue("count() must span all pages, not just the first one") {
            sut.count().shouldBeRight() shouldBe 3L
        }
    }

    @Test
    fun `deleteOlderThan() deletes only the notifications sent before the threshold`() {
        // Given
        val alert = anAlert(
            lifecycle = AlertLifecycle.Open,
            openedAt = Instant.now().truncatedTo(ChronoUnit.MICROS)
        )
        alertsAdapter.create(alert).shouldBeRight()
        val threshold = Instant.now().truncatedTo(ChronoUnit.MICROS)
        val expired = aNotification(alertUuid = alert.uuid, sentAt = threshold.minusSeconds(60))
        val retained = aNotification(alertUuid = alert.uuid, sentAt = threshold.plusSeconds(60))
        dispatcher.dispatch(expired).shouldBeRight()
        dispatcher.dispatch(retained).shouldBeRight()

        // When
        val deleted = sut.deleteOlderThan(threshold)

        // Then
        withClue("only the notification sent before the threshold should be deleted") {
            deleted.shouldBeRight() shouldBe 1
        }
        withClue("the notification sent after the threshold should survive the prune") {
            sut.findAll(offset = 0, limit = 10)
                .shouldBeRight()
                .shouldContainExactly(retained)
        }
    }

    @Test
    fun `findAll() returns NotificationRepositoryError when a persisted row cannot be mapped to the domain`() {
        // Given a notification row whose event is not a known NotificationEvent
        val alert = anAlert(
            lifecycle = AlertLifecycle.Open,
            openedAt = Instant.now().truncatedTo(ChronoUnit.MICROS)
        )
        alertsAdapter.create(alert).shouldBeRight()
        jdbcTemplate.update(
            """
            INSERT INTO smart_home.notification (uuid, alert_uuid, event, sent_at)
            VALUES (:uuid, :alertUuid, 'FOO', now())
            """,
            mapOf("uuid" to UUID.randomUUID(), "alertUuid" to alert.uuid)
        )

        // When / Then
        withClue("a corrupt row must surface as the typed NotificationRepositoryError, never propagate") {
            sut.findAll(offset = 0, limit = 10)
                .shouldBeLeft()
                .shouldBe(NotificationRepositoryError)
        }
    }

    @Test
    fun `findAll() returns NotificationRepositoryError when fetching fails`() {
        // Given
        every { notificationsRepo.findAll(any(), any()) } throws
            DataAccessResourceFailureException("notifications fetching failure")

        // When / Then
        withClue("a fetching exception must surface as the typed NotificationRepositoryError, never propagate") {
            sut.findAll(offset = 0, limit = 10)
                .shouldBeLeft()
                .shouldBe(NotificationRepositoryError)
        }
    }

    @Test
    fun `count() returns NotificationRepositoryError when counting fails`() {
        // Given
        every { notificationsRepo.count() } throws
            DataAccessResourceFailureException("notifications counting failure")

        // When / Then
        withClue("a counting exception must surface as the typed NotificationRepositoryError, never propagate") {
            sut.count()
                .shouldBeLeft()
                .shouldBe(NotificationRepositoryError)
        }
    }

    @Test
    fun `deleteOlderThan() returns NotificationRepositoryError when pruning fails`() {
        // Given
        every { notificationsRepo.deleteOlderThan(any()) } throws
            DataAccessResourceFailureException("notifications pruning failure")

        // When / Then
        withClue("a pruning exception must surface as the typed NotificationRepositoryError, never propagate") {
            sut.deleteOlderThan(Instant.now())
                .shouldBeLeft()
                .shouldBe(NotificationRepositoryError)
        }
    }
}
