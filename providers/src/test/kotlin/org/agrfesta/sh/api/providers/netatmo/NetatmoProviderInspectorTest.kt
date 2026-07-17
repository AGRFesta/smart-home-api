package org.agrfesta.sh.api.providers.netatmo

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.agrfesta.sh.api.core.application.ports.outbounds.Cache
import org.agrfesta.sh.api.core.application.ports.outbounds.settings.PropertyRepository
import org.agrfesta.sh.api.core.domain.failures.MissingProbeParams
import org.agrfesta.sh.api.core.domain.failures.ProviderInspectionFailure
import org.agrfesta.sh.api.core.domain.failures.UnknownProbe
import org.agrfesta.sh.api.providers.createMockEngine
import org.agrfesta.sh.api.providers.netatmo.NetatmoService.Companion.NETATMO_ACCESS_TOKEN_CACHE_KEY
import org.agrfesta.sh.api.utils.CacheAsserter
import org.junit.jupiter.api.Test

class NetatmoProviderInspectorTest {
    private val accessToken = "cached-access-token"
    private val config = NetatmoConfiguration(
        baseUrl = "https://api.netatmo.example.com",
        clientSecret = "nt-secret",
        clientId = "nt-client-id",
        homeId = "configured-home-id",
        roomId = "configured-room-id",
    )
    private val cache: Cache = mockk(relaxed = true)
    private val propertyRepository: PropertyRepository = mockk(relaxed = true)
    private val registry = BehaviorRegistry()
    private val engine = createMockEngine(registry)
    private val cacheAsserter = CacheAsserter(cache, propertyRepository)
    private val client = NetatmoClient(config, cache, propertyRepository, engine)

    private val sut = NetatmoProviderInspector(client)

    init {
        // Default behaviour: a valid access token is already cached
        cacheAsserter.givenCacheEntry(NETATMO_ACCESS_TOKEN_CACHE_KEY, accessToken)
    }

    @Test
    fun `inspect() with an unknown probe returns UnknownProbe carrying the available probes`() {
        // Given
        val unknownProbe = "set-state"

        // When
        val result = sut.inspect(unknownProbe, emptyMap()).shouldBeLeft()

        // Then
        result shouldBe UnknownProbe(
            probe = unknownProbe,
            availableProbes = setOf("home-status")
        )
    }

    @Test
    fun `home-status probe without its required params returns MissingProbeParams with the expected set`() {
        // When
        val result = sut.inspect("home-status", emptyMap()).shouldBeLeft()

        // Then
        result shouldBe MissingProbeParams(
            probe = "home-status",
            expectedParams = setOf("homeId")
        )
    }

    @Test
    fun `home-status probe forwards the homeId and returns the provider body verbatim`() {
        // Given: a body whose shape differs from what the typed client expects,
        // with irregular whitespace — a parse → re-serialize round trip would alter it
        val unexpectedShapeBody = """{"body": {"whatever": 1},   "extra": true}"""
        registry.given(
            { it.method == HttpMethod.Get && it.url.encodedPath == "/api/homestatus" },
            ResponseSpec(unexpectedShapeBody),
        )

        // When
        val result = sut.inspect("home-status", mapOf("homeId" to "home-42")).shouldBeRight()

        // Then
        result shouldBe unexpectedShapeBody
        runBlocking {
            registry.verifyRequest(HttpMethod.Get, "/api/homestatus") { request ->
                withClue("the probe must forward the caller's homeId as home_id") {
                    request.url.parameters["home_id"] shouldBe "home-42"
                }
            }
        }
    }

    @Test
    fun `non-JSON provider body is wrapped in a JSON envelope`() {
        // Given: the cloud answers 200 with a non-JSON body (e.g. an HTML maintenance page)
        val nonJsonBody = "<html>maintenance</html>"
        registry.given(
            { it.method == HttpMethod.Get && it.url.encodedPath == "/api/homestatus" },
            ResponseSpec(nonJsonBody),
        )

        // When
        val result = sut.inspect("home-status", mapOf("homeId" to "home-42")).shouldBeRight()

        // Then
        result shouldBe """{"nonJsonBody":"<html>maintenance</html>"}"""
    }

    @Test
    fun `a client failure maps to ProviderInspectionFailure surfacing the cause`() {
        // Given: the cloud answers 403 to the probe's backing call
        registry.given(
            { it.method == HttpMethod.Get && it.url.encodedPath == "/api/homestatus" },
            ResponseSpec("""{"error":"forbidden"}""", status = HttpStatusCode.Forbidden),
        )

        // When
        val result = sut.inspect("home-status", mapOf("homeId" to "home-42")).shouldBeLeft()

        // Then
        val failure = result.shouldBeInstanceOf<ProviderInspectionFailure>()
        withClue("the failure message should surface the transport error status") {
            failure.message shouldContain "403"
        }
    }

    @Test
    fun `a server error not handled by the client maps to ProviderInspectionFailure surfacing the cause`() {
        // Given: the cloud answers 500 — the client lets the transport exception escape
        registry.given(
            { it.method == HttpMethod.Get && it.url.encodedPath == "/api/homestatus" },
            ResponseSpec("""{"error":"boom"}""", status = HttpStatusCode.InternalServerError),
        )

        // When
        val result = sut.inspect("home-status", mapOf("homeId" to "home-42")).shouldBeLeft()

        // Then
        val failure = result.shouldBeInstanceOf<ProviderInspectionFailure>()
        withClue("the failure message should surface the transport error status") {
            failure.message shouldContain "500"
        }
    }
}
