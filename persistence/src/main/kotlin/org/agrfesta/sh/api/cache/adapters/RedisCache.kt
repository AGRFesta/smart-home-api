package org.agrfesta.sh.api.cache.adapters

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.agrfesta.sh.api.core.application.ports.outbounds.Cache
import org.agrfesta.sh.api.core.application.ports.outbounds.CacheError
import org.agrfesta.sh.api.core.application.ports.outbounds.CacheFailure
import org.agrfesta.sh.api.core.application.ports.outbounds.CacheOkResponse
import org.agrfesta.sh.api.core.application.ports.outbounds.CacheResponse
import org.agrfesta.sh.api.core.application.ports.outbounds.CachedValueNotFound
import org.slf4j.Logger
import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import kotlin.time.Duration
import kotlin.time.toJavaDuration

@Service
class RedisCache(private val template: RedisTemplate<String, String>) : Cache {

    override fun set(key: String, value: String, ttl: Duration?): CacheResponse =
        try {
            if (ttl != null) {
                template.opsForValue().set(key, value, ttl.toJavaDuration())
            } else {
                template.opsForValue().set(key, value)
            }
            CacheOkResponse
        } catch (ex: DataAccessException) {
            CacheError(ex)
        }

    override fun get(key: String): Either<CacheFailure, String> =
        try {
            template.opsForValue().get(key)
                ?.right()
                ?: CachedValueNotFound(key).left()
        } catch (ex: DataAccessException) {
            CacheError(ex).left()
        }

    override fun remove(key: String): Either<CacheFailure, Unit> =
        try {
            template.delete(key)
            Unit.right()
        } catch (ex: DataAccessException) {
            CacheError(ex).left()
        }
}

fun Either<CacheFailure, String>.onLeftLogOn(logger: Logger) = onLeft {
    when (it) {
        is CacheError -> logger.error("cache fetch failure", it.exception)
        is CachedValueNotFound -> logger.error("missing cache key: ${it.key}")
    }
}
