package org.agrfesta.sh.api.utils

import org.springframework.stereotype.Service

interface Cache {
    fun set(key: String, value: String): CacheResponse
}

sealed interface CacheResponse
data object CacheOkResponse: CacheResponse
class CacheError(val reason: Throwable): CacheResponse

@Service
class DoNothingCache: Cache {
    override fun set(key: String, value: String): CacheResponse {
        return CacheOkResponse
    }
}