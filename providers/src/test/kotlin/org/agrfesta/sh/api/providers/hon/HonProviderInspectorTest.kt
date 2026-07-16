package org.agrfesta.sh.api.providers.hon

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.agrfesta.sh.api.core.application.ports.outbounds.RandomGenerator
import org.agrfesta.sh.api.core.application.ports.outbounds.TimeProvider
import org.agrfesta.sh.api.core.domain.failures.MissingProbeParams
import org.agrfesta.sh.api.core.domain.failures.ProviderInspectionFailure
import org.agrfesta.sh.api.core.domain.failures.UnknownProbe
import org.agrfesta.sh.api.providers.FakePropertyRepository
import org.agrfesta.sh.api.providers.createMockEngine
import org.agrfesta.sh.api.providers.netatmo.BehaviorRegistry
import org.agrfesta.sh.api.providers.netatmo.ResponseSpec
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class HonProviderInspectorTest {
    private val config = HonConfiguration(
        enabled = true,
        email = "user@example.com",
        password = "secret-pw",
        authApiUrl = "https://auth.example.com",
        apiUrl = "https://api.example.com",
    )
    private val propertyRepository = FakePropertyRepository()
    private val randomGenerator: RandomGenerator = mockk {
        every { uuid() } returns UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
    }
    private val timeProvider: TimeProvider = mockk {
        every { now() } returns Instant.parse("2026-07-16T10:00:00Z")
    }
    private val registry = BehaviorRegistry()
    private val engine = createMockEngine(registry)
    private val client = HonApiClient(config, propertyRepository, randomGenerator, timeProvider, engine)

    private val sut = HonProviderInspector(client)

    /** A session obtainable via refresh grant, mirroring [HonApiClientTest]'s helper. */
    private fun givenRefreshableSession() {
        propertyRepository.givenProperty(HonAuth.HON_REFRESH_TOKEN_KEY, "RRR0")
        registry.given(
            { it.method == HttpMethod.Post && it.url.encodedPath == "/services/oauth2/token" },
            ResponseSpec("""{"id_token":"II1","access_token":"AA1","refresh_token":"RRR1"}"""),
        )
        registry.given(
            { it.method == HttpMethod.Post && it.url.encodedPath == "/auth/v1/login" },
            ResponseSpec("""{"cognitoUser":{"Token":"COG1"}}"""),
        )
    }

    @Test
    fun `inspect() with an unknown probe returns UnknownProbe carrying the available probes`() {
        // Given
        val unknownProbe = "send-command"

        // When
        val result = sut.inspect(unknownProbe, emptyMap()).shouldBeLeft()

        // Then
        result shouldBe UnknownProbe(
            probe = unknownProbe,
            availableProbes = setOf("appliance-list", "context", "commands", "appliance-model")
        )
    }

    @Test
    fun `appliance-list probe returns the provider body verbatim`() {
        // Given: a body whose shape differs from what the typed client expects,
        // with irregular whitespace — a parse → re-serialize round trip would alter it
        val unexpectedShapeBody = """{"totally": "unexpected",   "shape": [1, 2]}"""
        givenRefreshableSession()
        registry.given(
            { it.method == HttpMethod.Post && it.url.encodedPath == "/unified-api/v1/view/appliance-list" },
            ResponseSpec(unexpectedShapeBody),
        )

        // When
        val result = sut.inspect("appliance-list", emptyMap()).shouldBeRight()

        // Then
        result shouldBe unexpectedShapeBody
    }

    @Test
    fun `context probe forwards the params plus the CYCLE category and returns the body verbatim`() {
        // Given
        val unexpectedShapeBody = """{"payload": {"whatever": 1},  "extra": true}"""
        givenRefreshableSession()
        registry.given(
            { it.method == HttpMethod.Get && it.url.encodedPath == "/commands/v1/context" },
            ResponseSpec(unexpectedShapeBody),
        )

        // When
        val result = sut.inspect(
            "context",
            mapOf("macAddress" to "AA-BB-01", "applianceType" to "WM")
        ).shouldBeRight()

        // Then
        result shouldBe unexpectedShapeBody
        runBlocking {
            registry.verifyRequest(HttpMethod.Get, "/commands/v1/context") { request ->
                withClue("the probe must preserve the polling-call query shape") {
                    request.url.parameters["macAddress"] shouldBe "AA-BB-01"
                    request.url.parameters["applianceType"] shouldBe "WM"
                    request.url.parameters["category"] shouldBe "CYCLE"
                }
            }
        }
    }

    @Test
    fun `commands probe forwards identifiers plus os and appVersion and returns the body verbatim`() {
        // Given
        val unexpectedShapeBody = """{"payload": {"resultCode": "9",  "odd": []}}"""
        givenRefreshableSession()
        registry.given(
            { it.method == HttpMethod.Get && it.url.encodedPath == "/commands/v1/retrieve" },
            ResponseSpec(unexpectedShapeBody),
        )
        val params = mapOf(
            "macAddress" to "AA-BB-01",
            "applianceType" to "WM",
            "applianceModelId" to "1234",
            "code" to "C1",
            "series" to "s100",
        )

        // When
        val result = sut.inspect("commands", params).shouldBeRight()

        // Then
        result shouldBe unexpectedShapeBody
        runBlocking {
            registry.verifyRequest(HttpMethod.Get, "/commands/v1/retrieve") { request ->
                withClue("catalog identifiers must pass through, os/appVersion must be defaulted") {
                    request.url.parameters["macAddress"] shouldBe "AA-BB-01"
                    request.url.parameters["applianceType"] shouldBe "WM"
                    request.url.parameters["applianceModelId"] shouldBe "1234"
                    request.url.parameters["code"] shouldBe "C1"
                    request.url.parameters["series"] shouldBe "s100"
                    request.url.parameters["os"] shouldBe HonConstants.OS
                    request.url.parameters["appVersion"] shouldBe HonConstants.APP_VERSION
                }
            }
        }
    }

    @Test
    fun `appliance-model probe forwards macAddress and code and returns the body verbatim`() {
        // Given
        val unexpectedShapeBody = """{"payload": "not-an-object at all",  "n": 1}"""
        givenRefreshableSession()
        registry.given(
            { it.method == HttpMethod.Get && it.url.encodedPath == "/commands/v1/appliance-model" },
            ResponseSpec(unexpectedShapeBody),
        )

        // When
        val result = sut.inspect(
            "appliance-model",
            mapOf("macAddress" to "AA-BB-01", "code" to "C1")
        ).shouldBeRight()

        // Then
        result shouldBe unexpectedShapeBody
        runBlocking {
            registry.verifyRequest(HttpMethod.Get, "/commands/v1/appliance-model") { request ->
                withClue("the model-sheet query must carry macAddress and code") {
                    request.url.parameters["macAddress"] shouldBe "AA-BB-01"
                    request.url.parameters["code"] shouldBe "C1"
                }
            }
        }
    }

    @Test
    fun `non-JSON provider body is wrapped in a JSON envelope`() {
        // Given: the cloud answers 200 with a non-JSON body (e.g. an HTML maintenance page)
        val nonJsonBody = "<html>maintenance</html>"
        givenRefreshableSession()
        registry.given(
            { it.method == HttpMethod.Post && it.url.encodedPath == "/unified-api/v1/view/appliance-list" },
            ResponseSpec(nonJsonBody),
        )

        // When
        val result = sut.inspect("appliance-list", emptyMap()).shouldBeRight()

        // Then
        result shouldBe """{"nonJsonBody":"<html>maintenance</html>"}"""
    }

    @Test
    fun `a transport failure maps to ProviderInspectionFailure surfacing the cause`() {
        // Given: the cloud keeps answering 500 to the probe's backing call
        givenRefreshableSession()
        registry.given(
            { it.method == HttpMethod.Post && it.url.encodedPath == "/unified-api/v1/view/appliance-list" },
            ResponseSpec("""{"error":"boom"}""", status = HttpStatusCode.InternalServerError),
        )

        // When
        val result = sut.inspect("appliance-list", emptyMap()).shouldBeLeft()

        // Then
        result shouldBe ProviderInspectionFailure("HonServerError(statusCode=500)")
    }

    @Test
    fun `context probe without its required params returns MissingProbeParams with the expected set`() {
        // When
        val result = sut.inspect("context", emptyMap()).shouldBeLeft()

        // Then
        result shouldBe MissingProbeParams(
            probe = "context",
            expectedParams = setOf("macAddress", "applianceType")
        )
    }

    @Test
    fun `commands probe without its required params returns MissingProbeParams with the expected set`() {
        // Given: one required param present — the validation must still flag the incomplete set
        val params = mapOf("macAddress" to "AA:BB")

        // When
        val result = sut.inspect("commands", params).shouldBeLeft()

        // Then
        result shouldBe MissingProbeParams(
            probe = "commands",
            expectedParams = setOf("macAddress", "applianceType", "applianceModelId", "code")
        )
    }

    @Test
    fun `appliance-model probe without its required params returns MissingProbeParams with the expected set`() {
        // When
        val result = sut.inspect("appliance-model", emptyMap()).shouldBeLeft()

        // Then
        result shouldBe MissingProbeParams(
            probe = "appliance-model",
            expectedParams = setOf("macAddress", "code")
        )
    }
}
