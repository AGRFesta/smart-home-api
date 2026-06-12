package org.agrfesta.sh.api

import io.restassured.RestAssured.given
import org.agrfesta.sh.api.controllers.authenticated
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Test

class HealthCheckIntegrationTest : AbstractIntegrationTest() {

    @Test fun `health is publicly reachable and reports UP without API key`() {
        // When / Then
        given()
            .`when`()
            .get("/actuator/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"))
    }

    @Test fun `health exposes component details to authenticated callers`() {
        // When / Then
        given()
            .authenticated()
            .`when`()
            .get("/actuator/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"))
            .body("components.db.status", equalTo("UP"))
            .body("components.redis.status", equalTo("UP"))
            .body("components.diskSpace.status", equalTo("UP"))
    }

    @Test fun `health hides component details from anonymous callers`() {
        // When / Then
        given()
            .`when`()
            .get("/actuator/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"))
            .body("components", nullValue())
    }

    @Test fun `readiness probe is publicly reachable and reports UP without API key`() {
        // When / Then
        given()
            .`when`()
            .get("/actuator/health/readiness")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"))
    }

    @Test fun `readiness includes db but not redis as a critical dependency`() {
        // When / Then
        given()
            .authenticated()
            .`when`()
            .get("/actuator/health/readiness")
            .then()
            .statusCode(200)
            .body("components.db.status", equalTo("UP"))
            .body("components.redis", nullValue())
    }

    @Test fun `info is reachable by authenticated callers`() {
        // When / Then
        given()
            .authenticated()
            .`when`()
            .get("/actuator/info")
            .then()
            .statusCode(200)
    }

    @Test fun `info exposes the application build version`() {
        // When / Then
        given()
            .authenticated()
            .`when`()
            .get("/actuator/info")
            .then()
            .statusCode(200)
            .body("build.version", notNullValue())
    }

    @Test fun `info is not reachable without API key`() {
        // When / Then
        given()
            .`when`()
            .get("/actuator/info")
            .then()
            .statusCode(401)
    }

    @Test fun `non-exposed actuator endpoints do not serve their payload even when authenticated`() {
        // When / Then — env is not in exposure.include, so it must never return its data (200)
        given()
            .authenticated()
            .`when`()
            .get("/actuator/env")
            .then()
            .statusCode(not(200))
    }
}
