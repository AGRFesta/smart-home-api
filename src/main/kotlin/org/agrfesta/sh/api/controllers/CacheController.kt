package org.agrfesta.sh.api.controllers

import arrow.core.Either
import org.agrfesta.sh.api.domain.failures.PersistedCacheEntryNotFound
import org.agrfesta.sh.api.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.services.PersistedCacheService
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.ResponseEntity
import org.springframework.http.ResponseEntity.ok
import org.springframework.http.ResponseEntity.status
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/cache")
class CacheController(
    private val persistedCacheService: PersistedCacheService
) {

    @PutMapping("/{key}")
    fun putCacheEntry(@PathVariable key: String, @RequestBody request: CacheRequest): ResponseEntity<MessageResponse> {
        persistedCacheService.upsert(key, request.value, request.ttl)
        return ok(MessageResponse("Inserted key '$key' with value '${request.value}'"))
    }

    @GetMapping("/{key}")
    fun getCacheEntry(@PathVariable key: String): ResponseEntity<Any> {
        val result = persistedCacheService.getEntry(key)
        return when (result) {
            is Either.Left -> when(result.value) {
                PersistedCacheEntryNotFound -> status(NOT_FOUND).body(MessageResponse("Key '$key' is missing"))
                is PersistenceFailure -> TODO()
            }
            is Either.Right -> ok(result.value)
        }
    }

}

/**
 * @property value Cache key value.
 * @property ttl Time-to-live in seconds, if null, entry will never expire.
 */
data class CacheRequest(
    val value: String,
    val ttl: Long? = null
)
