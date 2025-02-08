package org.agrfesta.sh.api.controllers

import org.agrfesta.sh.api.persistence.jdbc.repositories.CacheJdbcRepository
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
    private val cacheJdbcRepository: CacheJdbcRepository
) {

    @PutMapping("/{key}")
    fun putCacheEntry(@PathVariable key: String, @RequestBody request: CacheRequest): ResponseEntity<MessageResponse> {
        cacheJdbcRepository.upsert(key, request.value, request.ttl)
        return ok(MessageResponse("Inserted key '$key' with value '${request.value}'"))
    }

    @GetMapping("/{key}")
    fun getCacheEntry(@PathVariable key: String): ResponseEntity<Any> {
        return cacheJdbcRepository.findEntry(key)?.let { ok(it) }
            ?: status(NOT_FOUND).body(MessageResponse("Key '$key' is missing"))
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
