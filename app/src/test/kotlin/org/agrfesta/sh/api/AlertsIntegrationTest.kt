package org.agrfesta.sh.api

import arrow.core.getOrElse
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.agrfesta.sh.api.controllers.AlertResponse
import org.agrfesta.sh.api.controllers.authenticated
import org.agrfesta.sh.api.controllers.toResponse
import org.agrfesta.sh.api.core.application.ports.inbounds.sensors.FetchSensorReadingsUseCase
import org.agrfesta.sh.api.core.application.ports.outbounds.alerts.AlertsRepository
import org.agrfesta.sh.api.core.domain.alerts.AlertLifecycle
import org.agrfesta.sh.api.core.domain.alerts.AlertScope
import org.agrfesta.sh.api.core.domain.alerts.AlertStatus
import org.agrfesta.sh.api.core.domain.alerts.AlertType
import org.agrfesta.sh.api.core.domain.commons.Percentage
import org.agrfesta.sh.api.core.domain.devices.DeviceModel
import org.agrfesta.sh.api.domain.aProviderDeviceData
import org.agrfesta.sh.api.domain.anAlert
import org.agrfesta.sh.api.persistence.jdbc.repositories.DevicesJdbcRepository
import org.agrfesta.sh.api.providers.switchbot.SwitchBotClientAsserter
import org.agrfesta.test.mothers.aRandomIntHumidity
import org.agrfesta.test.mothers.aRandomThermoHygroData
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class AlertsIntegrationTest(
    private val alertsRepository: AlertsRepository,
    private val fetchSensorReadings: FetchSensorReadingsUseCase,
    private val devicesRepository: DevicesJdbcRepository,
    private val switchBotClientAsserter: SwitchBotClientAsserter,
    private val objectMapper: ObjectMapper
) : AbstractIntegrationTest() {

    @Test
    fun `GET alerts returns only the currently open alerts`() {
        val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        val openAlert = anAlert(lifecycle = AlertLifecycle.Open, openedAt = now)
        val resolvedAlert = anAlert(lifecycle = AlertLifecycle.Resolved(now), openedAt = now)
        alertsRepository.create(openAlert).getOrElse { error("Failed to create open alert: $it") }
        alertsRepository.create(resolvedAlert).getOrElse { error("Failed to create resolved alert: $it") }

        val alerts = getAlerts()

        alerts.shouldContainExactly(openAlert.toResponse())
    }

    @Test
    fun `a low battery reading opens a BATTERY_LOW alert visible via GET alerts`() {
        // Given a paired SwitchBot Meter reporting a battery below the trigger threshold
        val sensor = aProviderDeviceData(model = DeviceModel("switchbot/Meter"))
        val deviceId = UUID.randomUUID()
        devicesRepository.persist(deviceId, sensor)
        switchBotClientAsserter.givenSensorData(
            sensorProviderId = sensor.deviceProviderId,
            data = aRandomThermoHygroData(relativeHumidity = Percentage.ofHundreds(aRandomIntHumidity())),
            battery = 10 // below alerts.battery-low.trigger (15)
        )

        // When the polling cycle runs
        fetchSensorReadings.execute()

        // Then
        val alerts = getAlerts()
        withClue("the polling cycle must have opened exactly one alert") { alerts shouldHaveSize 1 }
        with(alerts.single()) {
            type shouldBe AlertType.BATTERY_LOW
            scope shouldBe AlertScope.DEVICE
            target shouldBe deviceId.toString()
            status shouldBe AlertStatus.OPEN
            details shouldBe "battery=10%"
        }
    }

    private fun getAlerts(): List<AlertResponse> {
        val responseBody = given()
            .contentType(ContentType.JSON)
            .authenticated()
            .`when`()
            .get("/alerts")
            .then()
            .statusCode(200)
            .extract()
            .asString()
        return objectMapper.readValue(responseBody, object : TypeReference<List<AlertResponse>>() {})
    }
}
