package org.agrfesta.sh.api.providers.hon

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
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
import java.net.URLDecoder
import java.time.Instant
import java.util.UUID

class HonAuthTest {
    private val authApi = "https://auth.example.com"
    private val apiUrl = "https://api.example.com"
    private val email = "user@example.com"
    private val password = "secret-pw"
    private val mobileId = "mobile-42"

    private val config = HonConfiguration(
        enabled = true,
        email = email,
        password = password,
        authApiUrl = authApi,
        apiUrl = apiUrl,
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
    private val mapper = HON_OBJECT_MAPPER

    private val sut = HonAuth(config, propertyRepository, randomGenerator, timeProvider, engine)

    /**
     * The 6-step happy flow: authorize page -> two manual redirects -> login page
     * (fwuid) -> aura credential POST -> token page -> cognito exchange.
     */
    private fun givenHappyLoginFlow() {
        givenLoginFlowUpToAura()
        givenAuraResponse("""{"events":[{"attributes":{"values":{"url":"/postlogin"}}}]}""")
        givenGet("/postlogin", page("""<a href="/finaltok">continue</a>"""))
        givenGet("/finaltok", page("access_token=AAA&refresh_token=RRR&id_token=III&x=1"))
        registry.given(
            { it.method == HttpMethod.Post && it.url.encodedPath == "/auth/v1/login" },
            ResponseSpec("""{"cognitoUser":{"Token":"COG"}}"""),
        )
    }

    /** Steps 1-3: authorize page, two manual redirect hops, login page with fwuid. */
    private fun givenLoginFlowUpToAura() {
        givenGet(
            "/services/oauth2/authorize/expid_Login",
            page("""window.location.href ='/s/login/legacy?startURL=%2Fsetup%3Fsource%3DABC';"""),
        )
        givenGet("/s/login/legacy", redirect("/hop1"))
        givenGet("/hop1", redirect("/hop2?startURL=%2Fsetup%3Fsource%3DABC"))
        givenGet(
            "/hop2",
            page("""<script>var ctx = {"fwuid":"FW123","loaded":{"APP@x":"L1"},"o":1};</script>"""),
        )
    }

    private fun givenAuraResponse(content: String) {
        registry.given(
            { it.method == HttpMethod.Post && it.url.encodedPath == "/s/sfsites/aura" },
            ResponseSpec(content),
        )
    }

    private fun givenGet(path: String, response: ResponseSpec) {
        registry.given({ it.method == HttpMethod.Get && it.url.encodedPath == path }, response)
    }

    private fun page(content: String) =
        ResponseSpec(content, headers = headersOf(HttpHeaders.ContentType, "text/html"))

    private fun redirect(location: String) = ResponseSpec(
        content = "",
        status = HttpStatusCode.Found,
        headers = headersOf(HttpHeaders.Location, location),
    )

    // /// authenticate() — hardening (review #253) ////////////////////////////////////////////////////////////////

    @Test fun `authenticate fails gracefully when the login page has no fwuid`() {
        // Given: a scraped login page whose structure changed (no fwuid) — must not NPE
        givenGet(
            "/services/oauth2/authorize/expid_Login",
            page("""window.location.href ='/s/login/legacy?startURL=%2Fx';"""),
        )
        givenGet("/s/login/legacy", redirect("/hop1"))
        givenGet("/hop1", redirect("/hop2?startURL=%2Fx"))
        givenGet("/hop2", page("<html>login form changed, no fwuid here</html>"))

        // When
        val result = runBlocking { sut.authenticate(mobileId) }

        // Then
        val failure = result.shouldBeLeft().shouldBeInstanceOf<HonLoginFlowBroken>()
        failure.reason shouldContain "fwuid"
    }

    @Test fun `authenticate does not overwrite the persisted refresh token when tokens are incomplete`() {
        // Given: a previously persisted good refresh token, and a token page whose fragment
        // is missing id_token (shape change) — adopting it would persist an empty token.
        propertyRepository.givenProperty(HonAuth.HON_REFRESH_TOKEN_KEY, "GOOD-RRR")
        givenLoginFlowUpToAura()
        givenAuraResponse("""{"events":[{"attributes":{"values":{"url":"/postlogin"}}}]}""")
        givenGet("/postlogin", page("""<a href="/finaltok">continue</a>"""))
        givenGet("/finaltok", page("access_token=AAA&refresh_token=RRR&x=1")) // no id_token

        // When
        val result = runBlocking { sut.authenticate(mobileId) }

        // Then
        result.shouldBeLeft().shouldBeInstanceOf<HonLoginFlowBroken>()
        withClue("the last known-good refresh token must survive an incomplete parse") {
            propertyRepository.storedValue(HonAuth.HON_REFRESH_TOKEN_KEY) shouldBe "GOOD-RRR"
        }
    }

    // /// authenticate() ///////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `authenticate returns tokens on full login flow`() {
        // Given
        givenHappyLoginFlow()

        // When
        val result = runBlocking { sut.authenticate(mobileId) }

        // Then
        result.shouldBeRight()
        withClue("tokens should be the ones from the fragment + cognito exchange") {
            sut.accessToken shouldBe "AAA"
            sut.refreshToken shouldBe "RRR"
            sut.idToken shouldBe "III"
            sut.cognitoToken shouldBe "COG"
        }
        runBlocking {
            verifyAuraCredentialPost()
            verifyCognitoExchange()
        }
    }

    @Test fun `authenticate persists the refresh token`() {
        // Given
        givenHappyLoginFlow()

        // When
        runBlocking { sut.authenticate(mobileId) }

        // Then: the (rotating) refresh token must survive restarts
        withClue("refresh token should be stored under ${HonAuth.HON_REFRESH_TOKEN_KEY}") {
            propertyRepository.storedValue(HonAuth.HON_REFRESH_TOKEN_KEY) shouldBe "RRR"
        }
    }

    @Test fun `authenticate skips login when authorize page already carries tokens`() {
        // Given: a still-valid SSO session — the authorize page IS the redirect with the
        // fragment (double quotes: no login url for the single-quote scraping regex).
        // The strict mock engine proves the shortcut: any login-flow request would fail.
        givenGet(
            "/services/oauth2/authorize/expid_Login",
            page(
                """window.location.replace("hon://mobilesdk/detect/oauth/done""" +
                    """#access_token=AAA&refresh_token=RRR&id_token=III&x=1");""",
            ),
        )
        registry.given(
            { it.method == HttpMethod.Post && it.url.encodedPath == "/auth/v1/login" },
            ResponseSpec("""{"cognitoUser":{"Token":"COG"}}"""),
        )

        // When
        val result = runBlocking { sut.authenticate(mobileId) }

        // Then
        result.shouldBeRight()
        withClue("tokens adopted straight from the authorize page fragment") {
            sut.accessToken shouldBe "AAA"
            sut.refreshToken shouldBe "RRR"
            sut.idToken shouldBe "III"
            sut.cognitoToken shouldBe "COG"
        }
    }

    @Test fun `authenticate fails when authorize page has no login url`() {
        // Given: neither a login url nor a token fragment (e.g. maintenance page)
        givenGet("/services/oauth2/authorize/expid_Login", page("<html>maintenance</html>"))

        // When
        val result = runBlocking { sut.authenticate(mobileId) }

        // Then
        val failure = result.shouldBeLeft().shouldBeInstanceOf<HonLoginFlowBroken>()
        withClue("failure should point at the broken step") {
            failure.reason shouldContain "login url"
        }
    }

    @Test fun `authenticate reports server message when aura response has no events`() {
        // Given: rejected credentials — the server answers 200 WITHOUT `events`, with
        // the outcome in actions[0].returnValue (live-verified shape, 2026-07-16)
        givenLoginFlowUpToAura()
        givenAuraResponse(
            """{"actions":[{"id":"79;a","state":"SUCCESS",""" +
                """"returnValue":"Your login attempt has failed.","error":[]}]}""",
        )

        // When
        val result = runBlocking { sut.authenticate(mobileId) }

        // Then
        val failure = result.shouldBeLeft().shouldBeInstanceOf<HonLoginFlowBroken>()
        withClue("the server message must surface in the failure") {
            failure.reason shouldContain "Your login attempt has failed."
        }
    }

    @Test fun `authenticate fails with HonMfaRequired on ProgressiveLogin OTP page`() {
        // Given: post-login redirect lands on the 2FA email-OTP page
        givenLoginFlowUpToAura()
        givenAuraResponse("""{"events":[{"attributes":{"values":{"url":"/postlogin"}}}]}""")
        givenGet("/postlogin", page("""<a href="/apex/ProgressiveLogin?x=1">continue</a>"""))
        givenGet(
            "/apex/ProgressiveLogin",
            page(
                """<script>ProgressiveLoginController.verifyEmailOTP(c);</script>""" +
                    """<input name="emailcode" />""",
            ),
        )

        // When
        val result = runBlocking { sut.authenticate(mobileId) }

        // Then
        result.shouldBeLeft() shouldBe HonMfaRequired
    }

    @Test fun `authenticate fails when cognito exchange returns no token`() {
        // Given: full login ok, but the IoT login answers without cognitoUser.Token
        givenLoginFlowUpToAura()
        givenAuraResponse("""{"events":[{"attributes":{"values":{"url":"/postlogin"}}}]}""")
        givenGet("/postlogin", page("""<a href="/finaltok">continue</a>"""))
        givenGet("/finaltok", page("access_token=AAA&refresh_token=RRR&id_token=III&x=1"))
        registry.given(
            { it.method == HttpMethod.Post && it.url.encodedPath == "/auth/v1/login" },
            ResponseSpec("""{"error":"unauthorized"}""", status = HttpStatusCode.Unauthorized),
        )

        // When
        val result = runBlocking { sut.authenticate(mobileId) }

        // Then
        val failure = result.shouldBeLeft().shouldBeInstanceOf<HonLoginFlowBroken>()
        withClue("failure should point at the cognito exchange") {
            failure.reason shouldContain "cognito"
        }
    }

    // /// refresh() ////////////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `refresh renews tokens and re-exchanges cognito`() {
        // Given
        registry.given(
            { it.method == HttpMethod.Post && it.url.encodedPath == "/services/oauth2/token" },
            ResponseSpec("""{"id_token":"II2","access_token":"AA2"}"""),
        )
        registry.given(
            { it.method == HttpMethod.Post && it.url.encodedPath == "/auth/v1/login" },
            ResponseSpec("""{"cognitoUser":{"Token":"COG2"}}"""),
        )

        // When
        val result = runBlocking { sut.refresh(mobileId, externalRefreshToken = "RRR0") }

        // Then
        result.shouldBeRight()
        withClue("renewed session tokens, cognito re-exchanged with the fresh id_token") {
            sut.idToken shouldBe "II2"
            sut.accessToken shouldBe "AA2"
            sut.cognitoToken shouldBe "COG2"
        }
        runBlocking {
            registry.verifyRequest(HttpMethod.Post, "/services/oauth2/token") { request ->
                withClue("refresh grant parameters travel in the query string") {
                    request.url.parameters["grant_type"] shouldBe "refresh_token"
                    request.url.parameters["refresh_token"] shouldBe "RRR0"
                    request.url.parameters["client_id"] shouldBe HonConstants.CLIENT_ID
                }
            }
        }
    }

    @Test fun `refresh adopts and persists a rotated refresh token`() {
        // Given: the server rotates the refresh token
        registry.given(
            { it.method == HttpMethod.Post && it.url.encodedPath == "/services/oauth2/token" },
            ResponseSpec("""{"id_token":"II2","access_token":"AA2","refresh_token":"RRR2"}"""),
        )
        registry.given(
            { it.method == HttpMethod.Post && it.url.encodedPath == "/auth/v1/login" },
            ResponseSpec("""{"cognitoUser":{"Token":"COG2"}}"""),
        )

        // When
        runBlocking { sut.refresh(mobileId, externalRefreshToken = "RRR0") }

        // Then: losing the rotated token would break every future refresh
        withClue("rotated refresh token must be persisted") {
            propertyRepository.storedValue(HonAuth.HON_REFRESH_TOKEN_KEY) shouldBe "RRR2"
        }
        sut.refreshToken shouldBe "RRR2"
    }

    @Test fun `refresh keeps the current refresh token when the server does not rotate it`() {
        // Given: persisted token from a previous session, response WITHOUT refresh_token
        propertyRepository.givenProperty(HonAuth.HON_REFRESH_TOKEN_KEY, "RRR0")
        registry.given(
            { it.method == HttpMethod.Post && it.url.encodedPath == "/services/oauth2/token" },
            ResponseSpec("""{"id_token":"II2","access_token":"AA2"}"""),
        )
        registry.given(
            { it.method == HttpMethod.Post && it.url.encodedPath == "/auth/v1/login" },
            ResponseSpec("""{"cognitoUser":{"Token":"COG2"}}"""),
        )

        // When
        runBlocking { sut.refresh(mobileId, externalRefreshToken = "RRR0") }

        // Then
        withClue("without rotation the stored token must stay valid") {
            propertyRepository.storedValue(HonAuth.HON_REFRESH_TOKEN_KEY) shouldBe "RRR0"
        }
        sut.refreshToken shouldBe "RRR0"
    }

    @Test fun `refresh fails on 4xx response`() {
        // Given: the grant is rejected (expired/consumed refresh token)
        registry.given(
            { it.method == HttpMethod.Post && it.url.encodedPath == "/services/oauth2/token" },
            ResponseSpec("""{"error":"invalid_grant"}""", status = HttpStatusCode.BadRequest),
        )

        // When
        val result = runBlocking { sut.refresh(mobileId, externalRefreshToken = "RRR0") }

        // Then: typed failure, session tokens untouched, nothing persisted
        result.shouldBeLeft().shouldBeInstanceOf<HonRefreshFailed>()
        withClue("failed refresh must not corrupt the session tokens") {
            sut.idToken shouldBe ""
            sut.accessToken shouldBe ""
        }
        propertyRepository.storedValue(HonAuth.HON_REFRESH_TOKEN_KEY) shouldBe null
    }

    /** The aura POST body is a wire contract: key order and encoding are exact. */
    private suspend fun verifyAuraCredentialPost() {
        registry.verifyRequest(HttpMethod.Post, "/s/sfsites/aura") { request ->
            withClue("aura endpoint query") {
                request.url.parameters["r"] shouldBe "3"
                request.url.parameters["other.LightningLoginCustom.login"] shouldBe "1"
            }
            val body = request.getBodyAsString()
            withClue("body must be urllib-quote encoded (no raw spaces, '+', quotes)") {
                body shouldMatch Regex("[A-Za-z0-9_.\\-~/%&=]+")
            }
            val parts = body.split("&")
            withClue("key order is part of the aura contract") {
                parts.map { it.substringBefore("=") } shouldBe
                    listOf("message", "aura.context", "aura.pageURI", "aura.token")
            }
            val message = mapper.readTree(decode(parts[0].substringAfter("=")))
            val action = message.at("/actions/0")
            action.get("id").asText() shouldBe "79;a"
            action.get("descriptor").asText() shouldBe
                "apex://LightningLoginCustomController/ACTION\$login"
            action.get("callingDescriptor").asText() shouldBe "markup://c:loginForm"
            action.at("/params/username").asText() shouldBe email
            action.at("/params/password").asText() shouldBe password
            withClue("startUrl: last startURL= occurrence, url-decoded") {
                action.at("/params/startUrl").asText() shouldBe
                    "/setup?source=ABC&System=IoT_Mobile_App&RegistrationSubChannel=hOn"
            }
            val context = mapper.readTree(decode(parts[1].substringAfter("=")))
            context.get("mode").asText() shouldBe "PROD"
            context.get("fwuid").asText() shouldBe "FW123"
            context.get("app").asText() shouldBe "siteforce:loginApp2"
            withClue("loaded must be sent back exactly as scraped") {
                context.get("loaded") shouldBe mapper.readTree("""{"APP@x":"L1"}""")
            }
            withClue("pageURI is the login page url without the auth host, as a JSON string") {
                decode(parts[2].substringAfter("=")) shouldBe
                    "\"/hop2?startURL=%2Fsetup%3Fsource%3DABC&System=IoT_Mobile_App&RegistrationSubChannel=hOn\""
            }
            parts[3] shouldBe "aura.token=null"
        }
    }

    private suspend fun verifyCognitoExchange() {
        registry.verifyRequest(HttpMethod.Post, "/auth/v1/login") { request ->
            withClue("cognito exchange carries the fresh id-token header") {
                request.headers["id-token"] shouldBe "III"
            }
            val body = mapper.readTree(request.getBodyAsString())
            body.get("mobileId").asText() shouldBe mobileId
            body.get("os").asText() shouldBe "android"
            body.get("appVersion").asText() shouldBe HonConstants.APP_VERSION
        }
    }

    private fun decode(s: String): String = URLDecoder.decode(s, Charsets.UTF_8)
}
