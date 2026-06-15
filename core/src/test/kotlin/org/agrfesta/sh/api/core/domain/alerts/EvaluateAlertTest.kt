package org.agrfesta.sh.api.core.domain.alerts

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.agrfesta.sh.api.domain.anAlert
import org.agrfesta.sh.api.domain.anAlertSubject
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class EvaluateAlertTest {

    @Test
    fun `opens a new alert when the condition is met and none is open`() {
        // Given
        val deviceTarget = AlertTarget.Device(UUID.randomUUID())
        val subject = anAlertSubject(
            type = AlertType.BATTERY_LOW,
            target = deviceTarget,
            details = "battery=10%"
        )
        val newId = UUID.randomUUID()
        val now = Instant.now()

        // When
        val transition = evaluateAlert(
            current = null,
            conditionMet = true,
            subject = subject,
            newId = newId,
            at = now
        )

        // Then
        val opened = transition.shouldBeInstanceOf<AlertTransition.Opened>()
        opened.alert.uuid shouldBe newId
        opened.alert.type shouldBe AlertType.BATTERY_LOW
        opened.alert.target shouldBe deviceTarget
        opened.alert.details shouldBe "battery=10%"
        opened.alert.lifecycle shouldBe AlertLifecycle.Open
        opened.alert.openedAt shouldBe now
    }

    @Test
    fun `does nothing when no alert is open and the condition is not met`() {
        // Given
        val current: Alert? = null

        // When
        val transition = evaluateAlert(
            current = current,
            conditionMet = false,
            subject = anAlertSubject(),
            newId = UUID.randomUUID(),
            at = Instant.now()
        )

        // Then
        transition shouldBe AlertTransition.Unchanged
    }

    @Test
    fun `does nothing when an alert is already open and the condition is still met`() {
        // Given
        val current = anAlert(lifecycle = AlertLifecycle.Open)

        // When
        val transition = evaluateAlert(
            current = current,
            conditionMet = true,
            subject = anAlertSubject(),
            newId = UUID.randomUUID(),
            at = Instant.now()
        )

        // Then
        transition shouldBe AlertTransition.Unchanged
    }

    @Test
    fun `resolves the open alert when the condition is no longer met`() {
        // Given
        val now = Instant.now()
        val current = anAlert(lifecycle = AlertLifecycle.Open)

        // When
        val transition = evaluateAlert(
            current = current,
            conditionMet = false,
            subject = anAlertSubject(),
            newId = UUID.randomUUID(),
            at = now
        )

        // Then
        val resolved = transition.shouldBeInstanceOf<AlertTransition.Resolved>()
        resolved.alert.lifecycle shouldBe AlertLifecycle.Resolved(now)
        resolved.alert.uuid shouldBe current.uuid
        resolved.alert.type shouldBe current.type
        resolved.alert.target shouldBe current.target
        resolved.alert.openedAt shouldBe current.openedAt
        resolved.alert.details shouldBe current.details
    }
}
