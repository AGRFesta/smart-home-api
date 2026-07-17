package org.agrfesta.sh.api

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.restassured.RestAssured
import org.junit.jupiter.api.Test

class SecurityErrorDispatchIntegrationTest : AbstractIntegrationTest() {

    @Test fun `an unhandled controller exception surfaces as 500, not 403`() {
        // Given: an authenticated call to an endpoint whose handler throws — the container
        // performs an ERROR dispatch to /error, which security must not block

        // When
        val response = RestAssured.given()
            .header("Authorization", "Bearer e88230d7d195479dabb1a6650343633f")
            .get("/test-support/boom")

        // Then
        withClue("the container ERROR dispatch to /error must not be masked as 403") {
            response.statusCode shouldBe 500
        }
        withClue("the standard Spring Boot error body must reach the client") {
            response.contentType shouldContain "application/json"
            response.jsonPath().getInt("status") shouldBe 500
        }
    }
}
