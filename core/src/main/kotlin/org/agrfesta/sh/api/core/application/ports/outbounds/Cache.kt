package org.agrfesta.sh.api.core.application.ports.outbounds

import arrow.core.Either
import kotlin.time.Duration

interface Cache {
    fun set(key: String, value: String, ttl: Duration? = null): CacheResponse
    fun get(key: String): Either<CacheFailure, String>
    fun remove(key: String): Either<CacheFailure, Unit>
}

sealed interface CacheResponse
data object CacheOkResponse : CacheResponse
sealed interface CacheFailure
class CacheError(val exception: Exception) : CacheFailure, CacheResponse
data class CachedValueNotFound(val key: String) : CacheFailure
