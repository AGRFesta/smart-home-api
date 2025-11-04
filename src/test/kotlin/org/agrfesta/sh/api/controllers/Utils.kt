package org.agrfesta.sh.api.controllers

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.restassured.specification.RequestSpecification
import org.agrfesta.sh.api.providers.netatmo.BehaviorRegistry
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder

fun MockHttpServletRequestBuilder.authenticated() =
    header("Authorization", "Bearer e88230d7d195479dabb1a6650343633f")

fun RequestSpecification.authenticated() =
    header("Authorization", "Bearer e88230d7d195479dabb1a6650343633f")

fun createMockEngine(registry: BehaviorRegistry) = MockEngine { request ->
    val spec = registry.matchAndPop(request)
        ?: error("Unexpected request: ${request.method} ${request.url}")
    respond(
        content = spec.content,
        status = spec.status,
        headers = spec.headers
    )
}
