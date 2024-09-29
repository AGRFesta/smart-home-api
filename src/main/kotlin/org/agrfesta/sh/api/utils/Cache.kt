package org.agrfesta.sh.api.utils

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.slf4j.Logger
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service

interface Cache {
    fun set(key: String, value: String): CacheResponse
    fun get(key: String): Either<CacheFailure, String>
}

sealed interface CacheResponse
data object CacheOkResponse: CacheResponse
sealed interface CacheFailure
class CacheError(val reason: Throwable): CacheFailure, CacheResponse
data class CachedValueNotFound(val key: String): CacheFailure

@Service
class RedisCache(private val template: RedisTemplate<String, String>): Cache {

    override fun set(key: String, value: String): CacheResponse {
        template.opsForValue().set(key, value)
        return CacheOkResponse
    }

    override fun get(key: String): Either<CacheFailure, String> = template.opsForValue().get(key)
            ?.right()
            ?: CachedValueNotFound(key).left()

}

fun Either<CacheFailure, String>.onLeftLogOn(logger: Logger) = onLeft {
    when(it) {
        is CacheError -> logger.error("cache fetch failure", it.reason)
        is CachedValueNotFound -> logger.error("missing cache key: ${it.key}")
    }
}
