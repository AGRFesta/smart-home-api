package org.agrfesta.sh.api.providers.switchbot.devices

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.withClue
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
import org.agrfesta.sh.api.core.domain.commons.Percentage
import org.agrfesta.sh.api.core.domain.commons.Temperature
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.core.domain.devices.ThermoHygroDataValue
import org.agrfesta.sh.api.core.domain.failures.DevicesProviderError
import org.agrfesta.sh.api.providers.switchbot.SwitchBotConfiguration
import org.agrfesta.sh.api.providers.switchbot.SwitchBotDevicesClient
import org.agrfesta.test.mothers.aRandomUniqueString
import org.agrfesta.test.mothers.anUrl
import org.junit.jupiter.api.BeforeEach
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

    @BeforeEach fun setUp() {
        every { randomGenerator.string() } returns aRandomUniqueString()
        every { timeProvider.now() } returns Instant.parse("2024-08-10T12:34:56Z")
    }

    @Test fun `inspect() returns the raw device status body verbatim`() {
        // Given
        val deviceProviderId = aRandomUniqueString()
        val rawBody = """{"statusCode":100,"body":{"deviceId":"$deviceProviderId","battery":88}}"""
        val sut = meterRespondingWith(deviceProviderId, rawBody)

        // When
        val result = sut.inspect().shouldBeRight()

        // Then
        result shouldBe rawBody
    }

    @Test fun `batteryLevel() returns the battery percentage from the device status body`() {
        // Given
        val deviceProviderId = aRandomUniqueString()
        val body = """{"statusCode":100,"body":{"deviceId":"$deviceProviderId","battery":77}}"""
        val sut = meterRespondingWith(deviceProviderId, body)

        // When
        val result = sut.batteryLevel().shouldBeRight()

        // Then
        result shouldBe 77
    }

    @Test fun `batteryLevel() returns DevicesProviderError when the battery node is missing`() {
        // Given
        val deviceProviderId = aRandomUniqueString()
        val body = """{"statusCode":100,"body":{"temperature":21.5,"humidity":48}}"""
        val sut = meterRespondingWith(deviceProviderId, body)

        // When
        val result = sut.batteryLevel()

        // Then
        withClue("a missing battery node must surface as a provider error, not be coerced to 0, but was: $result") {
            result.isLeft() shouldBe true
        }
        result.shouldBeLeft().shouldBeInstanceOf<DevicesProviderError>()
    }

    @Test fun `fetchReadings() maps the device status to a ThermoHygroDataValue`() {
        // Given
        val deviceProviderId = aRandomUniqueString()
        val body = """{"statusCode":100,"body":{"temperature":21.5,"humidity":48,"battery":77}}"""
        val sut = meterRespondingWith(deviceProviderId, body)

        // When
        val readings = sut.fetchReadings().shouldBeRight().shouldBeInstanceOf<ThermoHygroDataValue>()

        // Then
        readings.thermoHygroData.temperature shouldBe Temperature.of("21.5")
        readings.thermoHygroData.relativeHumidity shouldBe Percentage.ofHundreds(48)
    }

    @Test fun `readings and battery share a single device status call per driver instance`() {
        // Given
        val deviceProviderId = aRandomUniqueString()
        var calls = 0
        val engine = MockEngine {
            calls++
            respond(
                content = """{"statusCode":100,"body":{"temperature":21.5,"humidity":48,"battery":77}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = SwitchBotDevicesClient(config, mapper, timeProvider, randomGenerator, engine)
        val sut = SwitchBotMeter(UUID.randomUUID(), Provider.SWITCHBOT, deviceProviderId, client)

        // When
        sut.fetchReadings().shouldBeRight()
        sut.batteryLevel().shouldBeRight()

        // Then
        withClue("readings + battery should share one status fetch, but hit the provider $calls times") {
            calls shouldBe 1
        }
    }

    @Test fun `batteryLevel() returns DevicesProviderError when the provider call fails`() {
        // Given
        val sut = meterRespondingWith(aRandomUniqueString(), "boom", HttpStatusCode.InternalServerError)

        // When
        val result = sut.batteryLevel().shouldBeLeft()

        // Then
        result.shouldBeInstanceOf<DevicesProviderError>()
    }

    @Test fun `inspect() returns DevicesProviderError when the provider call fails`() {
        // Given
        val sut = meterRespondingWith(aRandomUniqueString(), "boom", HttpStatusCode.InternalServerError)

        // When
        val result = sut.inspect().shouldBeLeft()

        // Then
        result.shouldBeInstanceOf<DevicesProviderError>()
    }

    private fun meterRespondingWith(
        deviceProviderId: String,
        content: String,
        status: HttpStatusCode = HttpStatusCode.OK
    ): SwitchBotMeter {
        val engine = MockEngine {
            respond(
                content = content,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = SwitchBotDevicesClient(config, mapper, timeProvider, randomGenerator, engine)
        return SwitchBotMeter(UUID.randomUUID(), Provider.SWITCHBOT, deviceProviderId, client)
    }
}
