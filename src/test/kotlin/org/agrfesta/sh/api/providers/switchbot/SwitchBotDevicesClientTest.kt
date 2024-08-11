package org.agrfesta.sh.api.providers.switchbot

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.agrfesta.sh.api.utils.RandomGenerator
import org.agrfesta.sh.api.utils.TimeService
import org.agrfesta.test.mothers.aJsonNode
import org.agrfesta.test.mothers.aRandomUniqueString
import org.agrfesta.test.mothers.anUrl
import org.junit.jupiter.api.Test
import java.time.Instant

class SwitchBotDevicesClientTest {
    private val config = SwitchBotConfiguration(
        baseUrl = anUrl(),
        token = aRandomUniqueString(),
        secret = aRandomUniqueString()
    )
    private val mapper = ObjectMapper()
    private val nonce = aRandomUniqueString()
    private val time = Instant.parse("2024-08-10T12:34:56Z")

    private val timeService: TimeService = mockk()
    private val randomGenerator: RandomGenerator = mockk()

    @Test fun `getDevices() test`() {
        runBlocking {
            val response = aJsonNode()

            every { randomGenerator.string() } returns nonce
            every { timeService.now() } returns time

            val engine = MockEngine { request ->
                request.method shouldBe HttpMethod.Get
                request.url.toString() shouldBe "${config.baseUrl}/devices"

                request.headers[HttpHeaders.Authorization] shouldBe config.token
                // request.headers["sign"] shouldBe ? TODO
                request.headers["nonce"] shouldBe nonce
                request.headers["t"] shouldBe "1723293296000"

                respond(
                    content = response.toString(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
            val client = SwitchBotDevicesClient(config, mapper, timeService, randomGenerator, engine)

            val devices = client.getDevices()

            devices shouldBe response
        }
    }

    @Test fun `getDeviceStatus() test`() {
        runBlocking {
            val deviceId = aRandomUniqueString()
            val response = aJsonNode()

            every { randomGenerator.string() } returns nonce
            every { timeService.now() } returns time

            val engine = MockEngine { request ->
                request.method shouldBe HttpMethod.Get
                request.url.toString() shouldBe "${config.baseUrl}/devices/$deviceId/status"

                request.headers[HttpHeaders.Authorization] shouldBe config.token
                // request.headers["sign"] shouldBe ? TODO
                request.headers["nonce"] shouldBe nonce
                request.headers["t"] shouldBe "1723293296000"

                respond(
                    content = response.toString(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
            val client = SwitchBotDevicesClient(config, mapper, timeService, randomGenerator, engine)

            val devices = client.getDeviceStatus(deviceId)

            devices shouldBe response
        }
    }

}
