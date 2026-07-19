package org.agrfesta.sh.api

import arrow.core.right
import com.fasterxml.jackson.databind.JsonNode
import com.ninjasquad.springmockk.MockkBean
import com.ninjasquad.springmockk.SpykBean
import io.mockk.coEvery
import io.restassured.RestAssured
import org.agrfesta.sh.api.core.application.ports.outbounds.RandomGenerator
import org.agrfesta.sh.api.core.application.ports.outbounds.TimeProvider
import org.agrfesta.sh.api.providers.hon.HonApiClient
import org.agrfesta.sh.api.providers.switchbot.SwitchBotDevicesClient
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

    // hOn is enabled in the test profile so its Spring wiring (factory, appliance store, provider)
    // is exercised end-to-end; only the transport is mocked, mirroring switchBotDevicesClient.
    // relaxUnitFun lets the container call the AutoCloseable close() on shutdown without a stub.
    @MockkBean(relaxUnitFun = true)
    protected lateinit var honApiClient: HonApiClient

    @Autowired
    protected lateinit var redisTemplate: RedisTemplate<String, Any>

    @LocalServerPort
    private val port: Int? = null

    @BeforeEach
    fun setUp() {
        RestAssured.baseURI = "http://localhost:$port"

        redisTemplate.connectionFactory?.connection?.serverCommands()?.flushDb()

        // Default: hOn contributes no devices to synchronization, so existing sync assertions
        // are unaffected. Tests that drive an hOn AC override the read/write calls they need.
        coEvery { honApiClient.loadAppliances() } returns emptyList<JsonNode>().right()
    }
}
