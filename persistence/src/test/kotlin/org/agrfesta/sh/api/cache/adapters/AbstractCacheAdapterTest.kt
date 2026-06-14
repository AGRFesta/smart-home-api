package org.agrfesta.sh.api.cache.adapters

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.agrfesta.sh.api.TestContainersConfig
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.StringRedisTemplate

@DataRedisTest
@Import(
    TestContainersConfig::class,
    AbstractCacheAdapterTest.JacksonTestConfig::class,
    RedisCache::class,
    SensorsCurrentReadingsCacheAdapter::class,
    DeviceBatteryCacheAdapter::class
)
abstract class AbstractCacheAdapterTest {

    @TestConfiguration
    class JacksonTestConfig {
        @Bean fun objectMapper(): ObjectMapper = jacksonObjectMapper().apply {
            configure(SerializationFeature.WRITE_BIGDECIMAL_AS_PLAIN, true)
        }
    }

    @Autowired
    private lateinit var stringRedisTemplate: StringRedisTemplate

    @BeforeEach
    fun cleanRedis() {
        stringRedisTemplate.connectionFactory?.connection?.serverCommands()?.flushDb()
    }
}
