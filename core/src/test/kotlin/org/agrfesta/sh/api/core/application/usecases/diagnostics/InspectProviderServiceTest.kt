package org.agrfesta.sh.api.core.application.usecases.diagnostics

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import org.agrfesta.sh.api.core.application.ports.outbounds.diagnostics.InspectableProvider
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.core.domain.failures.InspectProviderFailure
import org.agrfesta.sh.api.core.domain.failures.ProviderDiagnosticsNotSupported
import org.agrfesta.sh.api.core.domain.failures.UnknownProbe
import org.junit.jupiter.api.Test

/** Echoes provider, probe and params in the returned body: delegation is asserted on state. */
private class FakeInspectableProvider(override val provider: Provider) : InspectableProvider {
    override val probes: Set<String> = setOf("echo")

    override fun inspect(probe: String, params: Map<String, String>): Either<InspectProviderFailure, String> =
        """{"provider":"${provider.name}","probe":"$probe","params":"$params"}""".right()
}

class InspectProviderServiceTest {

    @Test
    fun `execute() returns ProviderDiagnosticsNotSupported when no inspectable provider matches`() {
        // Given
        val sut = InspectProviderService(inspectableProviders = emptySet())

        // When
        val result = sut.execute(Provider.SWITCHBOT, "devices", emptyMap()).shouldBeLeft()

        // Then
        result shouldBe ProviderDiagnosticsNotSupported
    }

    @Test
    fun `execute() delegates probe and params to the matching provider and returns the raw body`() {
        // Given
        val sut = InspectProviderService(
            inspectableProviders = setOf(
                FakeInspectableProvider(Provider.SWITCHBOT),
                FakeInspectableProvider(Provider.NETATMO)
            )
        )

        // When
        val result = sut.execute(Provider.NETATMO, "home-status", mapOf("homeId" to "h-1")).shouldBeRight()

        // Then
        result shouldBe """{"provider":"NETATMO","probe":"home-status","params":"{homeId=h-1}"}"""
    }

    /** Guard (green on arrival): the port failure must reach the caller untouched. */
    @Test
    fun `execute() passes the port failure through untouched`() {
        // Given
        val portFailure = UnknownProbe("nope", availableProbes = setOf("echo"))
        val failing = object : InspectableProvider {
            override val provider = Provider.SWITCHBOT
            override val probes = setOf("echo")
            override fun inspect(probe: String, params: Map<String, String>) = portFailure.left()
        }
        val sut = InspectProviderService(inspectableProviders = setOf(failing))

        // When
        val result = sut.execute(Provider.SWITCHBOT, "nope", emptyMap()).shouldBeLeft()

        // Then
        result shouldBe portFailure
    }
}
