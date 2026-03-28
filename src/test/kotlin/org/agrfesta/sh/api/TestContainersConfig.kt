package org.agrfesta.sh.api

import com.redis.testcontainers.RedisContainer
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

@TestConfiguration(proxyBeanMethods = false)
class TestContainersConfig {

    @Bean
    @ServiceConnection
    fun postgresContainer(): PostgreSQLContainer<*> {
        return PostgreSQLContainer(
            DockerImageName.parse("timescale/timescaledb:latest-pg16")
                .asCompatibleSubstituteFor("postgres")
        )
    }

    @Bean
    @ServiceConnection
    fun redisContainer(): RedisContainer {
        return RedisContainer(DockerImageName.parse("redis:7.0.10-alpine"))
    }

}
