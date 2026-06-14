package org.agrfesta.sh.api.providers.switchbot.devices

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.mockk.every
import io.mockk.mockk
import org.agrfesta.sh.api.core.application.ports.outbounds.RandomGenerator
import org.agrfesta.sh.api.core.application.ports.outbounds.TimeProvider
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.core.domain.failures.DevicesProviderError
import org.agrfesta.sh.api.providers.switchbot.SwitchBotConfiguration
import org.agrfesta.sh.api.providers.switchbot.SwitchBotDevicesClient
import org.agrfesta.test.mothers.aRandomUniqueString
import org.agrfesta.test.mothers.anUrl
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class SwitchBotMeterTest {
    private val config = SwitchBotConfiguration(
        baseUrl = anUrl(),
        token = aRandomUniqueString(),
        secret = aRandomUniqueString()
    )
    private val mapper = ObjectMapper()
    private val timeProvider: TimeProvider = mockk()
    private val randomGenerator: RandomGenerator = mockk()

    @Test fun `inspect() returns the raw device status body verbatim`() {
        // Given
        val deviceProviderId = aRandomUniqueString()
        val rawBody = """{"statusCode":100,"body":{"deviceId":"$deviceProviderId","battery":88}}"""
        every { randomGenerator.string() } returns aRandomUniqueString()
        every { timeProvider.now() } returns Instant.parse("2024-08-10T12:34:56Z")
        val engine = MockEngine {
            respond(
                content = rawBody,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = SwitchBotDevicesClient(config, mapper, timeProvider, randomGenerator, engine)
        val sut = SwitchBotMeter(UUID.randomUUID(), Provider.SWITCHBOT, deviceProviderId, client)

        // When
        val result = sut.inspect().shouldBeRight()

        // Then
        result shouldBe rawBody
    }

    @Test fun `inspect() returns DevicesProviderError when the provider call fails`() {
        // Given
        val deviceProviderId = aRandomUniqueString()
        every { randomGenerator.string() } returns aRandomUniqueString()
        every { timeProvider.now() } returns Instant.parse("2024-08-10T12:34:56Z")
        val engine = MockEngine {
            respond(
                content = "boom",
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = SwitchBotDevicesClient(config, mapper, timeProvider, randomGenerator, engine)
        val sut = SwitchBotMeter(UUID.randomUUID(), Provider.SWITCHBOT, deviceProviderId, client)

        // When
        val result = sut.inspect().shouldBeLeft()

        // Then
        result.shouldBeInstanceOf<DevicesProviderError>()
    }
}
