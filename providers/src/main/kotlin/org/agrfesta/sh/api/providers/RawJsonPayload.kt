package org.agrfesta.sh.api.providers

import com.fasterxml.jackson.databind.ObjectMapper

private val PAYLOAD_MAPPER = ObjectMapper()

/**
 * The diagnostics endpoint promises `application/json`: a body the cloud sent as non-JSON (e.g. an
 * HTML maintenance page) is wrapped in a `{"nonJsonBody": "<body>"}` envelope instead of being
 * dropped — on a diagnostic surface the weird body is exactly what the caller wants to see.
 * This is the shared implementation of the [org.agrfesta.sh.api.core.application.ports.outbounds.diagnostics.InspectableProvider]
 * body contract.
 */
internal fun String.asJsonPayload(): String =
    runCatching { PAYLOAD_MAPPER.readTree(this) }.fold(
        { this },
        { PAYLOAD_MAPPER.createObjectNode().put("nonJsonBody", this).toString() },
    )
