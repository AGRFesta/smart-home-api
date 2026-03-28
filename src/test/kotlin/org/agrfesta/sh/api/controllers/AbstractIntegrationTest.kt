package org.agrfesta.sh.api.controllers

import com.ninjasquad.springmockk.MockkBean
import com.ninjasquad.springmockk.SpykBean
import com.redis.testcontainers.RedisContainer
import io.restassured.RestAssured
import org.agrfesta.sh.api.SmartHomeTestConfiguration
import org.agrfesta.sh.api.providers.switchbot.SwitchBotDevicesClient
import org.agrfesta.sh.api.utils.RandomGenerator
import org.agrfesta.sh.api.utils.TimeService
import org.junit.jupiter.api.BeforeEach
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestConstructor
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(SmartHomeTestConfiguration::class)
@Testcontainers
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@ActiveProfiles("test")
abstract class AbstractIntegrationTest {
    @SpykBean protected lateinit var randomGenerator: RandomGenerator
    @SpykBean protected lateinit var timeService: TimeService
    @MockkBean protected lateinit var switchBotDevicesClient: SwitchBotDevicesClient

    companion object {
        fun createPostgresContainer(): PostgreSQLContainer<*> =
            DockerImageName.parse("timescale/timescaledb:latest-pg16")
                .asCompatibleSubstituteFor("postgres")
                .let { PostgreSQLContainer(it) }

        fun createRedisContainer() = RedisContainer(DockerImageName.parse("redis:7.0.10-alpine"))
    }

    @LocalServerPort private val port: Int? = null

    @BeforeEach
    fun setUp() {
        RestAssured.baseURI = "http://localhost:$port"
    }

}
