package org.agrfesta.sh.api

import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.restassured.RestAssured
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.agrfesta.sh.api.controllers.authenticated
import org.agrfesta.sh.api.core.application.usecases.heating.EvaluateHeatingStateService.Companion.HEATING_ENABLED_KEY
import org.agrfesta.sh.api.persistence.jdbc.repositories.PropertyJdbcRepository
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.concurrent.thread

class HeatingIntegrationTest(
    private val propertyRepository: PropertyJdbcRepository
) : AbstractIntegrationTest() {

    // /// setHeatingEnabled ////////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `setHeatingEnabled() enables heating, pushes a stream update and is reflected by GET home`() {
        // Given
        propertyRepository.upsert(HEATING_ENABLED_KEY, "false")

        // When / Then
        assertSwitchIsSavedPushedAndReflected(enabled = true)
    }

    @Test fun `setHeatingEnabled() disables heating, pushes a stream update and is reflected by GET home`() {
        // Given
        propertyRepository.upsert(HEATING_ENABLED_KEY, "true")

        // When / Then
        assertSwitchIsSavedPushedAndReflected(enabled = false)
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private fun assertSwitchIsSavedPushedAndReflected(enabled: Boolean) {
        val dataEvents = LinkedBlockingQueue<String>()
        val streamRequest = HttpRequest.newBuilder()
            .uri(URI.create("${RestAssured.baseURI}/home/stream"))
            .header("Authorization", "Bearer e88230d7d195479dabb1a6650343633f")
            .GET()
            .build()
        val reader = HttpClient.newHttpClient().send(streamRequest, BodyHandlers.ofInputStream())
            .body().bufferedReader()
        val readerThread = thread(isDaemon = true) {
            runCatching { reader.forEachLine { line -> if (line.startsWith("data:")) dataEvents.put(line) } }
        }

        try {
            dataEvents.poll(5, SECONDS).shouldNotBeNull()

            given()
                .authenticated()
                .contentType(ContentType.JSON)
                .body("""{ "enabled": $enabled }""")
                .`when`()
                .put("/heating/enabled")
                .then()
                .statusCode(200)

            withClue("a stream client should receive an update after the flag is saved") {
                dataEvents.poll(5, SECONDS).shouldNotBeNull() shouldContain "globalState"
            }
        } finally {
            readerThread.interrupt()
            reader.close()
        }

        withClue("heating.enabled should be saved in PostgreSQL") {
            propertyRepository.findEntry(HEATING_ENABLED_KEY).shouldNotBeNull().value shouldBe enabled.toString()
        }
        val home = given()
            .authenticated()
            .`when`()
            .get("/home")
            .then()
            .statusCode(200)
            .extract().body().jsonPath()
        withClue("GET /home should report the saved flag as heatingActive") {
            home.getBoolean("globalState.heatingActive.value") shouldBe enabled
        }
    }
}
