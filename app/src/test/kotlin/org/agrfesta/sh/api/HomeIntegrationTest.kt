package org.agrfesta.sh.api

import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.restassured.RestAssured
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.agrfesta.sh.api.controllers.authenticated
import org.agrfesta.sh.api.core.application.ports.inbounds.AssignSensorToAreaUseCase
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.AreasRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicesRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.sensors.SensorsCurrentReadingsRepository
import org.agrfesta.sh.api.core.application.usecases.EvaluateHeatingStateService.Companion.HEATING_ENABLED_KEY
import org.agrfesta.sh.api.core.application.usecases.heating.DynamicSharedHeatingStrategyService.Companion.HEATING_STRATEGY_KEY
import org.agrfesta.sh.api.domain.aSensorProviderData
import org.agrfesta.sh.api.domain.anAreaDto
import org.agrfesta.sh.api.persistence.jdbc.repositories.PropertyJdbcRepository
import org.agrfesta.test.mothers.aRandomThermoHygroData
import org.agrfesta.test.mothers.aRandomUniqueString
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.concurrent.thread

class HomeIntegrationTest(
    private val areasRepository: AreasRepository,
    private val devicesRepository: DevicesRepository,
    private val assignSensorToAreaUseCase: AssignSensorToAreaUseCase,
    private val readingsRepository: SensorsCurrentReadingsRepository,
    private val propertyRepository: PropertyJdbcRepository
) : AbstractIntegrationTest() {

    // /// getHome //////////////////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `getHome() returns area measurements populated from sensor readings in Redis`() {
        val area = anAreaDto()
        areasRepository.save(area)
        val sensorData = aSensorProviderData()
        val sensorId = randomGenerator.uuid()
        devicesRepository.create(sensorId, sensorData).shouldBeRight()
        assignSensorToAreaUseCase.execute(area.uuid, sensorId).shouldBeRight()
        val readings = aRandomThermoHygroData()
        readingsRepository.save(sensorData, readings).shouldBeRight()

        val json = given()
            .authenticated()
            .`when`()
            .get("/home")
            .then()
            .statusCode(200)
            .extract().body().jsonPath()

        withClue("one area returned") { json.getList<Any>("areas").size shouldBe 1 }
        withClue("area id and name") {
            json.getString("areas[0].id") shouldBe area.uuid.toString()
            json.getString("areas[0].name") shouldBe area.name
        }
        withClue("currentTemperature is resolved from cache") {
            json.getString("areas[0].measurements.heating.currentTemperature.type") shouldBe "success"
            json.get<Any?>("areas[0].measurements.heating.currentTemperature.value").shouldNotBeNull()
        }
        withClue("relative humidity is resolved from cache") {
            json.getString("areas[0].measurements.humidity.relative.type") shouldBe "success"
            json.get<Any?>("areas[0].measurements.humidity.relative.value").shouldNotBeNull()
        }
    }

    @Test fun `getHome() reflects heating properties stored in PostgreSQL`() {
        propertyRepository.upsert(HEATING_ENABLED_KEY, "true")
        propertyRepository.upsert(HEATING_STRATEGY_KEY, "COMFORT")

        val json = given()
            .authenticated()
            .`when`()
            .get("/home")
            .then()
            .statusCode(200)
            .extract().body().jsonPath()

        withClue("heatingActive is true") {
            json.getString("globalState.heatingActive.type") shouldBe "success"
            json.getBoolean("globalState.heatingActive.value") shouldBe true
        }
        withClue("strategy is COMFORT") {
            json.getString("globalState.strategy.type") shouldBe "success"
            json.getString("globalState.strategy.value") shouldBe "COMFORT"
        }
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // /// getHomeStream ////////////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `getHomeStream() sends an initial event and pushes a new one after a configuration change`() {
        val dataEvents = LinkedBlockingQueue<String>()
        val client = HttpClient.newHttpClient()
        val streamRequest = HttpRequest.newBuilder()
            .uri(URI.create("${RestAssured.baseURI}/home/stream"))
            .header("Authorization", "Bearer e88230d7d195479dabb1a6650343633f")
            .GET()
            .build()

        val response = client.send(streamRequest, BodyHandlers.ofInputStream())
        withClue("stream is a text/event-stream") {
            response.statusCode() shouldBe 200
            response.headers().firstValue("Content-Type").orElse("") shouldContain "text/event-stream"
        }

        val reader = response.body().bufferedReader()
        val readerThread = thread(isDaemon = true) {
            runCatching {
                reader.forEachLine { line -> if (line.startsWith("data:")) dataEvents.put(line) }
            }
        }

        try {
            withClue("initial event is received immediately") {
                dataEvents.poll(5, SECONDS).shouldNotBeNull() shouldContain "globalState"
            }

            // Configuration change → must trigger an immediate push
            given()
                .authenticated()
                .contentType(ContentType.JSON)
                .body(mapOf("value" to "true"))
                .`when`()
                .put("/properties/${aRandomUniqueString()}")
                .then()
                .statusCode(200)

            withClue("a new event is pushed after the configuration change") {
                dataEvents.poll(5, SECONDS).shouldNotBeNull() shouldContain "globalState"
            }
        } finally {
            readerThread.interrupt()
            reader.close()
        }
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
}
