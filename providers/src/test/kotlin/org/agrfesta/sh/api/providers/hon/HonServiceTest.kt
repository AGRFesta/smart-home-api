package org.agrfesta.sh.api.providers.hon

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.mockk.every
import io.mockk.mockk
import org.agrfesta.sh.api.core.application.ports.outbounds.RandomGenerator
import org.agrfesta.sh.api.core.application.ports.outbounds.TimeProvider
import org.agrfesta.sh.api.core.domain.devices.DeviceModel
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.core.domain.devices.ProviderDeviceData
import org.agrfesta.sh.api.core.domain.failures.DevicesProviderError
import org.agrfesta.sh.api.providers.FakePropertyRepository
import org.agrfesta.sh.api.providers.createMockEngine
import org.agrfesta.sh.api.providers.netatmo.BehaviorRegistry
import org.agrfesta.sh.api.providers.netatmo.ResponseSpec
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class HonServiceTest {
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

    private val store = HonApplianceStore()

    private val sut = HonService(client, store)

    init {
        // Shared non-subject setup: every test talks through a session obtainable via
        // refresh grant, mirroring [HonApiClientTest]'s helper.
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

    /** The real (anonymized) appliance-list payload captured in Phase 0 of the hOn epic. */
    private fun realApplianceListFixture(): String =
        javaClass.getResource("/hon/appliance-list.json")!!.readText()

    private fun givenApplianceListResponse(body: String, status: HttpStatusCode = HttpStatusCode.OK) {
        registry.given(
            { it.method == HttpMethod.Post && it.url.encodedPath == "/unified-api/v1/view/appliance-list" },
            ResponseSpec(body, status = status),
        )
    }

    @Test
    fun `getAllDevices answers an empty collection when the appliance payload shape is unexpected`() {
        // Given
        givenApplianceListResponse("""{"unexpected":"shape"}""")

        // When
        val result = sut.getAllDevices()

        // Then
        result.shouldBeRight().shouldBeEmpty()
    }

    @Test
    fun `getAllDevices maps every fixture appliance to ProviderDeviceData`() {
        // Given: the real (anonymized) appliance-list payload captured in Phase 0 — two ACs
        givenApplianceListResponse(realApplianceListFixture())

        // When
        val result = sut.getAllDevices()

        // Then
        withClue("both fixture ACs should be discovered, with mac as id and hon-qualified model") {
            result.shouldBeRight() shouldContainExactlyInAnyOrder listOf(
                ProviderDeviceData(
                    deviceProviderId = "aa-bb-cc-00-00-01",
                    provider = Provider.HON,
                    name = "Studio",
                    model = DeviceModel("hon/AS25PBPHRA-PRE"),
                ),
                ProviderDeviceData(
                    deviceProviderId = "aa-bb-cc-00-00-02",
                    provider = Provider.HON,
                    name = "Clima Camera da letto",
                    model = DeviceModel("hon/AS35PBPHRA-PRE"),
                ),
            )
        }
    }

    @Test
    fun `getAllDevices falls back to the model name when the nickname is blank`() {
        // Given: an appliance whose nickname is blank
        givenApplianceListResponse(
            """{"modules":{"applianceList":{"payload":{"appliances":[
                {"macAddress":"aa-bb-cc-00-00-03","nickName":"   ","modelName":"AS35PBPHRA-PRE"}
            ]}}}}""",
        )

        // When
        val result = sut.getAllDevices()

        // Then
        withClue("a blank nickname should fall back to the model name") {
            result.shouldBeRight().single().name shouldBe "AS35PBPHRA-PRE"
        }
    }

    @Test
    fun `getAllDevices falls back to the model name when the nickname is missing`() {
        // Given: an appliance entry without the nickName field at all
        givenApplianceListResponse(
            """{"modules":{"applianceList":{"payload":{"appliances":[
                {"macAddress":"aa-bb-cc-00-00-08","modelName":"AS25PBPHRA-PRE"}
            ]}}}}""",
        )

        // When
        val result = sut.getAllDevices()

        // Then
        withClue("a missing nickname should fall back to the model name, not throw") {
            result.shouldBeRight().single().name shouldBe "AS25PBPHRA-PRE"
        }
    }

    @Test
    fun `getAllDevices falls back to the model name when the nickname is an explicit null`() {
        // Given: Jackson maps "nickName": null to NullNode, whose asText() is the string "null"
        givenApplianceListResponse(
            """{"modules":{"applianceList":{"payload":{"appliances":[
                {"macAddress":"aa-bb-cc-00-00-09","nickName":null,"modelName":"AS25PBPHRA-PRE"}
            ]}}}}""",
        )

        // When
        val result = sut.getAllDevices()

        // Then
        withClue("an explicit null nickname should fall back to the model name, not become \"null\"") {
            result.shouldBeRight().single().name shouldBe "AS25PBPHRA-PRE"
        }
    }

    @Test
    fun `getAllDevices skips appliances missing macAddress or modelName`() {
        // Given: two malformed entries beside a complete one
        givenApplianceListResponse(
            """{"modules":{"applianceList":{"payload":{"appliances":[
                {"nickName":"NoMac","modelName":"AS25PBPHRA-PRE"},
                {"macAddress":"aa-bb-cc-00-00-04","nickName":"NoModel"},
                {"macAddress":"aa-bb-cc-00-00-05","nickName":"Valid","modelName":"AS35PBPHRA-PRE"}
            ]}}}}""",
        )

        // When
        val result = sut.getAllDevices()

        // Then
        withClue("only the complete entry should survive the mapping") {
            result.shouldBeRight().single().deviceProviderId shouldBe "aa-bb-cc-00-00-05"
        }
    }

    @Test
    fun `getAllDevices preserves the command-endpoint fields in the appliance store`() {
        // Given: the real (anonymized) appliance-list payload captured in Phase 0
        givenApplianceListResponse(realApplianceListFixture())

        // When
        sut.getAllDevices().shouldBeRight()

        // Then
        withClue("the factory must find every command-endpoint field without a second list call") {
            store.refOf("aa-bb-cc-00-00-01") shouldBe HonApplianceRef(
                macAddress = "aa-bb-cc-00-00-01",
                applianceType = "AC",
                applianceModelId = "13336",
                code = "AACCTEST001",
                firmwareId = "41",
                fwVersion = "6.8.0",
                series = "pearl",
            )
        }
        withClue("every fixture appliance should be preserved, not just the first") {
            store.refOf("aa-bb-cc-00-00-02")?.code shouldBe "AACCTEST002"
        }
    }

    @Test
    fun `getAllDevices derives the code from the serial number when code is missing`() {
        // Given: entries without a code — the addhOn rule: first 8 chars of the serial
        // when it is shorter than 18 chars, first 11 otherwise
        givenApplianceListResponse(
            """{"modules":{"applianceList":{"payload":{"appliances":[
                {"macAddress":"aa-bb-cc-00-00-06","nickName":"Short","modelName":"AS25PBPHRA-PRE",
                 "serialNumber":"SHORTSER12345"},
                {"macAddress":"aa-bb-cc-00-00-07","nickName":"Long","modelName":"AS35PBPHRA-PRE",
                 "serialNumber":"AACCTEST002X00000002"}
            ]}}}}""",
        )

        // When
        sut.getAllDevices().shouldBeRight()

        // Then
        withClue("a serial shorter than 18 chars should yield its first 8 chars as code") {
            store.refOf("aa-bb-cc-00-00-06")?.code shouldBe "SHORTSER"
        }
        withClue("a serial of 18+ chars should yield its first 11 chars as code") {
            store.refOf("aa-bb-cc-00-00-07")?.code shouldBe "AACCTEST002"
        }
    }

    @Test
    fun `getAllDevices answers DevicesProviderError when the hOn cloud fails`() {
        // Given: the cloud keeps answering 500 to the appliance-list call
        givenApplianceListResponse("""{"error":"boom"}""", status = HttpStatusCode.InternalServerError)

        // When
        val result = sut.getAllDevices()

        // Then
        val failure = result.shouldBeLeft().shouldBeInstanceOf<DevicesProviderError>()
        withClue("the typed HonFailure should surface as a readable message, not a data-class toString") {
            failure.exception.message shouldBe "hOn server error (HTTP 500)"
        }
    }
}
