package org.agrfesta.sh.api

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import com.redis.testcontainers.RedisContainer

@TestConfiguration(proxyBeanMethods = false)
class TestContainersConfig {

    companion object {
        /* * We start the containers manually here in the companion object (static context)
         * rather than relying entirely on Spring's @ServiceConnection lifecycle management.
         * * WHY: Different test slices (e.g., @DataJpaTest vs @SpringBootTest) create
         * different Spring ApplicationContexts. If Spring manages the container lifecycle,
         * it will shut down and restart the containers every time the context changes.
         * Starting them statically implements the Singleton Container pattern: they boot
         * exactly once for the entire JVM/test suite, drastically reducing execution time.
         */
        val postgres = PostgreSQLContainer<Nothing>(
            DockerImageName.parse("timescale/timescaledb:latest-pg16")
                .asCompatibleSubstituteFor("postgres")
        ).apply { start() }

        val redis = RedisContainer(
            DockerImageName.parse("redis:7.0.10-alpine")
        ).apply { start() }
    }

    @Bean
    @ServiceConnection
    fun postgresContainer(): PostgreSQLContainer<*> = postgres

    @Bean
    @ServiceConnection
    fun redisContainer(): RedisContainer = redis
}
