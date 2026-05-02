package org.agrfesta.sh.api.cache.adapters

import org.agrfesta.sh.api.TestContainersConfig
import org.agrfesta.sh.api.configuration.JacksonConfiguration
import org.agrfesta.sh.api.cache.adapters.RedisCache
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.StringRedisTemplate

@DataRedisTest
@Import(
    TestContainersConfig::class,
    JacksonConfiguration::class,
    RedisCache::class,
    SensorsCurrentReadingsCacheAdapter::class
)
abstract class AbstractCacheAdapterTest {

    @Autowired
    private lateinit var stringRedisTemplate: StringRedisTemplate

    @BeforeEach
    fun cleanRedis() {
        stringRedisTemplate.connectionFactory?.connection?.serverCommands()?.flushDb()
    }

}
