package org.agrfesta.sh.api

import com.ninjasquad.springmockk.MockkBean
import com.ninjasquad.springmockk.SpykBean
import io.restassured.RestAssured
import org.agrfesta.sh.api.providers.switchbot.SwitchBotDevicesClient
import org.agrfesta.sh.api.core.application.ports.outbounds.RandomGenerator
import org.agrfesta.sh.api.core.application.ports.outbounds.TimeProvider
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestConstructor
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(SmartHomeTestConfiguration::class, TestContainersConfig::class)
@Testcontainers
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@ActiveProfiles("test")
@CleanSmartHomeDatabase
abstract class AbstractIntegrationTest {
    @SpykBean
    protected lateinit var randomGenerator: RandomGenerator
    @SpykBean
    protected lateinit var timeProvider: TimeProvider
    @MockkBean
    protected lateinit var switchBotDevicesClient: SwitchBotDevicesClient

    @Autowired
    protected lateinit var redisTemplate: RedisTemplate<String, Any>

    @LocalServerPort
    private val port: Int? = null

    @BeforeEach
    fun setUp() {
        RestAssured.baseURI = "http://localhost:$port"

        redisTemplate.connectionFactory?.connection?.serverCommands()?.flushDb()
    }

}