package org.agrfesta.sh.api.providers.hon

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.agrfesta.sh.api.core.application.ports.outbounds.RandomGenerator
import org.agrfesta.sh.api.core.application.ports.outbounds.TimeProvider
import org.agrfesta.sh.api.providers.FakePropertyRepository
import org.agrfesta.sh.api.providers.createMockEngine
import org.agrfesta.sh.api.providers.netatmo.BehaviorRegistry
import org.agrfesta.sh.api.providers.netatmo.ResponseSpec
import org.agrfesta.sh.api.providers.netatmo.getBodyAsString
import org.junit.jupiter.api.Test
import java.net.UnknownHostException
import java.time.Duration
import java.time.Instant
import java.util.UUID

class HonApiClientTest {
    private val config = HonConfiguration(
        enabled = true,
        email = "user@example.com",
        password = "secret-pw",
        authApiUrl = "https://auth.example.com",
        apiUrl = "https://api.example.com",
    )
    private val propertyRepository = FakePropertyRepository()
    private val generatedUuid = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
    private val randomGenerator: RandomGenerator = mockk {
        every { uuid() } returns generatedUuid
    }
    private var clock: Instant = Instant.parse("2026-07-16T10:00:00Z")
    private val timeProvider: TimeProvider = mockk {
        every { now() } answers { clock }
    }
    private val registry = BehaviorRegistry()
    private val engine = createMockEngine(registry)
    private val mapper = HON_OBJECT_MAPPER

    private val sut = HonApiClient(config, propertyRepository, randomGenerator, timeProvider, engine)

    /** A session obtainable via refresh grant: tokens II1/AA1 (rotated RRR1) + cognito COG1. */
    private fun givenRefreshableSession() {
        // Responses provided twice so a test can drive two auth attempts (e.g. proactive
        // refresh) without the mock engine exhausting its stubbed responses.
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

    // /// loadAppliances() /////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `loadAppliances sends authenticated request and returns appliances`() {
        // Given: a refresh token persisted by a previous session
        propertyRepository.givenProperty(HonAuth.HON_REFRESH_TOKEN_KEY, "RRR0")
        givenRefreshableSession()
        registry.given(
            { it.method == HttpMethod.Post && it.url.encodedPath == "/unified-api/v1/view/appliance-list" },
            ResponseSpec(
                """{"modules":{"applianceList":{"payload":{"appliances":""" +
                    """[{"nickName":"Studio"},{"nickName":"Camera"}]}}}}""",
            ),
        )

        // When
        val result = runBlocking { sut.loadAppliances() }

        // Then
        val appliances = result.shouldBeRight()
        withClue("appliances extracted from modules.applianceList.payload.appliances") {
            appliances.map { it.get("nickName").asText() } shouldBe listOf("Studio", "Camera")
        }
        runBlocking {
            registry.verifyRequest(HttpMethod.Post, "/unified-api/v1/view/appliance-list") { request ->
                withClue("every API call carries both tokens and the user agent") {
                    request.headers["cognito-token"] shouldBe "COG1"
                    request.headers["id-token"] shouldBe "II1"
                    request.headers[HttpHeaders.UserAgent] shouldBe HonConstants.USER_AGENT
                }
            }
            registry.verifyRequest(HttpMethod.Post, "/services/oauth2/token") { request ->
                withClue("session must bootstrap from the PERSISTED refresh token, no full login") {
                    request.url.parameters["refresh_token"] shouldBe "RRR0"
                }
            }
        }
    }

    @Test fun `loadAppliances generates and persists the mobileId when absent`() {
        // Given: no HON_MOBILE_ID persisted yet (first run of this installation)
        propertyRepository.givenProperty(HonAuth.HON_REFRESH_TOKEN_KEY, "RRR0")
        givenRefreshableSession()
        givenApplianceList()

        // When
        runBlocking { sut.loadAppliances() }

        // Then: the generated id is used on the wire AND persisted for the next runs
        withClue("mobileId must be stable per installation, so it must be persisted") {
            propertyRepository.storedValue(HonApiClient.HON_MOBILE_ID_KEY) shouldBe
                generatedUuid.toString()
        }
        runBlocking {
            registry.verifyRequest(HttpMethod.Post, "/unified-api/v1/view/appliance-list") { request ->
                mapper.readTree(request.getBodyAsString()).get("deviceId").asText() shouldBe
                    generatedUuid.toString()
            }
        }
    }

    @Test fun `loadAppliances reuses the persisted mobileId`() {
        // Given: a mobileId persisted by a previous run of this installation
        propertyRepository.givenProperty(HonApiClient.HON_MOBILE_ID_KEY, "mob-1")
        propertyRepository.givenProperty(HonAuth.HON_REFRESH_TOKEN_KEY, "RRR0")
        givenRefreshableSession()
        givenApplianceList()

        // When
        runBlocking { sut.loadAppliances() }

        // Then
        runBlocking {
            registry.verifyRequest(HttpMethod.Post, "/unified-api/v1/view/appliance-list") { request ->
                withClue("the installation identity must not change across restarts") {
                    mapper.readTree(request.getBodyAsString()).get("deviceId").asText() shouldBe "mob-1"
                }
            }
        }
        propertyRepository.storedValue(HonApiClient.HON_MOBILE_ID_KEY) shouldBe "mob-1"
    }

    @Test fun `loadAppliances returns empty list on malformed payload`() {
        // Given: the cloud changed the response shape without notice
        propertyRepository.givenProperty(HonAuth.HON_REFRESH_TOKEN_KEY, "RRR0")
        givenRefreshableSession()
        registry.given(
            { it.method == HttpMethod.Post && it.url.encodedPath == "/unified-api/v1/view/appliance-list" },
            ResponseSpec("""{"unexpected":"shape"}"""),
        )

        // When
        val result = runBlocking { sut.loadAppliances() }

        // Then: defensive extraction, never an exception
        result.shouldBeRight() shouldBe emptyList()
    }

    // /// loadAttributes() /////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `loadAttributes returns the context payload`() {
        // Given
        propertyRepository.givenProperty(HonAuth.HON_REFRESH_TOKEN_KEY, "RRR0")
        givenRefreshableSession()
        registry.given(
            { it.method == HttpMethod.Get && it.url.encodedPath == "/commands/v1/context" },
            ResponseSpec(
                """{"payload":{"shadow":{"parameters":{"tempSel":{"parNewVal":"22"}}}},"resultCode":"0"}""",
            ),
        )
        val appliance = HonApplianceRef(macAddress = "00-4b-12", applianceType = "AC")

        // When
        val result = runBlocking { sut.loadAttributes(appliance) }

        // Then
        result.shouldBeRight() shouldBe
            mapper.readTree("""{"shadow":{"parameters":{"tempSel":{"parNewVal":"22"}}}}""")
        runBlocking {
            registry.verifyRequest(HttpMethod.Get, "/commands/v1/context") { request ->
                withClue("context query identifies the device and the CYCLE category") {
                    request.url.parameters["macAddress"] shouldBe "00-4b-12"
                    request.url.parameters["applianceType"] shouldBe "AC"
                    request.url.parameters["category"] shouldBe "CYCLE"
                }
            }
        }
    }

    // /// loadApplianceModel() /////////////////////////////////////////////////////////////////////////////////////

    @Test fun `loadApplianceModel returns the applianceModel payload`() {
        // Given
        propertyRepository.givenProperty(HonAuth.HON_REFRESH_TOKEN_KEY, "RRR0")
        givenRefreshableSession()
        registry.given(
            { it.method == HttpMethod.Get && it.url.encodedPath == "/commands/v1/appliance-model" },
            ResponseSpec("""{"payload":{"applianceModel":{"options":{"x":"1"}}}}"""),
        )
        val appliance = HonApplianceRef(macAddress = "00-4b-12", applianceType = "AC", code = "C0DE")

        // When
        val result = runBlocking { sut.loadApplianceModel(appliance) }

        // Then
        result.shouldBeRight() shouldBe mapper.readTree("""{"options":{"x":"1"}}""")
        runBlocking {
            registry.verifyRequest(HttpMethod.Get, "/commands/v1/appliance-model") { request ->
                withClue("model sheet is looked up by code + macAddress") {
                    request.url.parameters["code"] shouldBe "C0DE"
                    request.url.parameters["macAddress"] shouldBe "00-4b-12"
                }
            }
        }
    }

    // /// loadCommands() ///////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `loadCommands returns the catalog when resultCode is 0`() {
        // Given
        propertyRepository.givenProperty(HonAuth.HON_REFRESH_TOKEN_KEY, "RRR0")
        givenRefreshableSession()
        registry.given(
            { it.method == HttpMethod.Get && it.url.encodedPath == "/commands/v1/retrieve" },
            ResponseSpec(
                """{"payload":{"resultCode":"0","settings":{"tempSel":{}},"startProgram":{}}}""",
            ),
        )
        val appliance = HonApplianceRef(
            macAddress = "00-4b-12",
            applianceType = "AC",
            applianceModelId = "m-77",
            code = "C0DE",
            firmwareId = "fw-1",
        )

        // When
        val result = runBlocking { sut.loadCommands(appliance) }

        // Then: the catalog, with the resultCode marker stripped
        result.shouldBeRight() shouldBe
            mapper.readTree("""{"settings":{"tempSel":{}},"startProgram":{}}""")
        runBlocking {
            registry.verifyRequest(HttpMethod.Get, "/commands/v1/retrieve") { request ->
                withClue("catalog lookup carries the full device identity + client identity") {
                    request.url.parameters["applianceType"] shouldBe "AC"
                    request.url.parameters["applianceModelId"] shouldBe "m-77"
                    request.url.parameters["macAddress"] shouldBe "00-4b-12"
                    request.url.parameters["code"] shouldBe "C0DE"
                    request.url.parameters["firmwareId"] shouldBe "fw-1"
                    request.url.parameters["os"] shouldBe HonConstants.OS
                    request.url.parameters["appVersion"] shouldBe HonConstants.APP_VERSION
                }
                withClue("optional identifiers must be omitted when unknown") {
                    request.url.parameters["fwVersion"] shouldBe null
                    request.url.parameters["series"] shouldBe null
                }
            }
        }
    }

    @Test fun `loadCommands returns an empty catalog when resultCode is not 0`() {
        // Given: the cloud flags the lookup as failed
        propertyRepository.givenProperty(HonAuth.HON_REFRESH_TOKEN_KEY, "RRR0")
        givenRefreshableSession()
        registry.given(
            { it.method == HttpMethod.Get && it.url.encodedPath == "/commands/v1/retrieve" },
            ResponseSpec("""{"payload":{"resultCode":"1","error":"not found"}}"""),
        )
        val appliance = HonApplianceRef(macAddress = "00-4b-12", applianceType = "AC")

        // When
        val result = runBlocking { sut.loadCommands(appliance) }

        // Then
        withClue("a failed lookup must not be mistaken for a catalog") {
            result.shouldBeRight() shouldBe mapper.createObjectNode()
        }
    }

    // /// sendCommand() ////////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `sendCommand sends the exact command body`() {
        // Given
        propertyRepository.givenProperty(HonAuth.HON_REFRESH_TOKEN_KEY, "RRR0")
        propertyRepository.givenProperty(HonApiClient.HON_MOBILE_ID_KEY, "mob-1")
        givenRefreshableSession()
        registry.given(
            { it.method == HttpMethod.Post && it.url.encodedPath == "/commands/v1/send" },
            ResponseSpec("""{"payload":{"resultCode":"0"}}"""),
        )
        val appliance = HonApplianceRef(macAddress = "00-4b-12-88-20-24", applianceType = "AC")

        // When
        val result = runBlocking {
            sut.sendCommand(
                appliance = appliance,
                command = "startProgram",
                parameters = mapOf("onOffStatus" to "1", "machMode" to "1", "tempSel" to "22"),
                programName = "iot_cool",
            )
        }

        // Then
        result.shouldBeRight()
        runBlocking {
            registry.verifyRequest(HttpMethod.Post, "/commands/v1/send") { request ->
                val body = mapper.readTree(request.getBodyAsString())
                val timestamp = body.get("timestamp").asText()
                withClue("timestamp: UTC, EXACTLY 3 fraction digits + Z") {
                    timestamp shouldMatch Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z""")
                }
                withClue("transactionId = {mac}_{timestamp}") {
                    body.get("transactionId").asText() shouldBe "00-4b-12-88-20-24_$timestamp"
                }
                body.get("commandName").asText() shouldBe "startProgram"
                body.get("macAddress").asText() shouldBe "00-4b-12-88-20-24"
                body.get("applianceType").asText() shouldBe "AC"
                withClue("programName only for startProgram, forced upper-case") {
                    body.get("programName").asText() shouldBe "IOT_COOL"
                }
                withClue("device block uses the mobileOs variant") {
                    body.at("/device/mobileOs").asText() shouldBe "android"
                    body.at("/device/mobileId").asText() shouldBe "mob-1"
                }
                body.at("/parameters/tempSel").asText() shouldBe "22"
            }
        }
    }

    @Test fun `sendCommand omits programName for non-startProgram commands`() {
        // Given
        propertyRepository.givenProperty(HonAuth.HON_REFRESH_TOKEN_KEY, "RRR0")
        givenRefreshableSession()
        registry.given(
            { it.method == HttpMethod.Post && it.url.encodedPath == "/commands/v1/send" },
            ResponseSpec("""{"payload":{"resultCode":"0"}}"""),
        )
        val appliance = HonApplianceRef(macAddress = "00-4b-12", applianceType = "AC")

        // When
        runBlocking {
            sut.sendCommand(appliance, command = "settings", parameters = mapOf("tempSel" to "24"))
        }

        // Then
        runBlocking {
            registry.verifyRequest(HttpMethod.Post, "/commands/v1/send") { request ->
                withClue("programName must be absent for commands other than startProgram") {
                    mapper.readTree(request.getBodyAsString()).has("programName") shouldBe false
                }
            }
        }
    }

    @Test fun `sendCommand fails when the cloud rejects the command`() {
        // Given: the cloud answers with a non-zero resultCode
        propertyRepository.givenProperty(HonAuth.HON_REFRESH_TOKEN_KEY, "RRR0")
        givenRefreshableSession()
        registry.given(
            { it.method == HttpMethod.Post && it.url.encodedPath == "/commands/v1/send" },
            ResponseSpec("""{"payload":{"resultCode":"1"}}"""),
        )
        val appliance = HonApplianceRef(macAddress = "00-4b-12", applianceType = "AC")

        // When
        val result = runBlocking {
            sut.sendCommand(appliance, command = "settings", parameters = mapOf("tempSel" to "24"))
        }

        // Then
        result.shouldBeLeft().shouldBeInstanceOf<HonCommandRejected>()
    }

    // /// session bootstrap & retry ////////////////////////////////////////////////////////////////////////////////

    @Test fun `first request performs a full login when no refresh token is persisted`() {
        // Given: nothing persisted -> the refresh grant is impossible, full login required
        givenFullLoginFlow()
        givenApplianceList()

        // When
        val result = runBlocking { sut.loadAppliances() }

        // Then
        result.shouldBeRight()
        runBlocking {
            registry.verifyRequest(HttpMethod.Get, "/services/oauth2/authorize/expid_Login")
            registry.verifyRequest(HttpMethod.Post, "/unified-api/v1/view/appliance-list") { request ->
                withClue("the appliance call uses the cognito token from the full login") {
                    request.headers["cognito-token"] shouldBe "COG"
                }
            }
        }
    }

    /** Stubs the whole Salesforce authorize flow, ending with tokens AAA/RRR/III + cognito COG. */
    private fun givenFullLoginFlow() {
        givenGet(
            "/services/oauth2/authorize/expid_Login",
            page("""window.location.href ='/s/login/legacy?startURL=%2Fx';"""),
        )
        givenGet("/s/login/legacy", redirect("/hop1"))
        givenGet("/hop1", redirect("/hop2?startURL=%2Fx"))
        givenGet("/hop2", page("""<script>{"fwuid":"FW","loaded":{"A":"L"},"o":1}</script>"""))
        registry.given(
            { it.method == HttpMethod.Post && it.url.encodedPath == "/s/sfsites/aura" },
            ResponseSpec("""{"events":[{"attributes":{"values":{"url":"/postlogin"}}}]}"""),
        )
        givenGet("/postlogin", page("""<a href="/finaltok">go</a>"""))
        givenGet("/finaltok", page("access_token=AAA&refresh_token=RRR&id_token=III&x=1"))
        registry.given(
            { it.method == HttpMethod.Post && it.url.encodedPath == "/auth/v1/login" },
            ResponseSpec("""{"cognitoUser":{"Token":"COG"}}"""),
        )
    }

    private fun givenGet(path: String, response: ResponseSpec) {
        registry.given({ it.method == HttpMethod.Get && it.url.encodedPath == path }, response)
    }

    private fun page(content: String) =
        ResponseSpec(content, headers = io.ktor.http.headersOf(HttpHeaders.ContentType, "text/html"))

    private fun redirect(location: String) = ResponseSpec(
        content = "",
        status = io.ktor.http.HttpStatusCode.Found,
        headers = io.ktor.http.headersOf(HttpHeaders.Location, location),
    )

    @Test fun `does not re-authenticate while the token is still fresh`() {
        // Given: a valid session and two consecutive polling calls, clock unchanged
        propertyRepository.givenProperty(HonAuth.HON_REFRESH_TOKEN_KEY, "RRR0")
        givenRefreshableSession()
        givenApplianceList()

        // When
        runBlocking {
            sut.loadAppliances()
            sut.loadAppliances()
        }

        // Then: the session is reused, the refresh grant fires only once
        runBlocking {
            registry.verifyRequest(HttpMethod.Post, "/services/oauth2/token", times = 1)
        }
    }

    @Test fun `re-authenticates when the token is older than 7 hours`() {
        // Given: a valid session, then the clock advances past the 7h renew window
        propertyRepository.givenProperty(HonAuth.HON_REFRESH_TOKEN_KEY, "RRR0")
        givenRefreshableSession()
        givenApplianceList()

        // When
        runBlocking {
            sut.loadAppliances()
            clock = clock.plus(Duration.ofHours(8))
            sut.loadAppliances()
        }

        // Then: the stale token triggers a proactive refresh on the second call
        runBlocking {
            registry.verifyRequest(HttpMethod.Post, "/services/oauth2/token", times = 2)
        }
    }

    @Test fun `retries the request after a refresh on 401`() {
        // Given: a fresh-looking session whose token the cloud rejects once with 401,
        // then accepts after the client refreshes.
        propertyRepository.givenProperty(HonAuth.HON_REFRESH_TOKEN_KEY, "RRR0")
        givenRefreshableSession()
        registry.given(
            { it.method == HttpMethod.Post && it.url.encodedPath == "/unified-api/v1/view/appliance-list" },
            ResponseSpec("""{}""", status = io.ktor.http.HttpStatusCode.Unauthorized),
            ResponseSpec(
                """{"modules":{"applianceList":{"payload":{"appliances":[{"nickName":"Studio"}]}}}}""",
            ),
        )

        // When
        val result = runBlocking { sut.loadAppliances() }

        // Then: the second attempt succeeds; a refresh happened between the attempts
        result.shouldBeRight().map { it.get("nickName").asText() } shouldBe listOf("Studio")
        runBlocking {
            registry.verifyRequest(HttpMethod.Post, "/unified-api/v1/view/appliance-list", times = 2)
            registry.verifyRequest(HttpMethod.Post, "/services/oauth2/token", times = 2)
        }
    }

    @Test fun `falls back to full login when the refresh fails on 401`() {
        // Given: the session bootstraps fine via refresh, then the cloud invalidates the
        // token (401); on retry the refresh is rejected (grant 4xx), so recovering
        // requires a full login.
        propertyRepository.givenProperty(HonAuth.HON_REFRESH_TOKEN_KEY, "RRR0")
        registry.given(
            { it.method == HttpMethod.Post && it.url.encodedPath == "/services/oauth2/token" },
            ResponseSpec("""{"id_token":"II1","access_token":"AA1"}"""),
            ResponseSpec("""{"error":"invalid_grant"}""", status = io.ktor.http.HttpStatusCode.BadRequest),
        )
        registry.given(
            { it.method == HttpMethod.Post && it.url.encodedPath == "/auth/v1/login" },
            ResponseSpec("""{"cognitoUser":{"Token":"COG1"}}"""),
            ResponseSpec("""{"cognitoUser":{"Token":"COG1"}}"""),
        )
        givenFullLoginFlow()
        registry.given(
            { it.method == HttpMethod.Post && it.url.encodedPath == "/unified-api/v1/view/appliance-list" },
            ResponseSpec("""{}""", status = io.ktor.http.HttpStatusCode.Unauthorized),
            ResponseSpec(
                """{"modules":{"applianceList":{"payload":{"appliances":[{"nickName":"Studio"}]}}}}""",
            ),
        )

        // When
        val result = runBlocking { sut.loadAppliances() }

        // Then: full login recovered the session, request succeeded on retry
        result.shouldBeRight().map { it.get("nickName").asText() } shouldBe listOf("Studio")
        runBlocking {
            registry.verifyRequest(HttpMethod.Get, "/services/oauth2/authorize/expid_Login")
            registry.verifyRequest(HttpMethod.Post, "/unified-api/v1/view/appliance-list", times = 2)
        }
    }

    @Test fun `fails with a typed failure when 401 persists after re-authentication`() {
        // Given: the session re-authenticates fine, but the cloud keeps answering 401
        propertyRepository.givenProperty(HonAuth.HON_REFRESH_TOKEN_KEY, "RRR0")
        givenRefreshableSession()
        val unauthorized = ResponseSpec("""{}""", status = io.ktor.http.HttpStatusCode.Unauthorized)
        registry.given(
            { it.method == HttpMethod.Post && it.url.encodedPath == "/unified-api/v1/view/appliance-list" },
            unauthorized,
            unauthorized,
        )

        // When
        val result = runBlocking { sut.loadAppliances() }

        // Then
        result.shouldBeLeft().shouldBeInstanceOf<HonUnauthorized>()
    }

    @Test fun `fails with a typed failure on a non-JSON response`() {
        // Given: the cloud returns an HTML error page instead of JSON
        propertyRepository.givenProperty(HonAuth.HON_REFRESH_TOKEN_KEY, "RRR0")
        givenRefreshableSession()
        registry.given(
            { it.method == HttpMethod.Post && it.url.encodedPath == "/unified-api/v1/view/appliance-list" },
            ResponseSpec(
                "<html><body>502 Bad Gateway</body></html>",
                headers = io.ktor.http.headersOf(HttpHeaders.ContentType, "text/html"),
            ),
        )

        // When
        val result = runBlocking { sut.loadAppliances() }

        // Then
        result.shouldBeLeft().shouldBeInstanceOf<HonNonJsonResponse>()
    }

    // /// hardening (review #253) //////////////////////////////////////////////////////////////////////////////////

    @Test fun `surfaces the auth failure instead of hammering when the account needs MFA`() {
        // Given: no persisted token; the full login lands on the 2FA OTP page
        givenGet(
            "/services/oauth2/authorize/expid_Login",
            page("""window.location.href ='/s/login/legacy?startURL=%2Fx';"""),
        )
        givenGet("/s/login/legacy", redirect("/hop1"))
        givenGet("/hop1", redirect("/hop2?startURL=%2Fx"))
        givenGet("/hop2", page("""<script>{"fwuid":"FW","loaded":{"A":"L"},"o":1}</script>"""))
        registry.given(
            { it.method == HttpMethod.Post && it.url.encodedPath == "/s/sfsites/aura" },
            ResponseSpec("""{"events":[{"attributes":{"values":{"url":"/postlogin"}}}]}"""),
        )
        givenGet("/postlogin", page("""<a href="/apex/ProgressiveLogin">go</a>"""))
        givenGet(
            "/apex/ProgressiveLogin",
            page(
                """<script>ProgressiveLoginController.verifyEmailOTP(c);</script>""" +
                    """<input name="emailcode" />""",
            ),
        )

        // When
        val result = runBlocking { sut.loadAppliances() }

        // Then: the real, actionable failure reaches the caller (not a generic HonUnauthorized)
        result.shouldBeLeft() shouldBe HonMfaRequired
        runBlocking {
            withClue("must not hammer Salesforce: the login runs once, no appliance call") {
                registry.verifyRequest(HttpMethod.Get, "/services/oauth2/authorize/expid_Login", times = 1)
                registry.verifyRequest(HttpMethod.Post, "/unified-api/v1/view/appliance-list", times = 0)
            }
        }
    }

    @Test fun `maps a raw network exception to a typed network error`() {
        // Given: auth succeeds, but the API call throws a ktor network exception
        propertyRepository.givenProperty(HonAuth.HON_REFRESH_TOKEN_KEY, "RRR0")
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/services/oauth2/token" ->
                    respond(
                        """{"id_token":"II1","access_token":"AA1"}""",
                        headers = io.ktor.http.headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                "/auth/v1/login" ->
                    respond(
                        """{"cognitoUser":{"Token":"COG1"}}""",
                        headers = io.ktor.http.headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                else -> throw UnknownHostException("api.example.com")
            }
        }
        val client = HonApiClient(config, propertyRepository, randomGenerator, timeProvider, engine)

        // When
        val result = runBlocking { client.loadAppliances() }

        // Then: the network blip is a typed failure, not a thrown exception
        result.shouldBeLeft().shouldBeInstanceOf<HonNetworkError>()
    }

    @Test fun `maps a non-2xx server response to a typed server error`() {
        // Given: a valid session, then the API answers 500
        propertyRepository.givenProperty(HonAuth.HON_REFRESH_TOKEN_KEY, "RRR0")
        givenRefreshableSession()
        registry.given(
            { it.method == HttpMethod.Get && it.url.encodedPath == "/commands/v1/context" },
            ResponseSpec("""{"error":"boom"}""", status = io.ktor.http.HttpStatusCode.InternalServerError),
        )
        val appliance = HonApplianceRef(macAddress = "00-4b-12", applianceType = "AC")

        // When
        val result = runBlocking { sut.loadAttributes(appliance) }

        // Then: a server error is not silently passed through as a payload
        val failure = result.shouldBeLeft().shouldBeInstanceOf<HonServerError>()
        failure.statusCode shouldBe 500
    }

    private fun givenApplianceList() {
        val response = ResponseSpec(
            """{"modules":{"applianceList":{"payload":{"appliances":""" +
                """[{"nickName":"Studio"},{"nickName":"Camera"}]}}}}""",
        )
        registry.given(
            { it.method == HttpMethod.Post && it.url.encodedPath == "/unified-api/v1/view/appliance-list" },
            response,
            response,
        )
    }
}
