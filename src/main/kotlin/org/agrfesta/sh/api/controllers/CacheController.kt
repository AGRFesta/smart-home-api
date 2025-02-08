package org.agrfesta.sh.api.controllers

import org.agrfesta.sh.api.persistence.jdbc.repositories.CacheJdbcRepository
import org.springframework.http.ResponseEntity
import org.springframework.http.ResponseEntity.ok
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

}

/**
 * @property value Cache key value
 * @property ttl Time-to-live in seconds
 */
data class CacheRequest(
    val value: String,
    val ttl: Long
)
