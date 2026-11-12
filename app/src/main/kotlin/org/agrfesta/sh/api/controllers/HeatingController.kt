package org.agrfesta.sh.api.controllers

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.agrfesta.sh.api.core.application.ports.inbounds.heating.SetHeatingEnabledUseCase
import org.agrfesta.sh.api.core.application.ports.inbounds.heating.SetHeatingStrategyUseCase
import org.agrfesta.sh.api.core.domain.failures.PropertyRepositoryError
import org.agrfesta.sh.api.core.domain.heating.SharedHeatingStrategy
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.ResponseEntity.badRequest
import org.springframework.http.ResponseEntity.internalServerError
import org.springframework.http.ResponseEntity.ok
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/heating")
class HeatingController(
    private val setHeatingEnabledUseCase: SetHeatingEnabledUseCase,
    private val setHeatingStrategyUseCase: SetHeatingStrategyUseCase,
    private val objectMapper: ObjectMapper
) {

    /**
     * Switches heating on or off. The body is read as raw JSON so that only a JSON boolean is accepted:
     * the shared [ObjectMapper] would convert `"true"` and `1` into a boolean.
     */
    @PutMapping("/enabled", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun setHeatingEnabled(@RequestBody(required = false) body: String?): ResponseEntity<Any> {
        val enabled = body?.let { parseEnabled(it) }
            ?: return badRequest()
                .body(MessageResponse("The body must be a JSON object with a boolean 'enabled' field"))
        return setHeatingEnabledUseCase.execute(enabled).fold(
            ifLeft = { failure ->
                when (failure) {
                    PropertyRepositoryError -> internalServerError()
                        .body(MessageResponse("Unable to save the heating.enabled flag"))
                }
            },
            ifRight = { ok(HeatingEnabledResponse(enabled = enabled)) }
        )
    }

    private fun parseEnabled(body: String): Boolean? =
        readJsonObject(body)?.get("enabled")?.takeIf { it.isBoolean }?.booleanValue()

    /**
     * Sets the shared heating strategy. The name is matched ignoring case and surrounding spaces,
     * and only a JSON string is accepted.
     */
    @PutMapping("/strategy", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun setHeatingStrategy(@RequestBody(required = false) body: String?): ResponseEntity<Any> {
        val strategy = body?.let { parseStrategy(it) }
            ?: return badRequest().body(
                MessageResponse(
                    "The body must be a JSON object with a 'strategy' field: " +
                        "one of ${SharedHeatingStrategy.entries.joinToString()}"
                )
            )
        return setHeatingStrategyUseCase.execute(strategy).fold(
            ifLeft = { failure ->
                when (failure) {
                    PropertyRepositoryError -> internalServerError()
                        .body(MessageResponse("Unable to save the heating strategy"))
                }
            },
            ifRight = { ok(HeatingStrategyResponse(strategy = strategy)) }
        )
    }

    private fun parseStrategy(body: String): SharedHeatingStrategy? =
        readJsonObject(body)?.get("strategy")?.takeIf { it.isTextual }?.textValue()
            ?.let { SharedHeatingStrategy.fromName(it) }

    private fun readJsonObject(body: String): JsonNode? = try {
        objectMapper.readTree(body)?.takeIf { it.isObject }
    } catch (_: JsonProcessingException) {
        null
    }
}

data class HeatingEnabledResponse(val enabled: Boolean)

data class HeatingStrategyResponse(val strategy: SharedHeatingStrategy)
