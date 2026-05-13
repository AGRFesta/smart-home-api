package org.agrfesta.sh.api.providers

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import org.agrfesta.sh.api.providers.netatmo.BehaviorRegistry

fun createMockEngine(registry: BehaviorRegistry) = MockEngine { request ->
    val spec = registry.matchAndPop(request)
        ?: error("Unexpected request: ${request.method} ${request.url}")
    respond(
        content = spec.content,
        status = spec.status,
        headers = spec.headers
    )
}
