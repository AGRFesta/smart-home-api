package org.agrfesta.sh.api.providers.hon.devices

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.agrfesta.sh.api.core.application.ports.outbounds.RandomGenerator
import org.agrfesta.sh.api.core.application.ports.outbounds.TimeProvider
import org.agrfesta.sh.api.core.domain.commons.Temperature
import org.agrfesta.sh.api.core.domain.devices.AcFanSpeed
import org.agrfesta.sh.api.core.domain.devices.AcMode
import org.agrfesta.sh.api.core.domain.devices.ActuatorStatus
import org.agrfesta.sh.api.core.domain.failures.DevicesProviderError
import org.agrfesta.sh.api.providers.FakePropertyRepository
import org.agrfesta.sh.api.providers.createMockEngine
import org.agrfesta.sh.api.providers.hon.HON_OBJECT_MAPPER
import org.agrfesta.sh.api.providers.hon.HonApiClient
import org.agrfesta.sh.api.providers.hon.HonApplianceRef
import org.agrfesta.sh.api.providers.hon.HonApplianceStore
import org.agrfesta.sh.api.providers.hon.HonAuth
import org.agrfesta.sh.api.providers.hon.HonCommandRejected
import org.agrfesta.sh.api.providers.hon.HonConfiguration
import org.agrfesta.sh.api.providers.netatmo.BehaviorRegistry
import org.agrfesta.sh.api.providers.netatmo.ResponseSpec
import org.agrfesta.sh.api.providers.netatmo.getBodyAsString
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * Slice tests of the AC driver over the REAL fixtures captured live from an AS35PBPHRA-PRE
 * (resources/hon/ac-commands.json + ac-context.json): a real [HonApiClient] runs against a
 * scripted engine, so every assertion is on the wire protocol — the body that would reach
 * the hOn cloud.
 */
class HonAcTest {
    private val mapper = HON_OBJECT_MAPPER
    private val catalogBody = javaClass.getResource("/hon/ac-commands.json")!!.readText()
    private val contextBody = javaClass.getResource("/hon/ac-context.json")!!.readText()
    private val uuid: UUID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")

    /** A fresh transport + store per scenario, with the session already refreshable. */
    private inner class Harness {
        val registry = BehaviorRegistry()
        private val config = HonConfiguration(
            enabled = true,
            email = "user@example.com",
            password = "secret-pw",
            authApiUrl = "https://auth.example.com",
            apiUrl = "https://api.example.com",
        )
        private val propertyRepository = FakePropertyRepository().apply {
            givenProperty(HonAuth.HON_REFRESH_TOKEN_KEY, "RRR0")
            givenProperty(HonApiClient.HON_MOBILE_ID_KEY, "mob-1")
        }
        private val randomGenerator: RandomGenerator = mockk {
            every { uuid() } returns UUID.fromString("11111111-2222-3333-4444-555555555555")
        }
        private val timeProvider: TimeProvider = mockk {
            every { now() } returns Instant.parse("2026-07-19T10:00:00Z")
        }
        private val client =
            HonApiClient(config, propertyRepository, randomGenerator, timeProvider, createMockEngine(registry))
        val store = HonApplianceStore()
        val sut = HonAc(uuid, MAC, store, client)

        init {
            registry.given(
                { it.method == HttpMethod.Post && it.url.encodedPath == "/services/oauth2/token" },
                ResponseSpec("""{"id_token":"II1","access_token":"AA1","refresh_token":"RRR1"}"""),
                ResponseSpec("""{"id_token":"II1","access_token":"AA1","refresh_token":"RRR1"}"""),
            )
            registry.given(
                { it.method == HttpMethod.Post && it.url.encodedPath == "/auth/v1/login" },
                ResponseSpec("""{"cognitoUser":{"Token":"COG1"}}"""),
                ResponseSpec("""{"cognitoUser":{"Token":"COG1"}}"""),
            )
        }

        /** The device sync has run: the store carries the AS35's appliance-list fields. */
        fun givenSyncedAppliance() {
            store.save(
                HonApplianceRef(
                    macAddress = MAC,
                    applianceType = "AC",
                    applianceModelId = "13337",
                    code = "AACCY0E0000",
                    firmwareId = "41",
                    fwVersion = "6.8.0",
                    series = "pearl",
                ),
            )
        }

        fun givenCatalog() {
            registry.given(
                { it.method == HttpMethod.Get && it.url.encodedPath == "/commands/v1/retrieve" },
                ResponseSpec(catalogBody),
            )
        }

        fun givenContext(body: String = contextBody) {
            registry.given(
                { it.method == HttpMethod.Get && it.url.encodedPath == "/commands/v1/context" },
                ResponseSpec(body),
            )
        }

        fun givenSendAccepted() {
            registry.given(
                { it.method == HttpMethod.Post && it.url.encodedPath == "/commands/v1/send" },
                ResponseSpec("""{"payload":{"resultCode":"0"}}"""),
            )
        }
    }

    private fun writableHarness() = Harness().apply {
        givenSyncedAppliance()
        givenCatalog()
        givenContext()
        givenSendAccepted()
    }

    @Test
    fun `setMode sends the full settings command with the requested machMode`() {
        // Given
        val harness = writableHarness()

        // When: cooling is requested (machMode 1 on this firmware)
        val result = harness.sut.setMode(AcMode.COOL)

        // Then
        result.shouldBeRight()
        runBlocking {
            harness.registry.verifyRequest(HttpMethod.Post, "/commands/v1/send") { request ->
                val body = mapper.readTree(request.getBodyAsString())
                body.get("commandName").asText() shouldBe "settings"
                withClue("the requested mode overlays the read-modify-write state") {
                    body.at("/parameters/machMode").asText() shouldBe "1"
                }
                withClue("ALL 38 settings parameters travel, resolved from the real device context") {
                    body.get("parameters").size() shouldBe 38
                }
                withClue("range values from the context are normalized to the catalog grid") {
                    body.at("/parameters/tempSel").asText() shouldBe "26"
                }
                withClue("the off-device windDirectionVertical 0 is sanitized to the fixed position") {
                    body.at("/parameters/windDirectionVertical").asText() shouldBe "2"
                }
                withClue("wire ancillaries travel, rules stripped") {
                    body.at("/ancillaryParameters/remoteActionable").asText() shouldBe "1"
                    body.at("/ancillaryParameters/programRules").isMissingNode shouldBe true
                }
            }
        }
    }

    @Test
    fun `setMode translates every domain mode to its firmware machMode code`() {
        // Given: golden codes from the real AS35 catalog — auto=0, cool=1, dry=2, heat=4, fan=6
        val expectedCodes = mapOf(
            AcMode.AUTO to "0",
            AcMode.COOL to "1",
            AcMode.DRY to "2",
            AcMode.HEAT to "4",
            AcMode.FAN_ONLY to "6",
        )

        expectedCodes.forEach { (mode, code) ->
            // Given: a fresh harness per send
            val harness = writableHarness()

            // When
            val result = harness.sut.setMode(mode)

            // Then
            withClue("$mode should be accepted by the catalog validation") { result.shouldBeRight() }
            runBlocking {
                harness.registry.verifyRequest(HttpMethod.Post, "/commands/v1/send") { request ->
                    withClue("$mode must travel as machMode=$code") {
                        mapper.readTree(request.getBodyAsString())
                            .at("/parameters/machMode").asText() shouldBe code
                    }
                }
            }
        }
    }

    @Test
    fun `on sends the settings command with onOffStatus 1`() {
        // Given: the real context reports the device OFF (onOffStatus 0)
        val harness = writableHarness()

        // When
        val result = harness.sut.on()

        // Then
        result.shouldBeRight()
        runBlocking {
            harness.registry.verifyRequest(HttpMethod.Post, "/commands/v1/send") { request ->
                mapper.readTree(request.getBodyAsString())
                    .at("/parameters/onOffStatus").asText() shouldBe "1"
            }
        }
    }

    @Test
    fun `off sends the settings command with onOffStatus 0`() {
        // Given
        val harness = writableHarness()

        // When
        val result = harness.sut.off()

        // Then
        result.shouldBeRight()
        runBlocking {
            harness.registry.verifyRequest(HttpMethod.Post, "/commands/v1/send") { request ->
                mapper.readTree(request.getBodyAsString())
                    .at("/parameters/onOffStatus").asText() shouldBe "0"
            }
        }
    }

    @Test
    fun `setTargetTemperature sends a clean integer tempSel`() {
        // Given
        val harness = writableHarness()

        // When: the Temperature VO normalizes the quantity — the wire form must be "23", never "23.0"
        val result = harness.sut.setTargetTemperature(Temperature.of("23.0"))

        // Then
        result.shouldBeRight()
        runBlocking {
            harness.registry.verifyRequest(HttpMethod.Post, "/commands/v1/send") { request ->
                mapper.readTree(request.getBodyAsString())
                    .at("/parameters/tempSel").asText() shouldBe "23"
            }
        }
    }

    @Test
    fun `setFanSpeed translates the domain speed to its firmware windSpeed code`() {
        // Given: golden codes from the real AS35 catalog — high=1, medium=2, low=3, auto=5
        val harness = writableHarness()

        // When
        val result = harness.sut.setFanSpeed(AcFanSpeed.HIGH)

        // Then
        result.shouldBeRight()
        runBlocking {
            harness.registry.verifyRequest(HttpMethod.Post, "/commands/v1/send") { request ->
                mapper.readTree(request.getBodyAsString())
                    .at("/parameters/windSpeed").asText() shouldBe "1"
            }
        }
    }

    @Test
    fun `getActuatorStatus reads onOffStatus from the device context`() {
        // Given: the REAL captured context reports onOffStatus 0
        val off = Harness().apply {
            givenSyncedAppliance()
            givenContext()
        }

        // When / Then
        off.sut.getActuatorStatus().shouldBeRight() shouldBe ActuatorStatus.OFF

        // Given: a powered-on variant
        val on = Harness().apply {
            givenSyncedAppliance()
            givenContext(
                """{"payload":{"shadow":{"parameters":{"onOffStatus":{"parNewVal":"1"}}}}}""",
            )
        }
        on.sut.getActuatorStatus().shouldBeRight() shouldBe ActuatorStatus.ON

        // Given: a context not reporting onOffStatus at all
        val unknown = Harness().apply {
            givenSyncedAppliance()
            givenContext("""{"payload":{"shadow":{"parameters":{}}}}""")
        }
        withClue("without a reported onOffStatus the driver must not guess") {
            unknown.sut.getActuatorStatus().shouldBeRight() shouldBe ActuatorStatus.UNDEFINED
        }
    }

    @Test
    fun `a command the cloud rejects surfaces as a provider failure`() {
        // Given: catalog and context are fine, but the cloud answers resultCode 1
        val harness = Harness().apply {
            givenSyncedAppliance()
            givenCatalog()
            givenContext()
            registry.given(
                { it.method == HttpMethod.Post && it.url.encodedPath == "/commands/v1/send" },
                ResponseSpec("""{"payload":{"resultCode":"1"}}"""),
            )
        }

        // When
        val result = harness.sut.setMode(AcMode.COOL)

        // Then
        val failure = result.shouldBeLeft().shouldBeInstanceOf<HonAcProviderFailure>()
        withClue("the wrapped transport failure must be the command rejection") {
            failure.failure shouldBe HonCommandRejected
        }
    }

    @Test
    fun `a write before the device sync surfaces as not-synced, not as a crash`() {
        // Given: a fresh restart — the in-memory store has no appliance ref yet
        val harness = Harness()

        // When
        val result = harness.sut.setMode(AcMode.COOL)

        // Then
        result.shouldBeLeft().shouldBeInstanceOf<AcApplianceNotSynced>()
        withClue("the status read shares the same contract as the writes") {
            harness.sut.getActuatorStatus().shouldBeLeft().shouldBeInstanceOf<AcApplianceNotSynced>()
        }
    }

    @Test
    fun `inspect returns the raw context body verbatim`() {
        // Given
        val harness = Harness().apply {
            givenSyncedAppliance()
            givenContext()
        }

        // When
        val result = harness.sut.inspect()

        // Then: pass-through — byte-for-byte the provider body, no parse -> re-serialize
        result.shouldBeRight() shouldBe contextBody
        runBlocking {
            harness.registry.verifyRequest(HttpMethod.Get, "/commands/v1/context") { request ->
                withClue("the context query is built from the stored appliance ref") {
                    request.url.parameters["macAddress"] shouldBe MAC
                    request.url.parameters["applianceType"] shouldBe "AC"
                    request.url.parameters["category"] shouldBe "CYCLE"
                }
            }
        }
    }

    @Test
    fun `inspect maps a client failure to a provider error`() {
        // Given: the cloud answers the context probe with a genuine server error
        val harness = Harness().apply {
            givenSyncedAppliance()
            registry.given(
                { it.method == HttpMethod.Get && it.url.encodedPath == "/commands/v1/context" },
                ResponseSpec("""{"error":"boom"}""", status = HttpStatusCode.InternalServerError),
            )
        }

        // When
        val result = harness.sut.inspect()

        // Then: mirrors SwitchBotMeter/NetatmoSmarther — a clean exception, no raw body leak
        val error = result.shouldBeLeft().shouldBeInstanceOf<DevicesProviderError>()
        error.exception.message shouldBe "hOn server error (HTTP 500)"
    }

    companion object {
        private const val MAC = "aa-bb-cc-00-00-02"
    }
}
