package org.agrfesta.sh.api.providers.switchbot

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.mockk.every
import io.mockk.mockk
import org.agrfesta.sh.api.core.application.ports.outbounds.RandomGenerator
import org.agrfesta.sh.api.core.application.ports.outbounds.TimeProvider
import org.agrfesta.sh.api.core.domain.failures.ProviderInspectionFailure
import org.agrfesta.sh.api.core.domain.failures.UnknownProbe
import org.agrfesta.sh.api.providers.createMockEngine
import org.agrfesta.sh.api.providers.netatmo.BehaviorRegistry
import org.agrfesta.sh.api.providers.netatmo.ResponseSpec
import org.junit.jupiter.api.Test
import java.time.Instant

class SwitchBotProviderInspectorTest {
    private val config = SwitchBotConfiguration(
        baseUrl = "https://api.switchbot.example.com",
        token = "sb-token",
        secret = "sb-secret",
    )
    private val timeProvider: TimeProvider = mockk {
        every { now() } returns Instant.parse("2026-07-17T10:00:00Z")
    }
    private val randomGenerator: RandomGenerator = mockk {
        every { string() } returns "fixed-nonce"
    }
    private val registry = BehaviorRegistry()
    private val engine = createMockEngine(registry)
    private val client = SwitchBotDevicesClient(config, ObjectMapper(), timeProvider, randomGenerator, engine)

    private val sut = SwitchBotProviderInspector(client)

    @Test
    fun `inspect() with an unknown probe returns UnknownProbe carrying the available probes`() {
        // Given
        val unknownProbe = "send-command"

        // When
        val result = sut.inspect(unknownProbe, emptyMap()).shouldBeLeft()

        // Then
        result shouldBe UnknownProbe(
            probe = unknownProbe,
            availableProbes = setOf("devices")
        )
    }

    @Test
    fun `devices probe returns the provider body verbatim`() {
        // Given: a body whose shape differs from what the typed client expects,
        // with irregular whitespace — a parse → re-serialize round trip would alter it
        val unexpectedShapeBody = """{"totally": "unexpected",   "shape": [1, 2]}"""
        registry.given(
            { it.method == HttpMethod.Get && it.url.encodedPath == "/devices" },
            ResponseSpec(unexpectedShapeBody),
        )

        // When
        val result = sut.inspect("devices", emptyMap()).shouldBeRight()

        // Then
        result shouldBe unexpectedShapeBody
    }

    @Test
    fun `non-JSON provider body is wrapped in a JSON envelope`() {
        // Given: the cloud answers 200 with a non-JSON body (e.g. an HTML maintenance page)
        val nonJsonBody = "<html>maintenance</html>"
        registry.given(
            { it.method == HttpMethod.Get && it.url.encodedPath == "/devices" },
            ResponseSpec(nonJsonBody),
        )

        // When
        val result = sut.inspect("devices", emptyMap()).shouldBeRight()

        // Then
        result shouldBe """{"nonJsonBody":"<html>maintenance</html>"}"""
    }

    @Test
    fun `a transport failure maps to ProviderInspectionFailure surfacing the cause`() {
        // Given: the cloud answers 500 to the probe's backing call
        registry.given(
            { it.method == HttpMethod.Get && it.url.encodedPath == "/devices" },
            ResponseSpec("""{"error":"boom"}""", status = HttpStatusCode.InternalServerError),
        )

        // When
        val result = sut.inspect("devices", emptyMap()).shouldBeLeft()

        // Then
        val failure = result.shouldBeInstanceOf<ProviderInspectionFailure>()
        withClue("the failure message should surface the transport error status") {
            failure.message shouldContain "500"
        }
    }
}
