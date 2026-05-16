package org.agrfesta.sh.api.controllers

import org.agrfesta.sh.api.core.application.ports.inbounds.GetPropertyUseCase
import org.agrfesta.sh.api.core.application.ports.inbounds.UpsertPropertyBatchUseCase
import org.agrfesta.sh.api.core.application.ports.inbounds.UpsertPropertyUseCase
import org.agrfesta.sh.api.core.domain.failures.DuplicatePropertyKeys
import org.agrfesta.sh.api.core.domain.failures.EmptyPropertyBatch
import org.agrfesta.sh.api.core.domain.failures.PropertyBatchTooLarge
import org.agrfesta.sh.api.core.domain.commons.PropertyUpsertEntry
import org.agrfesta.sh.api.core.domain.failures.PropertyNotFound
import org.agrfesta.sh.api.core.domain.failures.PropertyRepositoryError
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.ResponseEntity
import org.springframework.http.ResponseEntity.ok
import org.springframework.http.ResponseEntity.status
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * REST controller for managing persisted property entries.
 *
 * This controller exposes endpoints to create, update, and retrieve property entries.
 */
@RestController
@RequestMapping("/properties")
class PropertyController(
    private val upsertPropertyUseCase: UpsertPropertyUseCase,
    private val upsertPropertyBatchUseCase: UpsertPropertyBatchUseCase,
    private val getPropertyUseCase: GetPropertyUseCase
) {

    /**
     * Inserts or updates a single property entry.
     *
     * @param key The unique key for the property entry.
     * @param request The request body containing the value and an optional TTL.
     * @return A [ResponseEntity] containing a [MessageResponse] indicating success or failure.
     */
    @PutMapping("/{key}")
    fun putPropertyEntry(@PathVariable key: String, @RequestBody request: PropertyRequest): ResponseEntity<MessageResponse> =
        upsertPropertyUseCase.execute(key, request.value, request.ttl).fold(
            ifLeft = { failure ->
                when (failure) {
                    PropertyRepositoryError -> status(HttpStatus.INTERNAL_SERVER_ERROR).body(MessageResponse("Failed to upsert property entry"))
                }
            },
            ifRight = { ok(MessageResponse("Entry for key '$key' upserted successfully")) }
        )

    /**
     * Inserts or updates a batch of property entries.
     *
     * @param batch A collection of [PropertyUpsertEntry] objects to be persisted.
     * @return A [ResponseEntity] containing a [MessageResponse] indicating success or failure.
     */
    @PostMapping("/batch")
    fun postPropertyBatch(@RequestBody batch: List<PropertyUpsertEntry>): ResponseEntity<MessageResponse> =
        upsertPropertyBatchUseCase.execute(batch).fold(
            ifLeft = { failure ->
                when (failure) {
                    EmptyPropertyBatch -> status(HttpStatus.BAD_REQUEST).body(MessageResponse("There are no entries to persist"))
                    is PropertyBatchTooLarge -> status(HttpStatus.PAYLOAD_TOO_LARGE).body(MessageResponse("Batch size exceeds the maximum of ${failure.maxSize} entries"))
                    DuplicatePropertyKeys -> status(HttpStatus.BAD_REQUEST).body(MessageResponse("Batch contains duplicate keys"))
                    PropertyRepositoryError -> status(HttpStatus.INTERNAL_SERVER_ERROR).body(MessageResponse("Failed to persist batch"))
                }
            },
            ifRight = { ok(MessageResponse("Successfully persisted ${batch.size} entries")) }
        )

    /**
     * Retrieves a property entry by its key.
     *
     * @param key The unique key of the property entry to retrieve.
     * @return A [ResponseEntity] containing the property entry value if found, or an error message if not found or if
     * an error occurs.
     */
    @GetMapping("/{key}")
    fun getPropertyEntry(@PathVariable key: String): ResponseEntity<Any> = getPropertyUseCase.execute(key).fold(
        ifLeft = { failure ->
            when (failure) {
                PropertyNotFound -> status(NOT_FOUND).body(MessageResponse("Key '$key' is missing"))
                PropertyRepositoryError -> status(HttpStatus.INTERNAL_SERVER_ERROR).body(MessageResponse("Failed to get property entry"))
            }
        },
        ifRight = { entry -> ok(entry) }
    )

}

/**
 * Request body for creating or updating a property entry.
 *
 * @property value The value to be stored.
 * @property ttl The time-to-live in seconds. If `null`, the entry does not expire automatically based on TTL.
 */
data class PropertyRequest(
    val value: String,
    val ttl: Long? = null
)
