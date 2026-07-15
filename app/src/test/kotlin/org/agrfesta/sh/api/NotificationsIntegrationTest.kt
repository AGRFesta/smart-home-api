package org.agrfesta.sh.api

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.agrfesta.sh.api.controllers.NotificationsPageResponse
import org.agrfesta.sh.api.controllers.authenticated
import org.agrfesta.sh.api.core.application.ports.inbounds.sensors.FetchSensorReadingsUseCase
import org.agrfesta.sh.api.core.domain.commons.Percentage
import org.agrfesta.sh.api.core.domain.devices.DeviceModel
import org.agrfesta.sh.api.core.domain.notifications.NotificationEvent
import org.agrfesta.sh.api.domain.aProviderDeviceData
import org.agrfesta.sh.api.persistence.jdbc.repositories.DevicesJdbcRepository
import org.agrfesta.sh.api.providers.switchbot.SwitchBotClientAsserter
import org.agrfesta.test.mothers.aRandomIntHumidity
import org.agrfesta.test.mothers.aRandomThermoHygroData
import org.junit.jupiter.api.Test
import java.util.UUID

class NotificationsIntegrationTest(
    private val fetchSensorReadings: FetchSensorReadingsUseCase,
    private val devicesRepository: DevicesJdbcRepository,
    private val switchBotClientAsserter: SwitchBotClientAsserter,
    private val objectMapper: ObjectMapper
) : AbstractIntegrationTest() {

    @Test
    fun `alert transitions emit notifications inspectable via GET notifications`() {
        // Given a paired SwitchBot Meter reporting a battery below the trigger threshold
        val sensor = aProviderDeviceData(model = DeviceModel("switchbot/Meter"))
        devicesRepository.persist(UUID.randomUUID(), sensor)
        switchBotClientAsserter.givenSensorData(
            sensorProviderId = sensor.deviceProviderId,
            data = aRandomThermoHygroData(relativeHumidity = Percentage.ofHundreds(aRandomIntHumidity())),
            battery = 10 // below alerts.battery-low.trigger (15)
        )

        // When the polling cycle runs
        fetchSensorReadings.execute()

        // Then exactly one OPENED notification is inspectable via the API
        val afterOpen = getNotifications()
        withClue("opening the alert must emit exactly one OPENED notification with the open-time snapshot") {
            afterOpen.total shouldBe 1L
            afterOpen.items.single().event shouldBe NotificationEvent.OPENED
            afterOpen.items.single().payload shouldBe "battery=10%"
        }

        // When the battery recovers above the clear threshold and the polling cycle runs again
        switchBotClientAsserter.givenSensorData(
            sensorProviderId = sensor.deviceProviderId,
            data = aRandomThermoHygroData(relativeHumidity = Percentage.ofHundreds(aRandomIntHumidity())),
            battery = 30 // above alerts.battery-low.clear (25)
        )
        fetchSensorReadings.execute()

        // Then a RESOLVED notification joins the OPENED one, most recent first
        val afterResolve = getNotifications()
        withClue("resolving the alert must add exactly one RESOLVED notification, most recent first") {
            afterResolve.total shouldBe 2L
            afterResolve.items.map { it.event }
                .shouldContainExactly(NotificationEvent.RESOLVED, NotificationEvent.OPENED)
        }
        withClue("both notifications must project the same alert") {
            afterResolve.items.map { it.alertUuid }.toSet() shouldBe setOf(afterOpen.items.single().alertUuid)
        }
    }

    private fun getNotifications(): NotificationsPageResponse {
        val responseBody = given()
            .contentType(ContentType.JSON)
            .authenticated()
            .`when`()
            .get("/notifications")
            .then()
            .statusCode(200)
            .extract()
            .asString()
        return objectMapper.readValue(responseBody, NotificationsPageResponse::class.java)
    }
}
