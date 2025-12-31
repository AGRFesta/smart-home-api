package org.agrfesta.sh.api.controllers

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import org.agrfesta.sh.api.domain.failures.PersistedCacheEntryNotFound
import org.agrfesta.sh.api.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.persistence.CacheEntryDto
import org.agrfesta.sh.api.services.PersistedCacheService
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
 * REST controller for managing persisted cache entries.
 *
 * This controller exposes endpoints to create, update, and retrieve cache entries.
 *
 * @property persistedCacheService The service used to manage persisted cache entries.
 */
@RestController
@RequestMapping("/cache")
class CacheController(
    private val persistedCacheService: PersistedCacheService
) {

    companion object {
        const val MAX_BATCH_SIZE = 1000
    }

    /**
     * Inserts or updates a single cache entry.
     *
     * @param key The unique key for the cache entry.
     * @param request The request body containing the value and an optional TTL.
     * @return A [ResponseEntity] containing a [MessageResponse] indicating success or failure.
     */
    @PutMapping("/{key}")
    fun putCacheEntry(@PathVariable key: String, @RequestBody request: CacheRequest): ResponseEntity<MessageResponse> =
        persistedCacheService.upsert(key, request.value, request.ttl).fold(
            ifLeft = { _ -> status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(MessageResponse("Failed to upsert cache entry")) },
            ifRight = { ok(MessageResponse("Entry for key '$key' upserted successfully")) }
        )

    /**
     * Inserts or updates a batch of cache entries.
     *
     * @param batch A collection of [CacheEntryDto] objects to be persisted. The maximum batch size is [MAX_BATCH_SIZE].
     * @return A [ResponseEntity] containing a [MessageResponse] indicating success or failure.
     */
    @PostMapping("/batch")
    fun postCacheBatch(@RequestBody batch: List<CacheEntryDto>): ResponseEntity<MessageResponse> =
        validateBatch(batch)
            .flatMap { validBatch ->
                persistedCacheService.upsertBatch(validBatch).mapLeft {
                    status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(MessageResponse("Failed to persist batch"))
                }
            }
            .fold(
                ifLeft = { it },
                ifRight = { ok(MessageResponse("Successfully persisted ${batch.size} entries")) }
            )

    private fun validateBatch(
        batch: List<CacheEntryDto>
    ): Either<ResponseEntity<MessageResponse>, List<CacheEntryDto>> = when {
        batch.isEmpty() -> status(HttpStatus.BAD_REQUEST)
            .body(MessageResponse("There are no entries to persist")).left()
        batch.size > MAX_BATCH_SIZE -> status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(MessageResponse("Batch size exceeds the maximum of $MAX_BATCH_SIZE entries")).left()
        hasDuplicateKeys(batch) -> status(HttpStatus.BAD_REQUEST)
            .body(MessageResponse("Batch contains duplicate keys")).left()
        else -> batch.right()
    }

    /**
     * Retrieves a cache entry by its key.
     *
     * @param key The unique key of the cache entry to retrieve.
     * @return A [ResponseEntity] containing the cache entry value if found, or an error message if not found or if an
     * error occurs.
     */
    @GetMapping("/{key}")
    fun getCacheEntry(@PathVariable key: String): ResponseEntity<Any> = persistedCacheService.getEntry(key).fold(
        ifLeft = { failure ->
            when (failure) {
                PersistedCacheEntryNotFound -> status(NOT_FOUND).body(MessageResponse("Key '$key' is missing"))
                is PersistenceFailure -> status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(MessageResponse("Failed to get cache entry"))
            }
        },
        ifRight = { entry -> ok(entry) }
    )

    private fun hasDuplicateKeys(batch: List<CacheEntryDto>): Boolean {
        return batch.map { it.key }.toSet().size < batch.size
    }

}

/**
 * Request body for creating or updating a cache entry.
 *
 * @property value The value to be cached.
 * @property ttl The time-to-live in seconds. If `null`, the entry does not expire automatically based on TTL.
 */
data class CacheRequest(
    val value: String,
    val ttl: Long? = null
)
