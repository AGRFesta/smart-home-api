package org.agrfesta.sh.api

import arrow.core.getOrElse
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.matchers.collections.shouldContainExactly
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.agrfesta.sh.api.controllers.AlertResponse
import org.agrfesta.sh.api.controllers.authenticated
import org.agrfesta.sh.api.controllers.toResponse
import org.agrfesta.sh.api.core.application.ports.outbounds.alerts.AlertsRepository
import org.agrfesta.sh.api.core.domain.alerts.AlertLifecycle
import org.agrfesta.sh.api.domain.anAlert
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class AlertsIntegrationTest(
    private val alertsRepository: AlertsRepository,
    private val objectMapper: ObjectMapper
) : AbstractIntegrationTest() {

    @Test
    fun `GET alerts returns only the currently open alerts`() {
        val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        val openAlert = anAlert(lifecycle = AlertLifecycle.Open, openedAt = now)
        val resolvedAlert = anAlert(lifecycle = AlertLifecycle.Resolved(now), openedAt = now)
        alertsRepository.create(openAlert).getOrElse { error("Failed to create open alert: $it") }
        alertsRepository.create(resolvedAlert).getOrElse { error("Failed to create resolved alert: $it") }

        val responseBody = given()
            .contentType(ContentType.JSON)
            .authenticated()
            .`when`()
            .get("/alerts")
            .then()
            .statusCode(200)
            .extract()
            .asString()

        val alerts: List<AlertResponse> =
            objectMapper.readValue(responseBody, object : TypeReference<List<AlertResponse>>() {})
        alerts.shouldContainExactly(openAlert.toResponse())
    }
}
