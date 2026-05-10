package org.agrfesta.sh.api.controllers

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import org.agrfesta.sh.api.core.domain.failures.PropertyNotFound
import org.agrfesta.sh.api.core.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.core.domain.commons.PropertyUpsertEntry
import org.agrfesta.sh.api.core.application.ports.outbounds.settings.PropertyRepository
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
    private val propertyRepository: PropertyRepository
) {

    companion object {
        const val MAX_BATCH_SIZE = 1000
    }

    /**
     * Inserts or updates a single property entry.
     *
     * @param key The unique key for the property entry.
     * @param request The request body containing the value and an optional TTL.
     * @return A [ResponseEntity] containing a [MessageResponse] indicating success or failure.
     */
    @PutMapping("/{key}")
    fun putPropertyEntry(@PathVariable key: String, @RequestBody request: PropertyRequest): ResponseEntity<MessageResponse> =
        propertyRepository.upsert(key, request.value, request.ttl).fold(
            ifLeft = { _ -> status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(MessageResponse("Failed to upsert property entry")) },
            ifRight = { ok(MessageResponse("Entry for key '$key' upserted successfully")) }
        )

    /**
     * Inserts or updates a batch of property entries.
     *
     * @param batch A collection of [PropertyUpsertEntry] objects to be persisted. The maximum batch size is [MAX_BATCH_SIZE].
     * @return A [ResponseEntity] containing a [MessageResponse] indicating success or failure.
     */
    @PostMapping("/batch")
    fun postPropertyBatch(@RequestBody batch: List<PropertyUpsertEntry>): ResponseEntity<MessageResponse> =
        validateBatch(batch)
            .flatMap { validBatch ->
                propertyRepository.upsertBatch(validBatch).mapLeft {
                    status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(MessageResponse("Failed to persist batch"))
                }
            }
            .fold(
                ifLeft = { it },
                ifRight = { ok(MessageResponse("Successfully persisted ${batch.size} entries")) }
            )

    private fun validateBatch(
        batch: List<PropertyUpsertEntry>
    ): Either<ResponseEntity<MessageResponse>, List<PropertyUpsertEntry>> = when {
        batch.isEmpty() -> status(HttpStatus.BAD_REQUEST)
            .body(MessageResponse("There are no entries to persist")).left()
        batch.size > MAX_BATCH_SIZE -> status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(MessageResponse("Batch size exceeds the maximum of $MAX_BATCH_SIZE entries")).left()
        hasDuplicateKeys(batch) -> status(HttpStatus.BAD_REQUEST)
            .body(MessageResponse("Batch contains duplicate keys")).left()
        else -> batch.right()
    }

    /**
     * Retrieves a property entry by its key.
     *
     * @param key The unique key of the property entry to retrieve.
     * @return A [ResponseEntity] containing the property entry value if found, or an error message if not found or if
     * an error occurs.
     */
    @GetMapping("/{key}")
    fun getPropertyEntry(@PathVariable key: String): ResponseEntity<Any> = propertyRepository.getEntry(key).fold(
        ifLeft = { failure ->
            when (failure) {
                PropertyNotFound -> status(NOT_FOUND).body(MessageResponse("Key '$key' is missing"))
                is PersistenceFailure -> status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(MessageResponse("Failed to get property entry"))
            }
        },
        ifRight = { entry -> ok(entry) }
    )

    private fun hasDuplicateKeys(batch: List<PropertyUpsertEntry>): Boolean {
        return batch.map { it.key }.toSet().size < batch.size
    }

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
