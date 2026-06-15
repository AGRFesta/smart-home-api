package org.agrfesta.sh.api.persistence.jdbc.adapters

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import org.agrfesta.sh.api.core.domain.alerts.AlertLifecycle
import org.agrfesta.sh.api.core.domain.alerts.AlertStatus
import org.agrfesta.sh.api.core.domain.alerts.AlertTarget
import org.agrfesta.sh.api.core.domain.failures.AlertAlreadyOpen
import org.agrfesta.sh.api.core.domain.failures.AlertRepositoryError
import org.agrfesta.sh.api.domain.anAlert
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class AlertsJdbcAdapterTest : AbstractJdbcAdapterTest() {

    @Autowired private lateinit var sut: AlertsJdbcAdapter
    @Autowired private lateinit var jdbcTemplate: NamedParameterJdbcTemplate

    @Test
    fun `getAlerts() returns empty collection when no alerts exist`() {
        sut.getAlerts()
            .shouldBeRight()
            .shouldHaveSize(0)
    }

    @Test
    fun `create() persists an alert retrievable via getAlerts()`() {
        // Given
        val alert = anAlert(
            lifecycle = AlertLifecycle.Open,
            openedAt = Instant.now().truncatedTo(ChronoUnit.MICROS),
            details = "battery=10%"
        )

        // When
        sut.create(alert).shouldBeRight()

        // Then
        sut.getAlerts()
            .shouldBeRight()
            .shouldContainExactly(alert)
    }

    @Test
    fun `getAlerts() filters by status`() {
        // Given
        val now = Instant.now().truncatedTo(ChronoUnit.MICROS)
        val openAlert = anAlert(lifecycle = AlertLifecycle.Open, openedAt = now)
        val resolvedAlert = anAlert(lifecycle = AlertLifecycle.Resolved(now), openedAt = now)
        sut.create(openAlert).shouldBeRight()
        sut.create(resolvedAlert).shouldBeRight()

        // When / Then
        withClue("getAlerts(OPEN) must return only OPEN alerts") {
            sut.getAlerts(AlertStatus.OPEN).shouldBeRight()
                .map { it.lifecycle.status }.shouldContainExactly(AlertStatus.OPEN)
        }
        withClue("getAlerts(RESOLVED) must return only RESOLVED alerts") {
            sut.getAlerts(AlertStatus.RESOLVED).shouldBeRight()
                .map { it.lifecycle.status }.shouldContainExactly(AlertStatus.RESOLVED)
        }
    }

    @Test
    fun `create() returns AlertAlreadyOpen when an OPEN alert already exists for the same type and target`() {
        // Given
        val target = AlertTarget.Device(UUID.randomUUID())
        val now = Instant.now().truncatedTo(ChronoUnit.MICROS)
        sut.create(anAlert(target = target, lifecycle = AlertLifecycle.Open, openedAt = now)).shouldBeRight()

        // When
        val result = sut.create(anAlert(target = target, lifecycle = AlertLifecycle.Open, openedAt = now))

        // Then
        result.shouldBeLeft().shouldBe(AlertAlreadyOpen)
    }

    @Test
    fun `create() returns AlertRepositoryError when persistence fails`() {
        // Given
        every { alertsRepo.persist(any()) } throws DataAccessResourceFailureException("alert creation failure")

        // When / Then
        sut.create(anAlert())
            .shouldBeLeft()
            .shouldBe(AlertRepositoryError)
    }

    @Test
    fun `getAlerts() returns AlertRepositoryError when fetching fails`() {
        // Given
        every { alertsRepo.findAlerts(any()) } throws DataAccessResourceFailureException("alerts fetching failure")

        // When / Then
        sut.getAlerts()
            .shouldBeLeft()
            .shouldBe(AlertRepositoryError)
    }

    @Test
    fun `getAlerts() returns AlertRepositoryError when a persisted row cannot be mapped to the domain`() {
        // Given a DEVICE-scoped row whose target is not a valid UUID
        jdbcTemplate.update(
            """
            INSERT INTO smart_home.alert (uuid, type, scope, target, status, opened_at)
            VALUES (:uuid, 'BATTERY_LOW', 'DEVICE', 'not-a-uuid', 'OPEN', now())
            """,
            mapOf("uuid" to UUID.randomUUID())
        )

        // When / Then
        sut.getAlerts()
            .shouldBeLeft()
            .shouldBe(AlertRepositoryError)
    }
}
