package org.agrfesta.sh.api.providers.hon

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.NullNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import org.agrfesta.sh.api.core.application.ports.outbounds.RandomGenerator
import org.agrfesta.sh.api.core.application.ports.outbounds.TimeProvider
import org.agrfesta.sh.api.core.application.ports.outbounds.settings.PropertyRepository
import java.io.IOException
import java.net.URLDecoder
import java.time.Duration
import java.time.Instant

/**
 * hOn login flow (Salesforce OAuth) and token state.
 *
 * Ported and adapted from the addhOn project (https://github.com/tis24dev/addhOn,
 * MIT License, Copyright (c) tis24dev) — `client/transport/{auth,oauth}.py` and
 * `examples/ktor-client/HonAuth.kt`. The flow:
 *
 *  1. GET  {auth}/services/oauth2/authorize/expid_Login  -> page with the login url
 *  2. two redirects followed by hand (the intermediate Location matters)
 *  3. GET  login page (aura) -> scrape fwuid + loaded
 *  4. POST {auth}/s/sfsites/aura (credentials, aura payload) -> redirect url
 *  5. GET  redirect -> href -> GET token page -> access/refresh/id token (fragment)
 *  6. POST {api}/auth/v1/login (id-token header + device payload) -> cognito-token
 *
 * Cookies MUST persist across the whole flow (Salesforce session): the two
 * HttpClients share the same CookiesStorage.
 *
 * Not thread-safe by itself: callers serialize authenticate/refresh (HonApiClient
 * does it with a Mutex).
 */
// The Salesforce login flow is inherently multi-step: one method per hop plus the
// exception-boundary and token-adoption helpers.
@Suppress("TooManyFunctions")
internal class HonAuth(
    private val config: HonConfiguration,
    private val propertyRepository: PropertyRepository,
    private val randomGenerator: RandomGenerator,
    private val timeProvider: TimeProvider,
    private val engine: HttpClientEngine,
) : AutoCloseable {
    var accessToken: String = ""
        private set
    var refreshToken: String = ""
        private set
    var idToken: String = ""
        private set

    /** Cognito token: together with [idToken], the header of EVERY authenticated API call. */
    var cognitoToken: String = ""
        private set

    private var fetchedAt: Instant = Instant.EPOCH

    val hasTokens: Boolean get() = cognitoToken.isNotEmpty() && idToken.isNotEmpty()

    /** The token lasts ~8h: past 7h it is worth renewing already. */
    val tokenExpiresSoon: Boolean
        get() = timeProvider.now() >= fetchedAt.plus(Duration.ofHours(TOKEN_RENEW_HOURS))

    private val json = HON_OBJECT_MAPPER
    private val cookies = LenientCookiesStorage()

    // "Normal" client (follows redirects) and a no-redirect twin for the two manual
    // hops of the flow: they share the cookies binding the Salesforce session.
    private val http = buildClient(followRedirects = true)
    private val httpNoRedirect = buildClient(followRedirects = false)

    /** Full login. On email-OTP 2FA returns [HonMfaRequired]. */
    suspend fun authenticate(mobileId: String): Either<HonAuthFailure, Unit> =
        runFlow { authenticateFlow(mobileId) }

    // Guard clauses: the SSO shortcut and the step failures read clearer as early returns.
    @Suppress("ReturnCount")
    private suspend fun authenticateFlow(mobileId: String): Either<HonAuthFailure, Unit> {
        clear()
        val authorizeUrl = HonAuthHelpers.buildAuthorizeUrl(
            authApi = config.authApiUrl,
            clientId = HonConstants.CLIENT_ID,
            nonce = randomGenerator.uuid().toString(),
        )
        val authorizePage = http.get(authorizeUrl) { ua() }.bodyAsText()
        val loginUrl = HonAuthHelpers.extractLoginUrl(config.authApiUrl, authorizePage)
            ?: return if ("oauth/done#access_token=" in authorizePage) {
                // Still-valid SSO session: the page already carries the token fragment.
                adopt(HonAuthHelpers.parseTokenFragment(authorizePage))
                    .getOrElse { return it.left() }
                apiAuth(mobileId)
            } else {
                HonLoginFlowBroken("authorize page carries no login url").left()
            }
        // Two manual redirects + the channel params the server expects.
        val r1 = manualRedirect(loginUrl)
        val r2 = manualRedirect(r1)
        val loginPageUrl = HonAuthHelpers.absolutize(
            config.authApiUrl,
            "$r2&System=IoT_Mobile_App&RegistrationSubChannel=hOn",
        )

        // Login page (aura): fwuid + loaded must be sent back identical in the POST.
        val pageText = http.get(loginPageUrl) { ua() }.bodyAsText()
        val fw = FWUID_RE.find(pageText)
            ?: return HonLoginFlowBroken("fwuid not found in login page").left()
        val loaded = json.readTree(fw.groupValues[2])
        val pageUrl = loginPageUrl.removePrefix(config.authApiUrl)

        val redirect = submitLogin(fw.groupValues[1], loaded, pageUrl)
            .getOrElse { return it.left() }
        fetchTokens(redirect).getOrElse { return it.left() }
        return apiAuth(mobileId)
    }

    /**
     * Token renewal via refresh_token grant (no full login). Honours refresh-token
     * rotation. [externalRefreshToken], when given, replaces the in-memory one
     * (bootstrap from persistence).
     */
    suspend fun refresh(
        mobileId: String,
        externalRefreshToken: String = "",
    ): Either<HonAuthFailure, Unit> = runFlow { refreshFlow(mobileId, externalRefreshToken) }

    private suspend fun refreshFlow(
        mobileId: String,
        externalRefreshToken: String,
    ): Either<HonAuthFailure, Unit> {
        if (externalRefreshToken.isNotEmpty()) refreshToken = externalRefreshToken
        // Grant parameters travel in the QUERY STRING: server contract.
        val resp = http.post(config.authApiUrl + "/services/oauth2/token") {
            ua()
            url {
                parameters.append("client_id", HonConstants.CLIENT_ID)
                parameters.append("refresh_token", refreshToken)
                parameters.append("grant_type", "refresh_token")
            }
        }
        if (resp.status.value >= HTTP_BAD_REQUEST) return HonRefreshFailed(resp.status.value).left()
        val data = json.readTree(resp.bodyAsText())
        idToken = data.at("/id_token").asText()
        accessToken = data.at("/access_token").asText()
        fetchedAt = timeProvider.now()
        data.at("/refresh_token").asText()
            .takeIf { it.isNotEmpty() }
            ?.let {
                // Rotation: losing the new token would break every future refresh.
                refreshToken = it
                propertyRepository.upsert(HON_REFRESH_TOKEN_KEY, it)
            }
        return apiAuth(mobileId)
    }

    fun clear() {
        accessToken = ""
        refreshToken = ""
        idToken = ""
        cognitoToken = ""
    }

    override fun close() {
        http.close()
        httpNoRedirect.close()
    }

    // ------------------------------------------------------------------ internals

    /**
     * Runs a login/refresh flow, converting the infrastructure exceptions it may throw
     * (ktor network/timeout, Jackson parse — both [IOException]) into a typed
     * [HonLoginFlowBroken], so the `Either` contract holds. [CancellationException] is
     * not an [IOException] and propagates, preserving cooperative cancellation.
     */
    private suspend fun runFlow(
        block: suspend () -> Either<HonAuthFailure, Unit>,
    ): Either<HonAuthFailure, Unit> =
        try {
            block()
        } catch (e: IOException) {
            HonLoginFlowBroken("hOn auth I/O error: ${e.message}").left()
        }

    private suspend fun manualRedirect(url: String): String {
        val resp = httpNoRedirect.get(HonAuthHelpers.absolutize(config.authApiUrl, url)) { ua() }
        return resp.headers[HttpHeaders.Location] ?: url
    }

    private suspend fun submitLogin(
        fwUid: String,
        loaded: JsonNode,
        pageUrl: String,
    ): Either<HonLoginFlowBroken, String> {
        // startURL: last occurrence, url-decoded, truncated at a leftover "%3D"
        // (double encoding) — as addhOn does.
        val startUrl = URLDecoder.decode(
            pageUrl.substringAfterLast("startURL="),
            Charsets.UTF_8,
        ).substringBefore("%3D")

        val action = json.createObjectNode().apply {
            put("id", "79;a")
            put("descriptor", "apex://LightningLoginCustomController/ACTION\$login")
            put("callingDescriptor", "markup://c:loginForm")
            set<ObjectNode>(
                "params",
                json.createObjectNode().apply {
                    put("username", config.email)
                    put("password", config.password)
                    put("startUrl", startUrl)
                },
            )
        }
        // Key order and the "k=quote(json)" encoding (urllib-style percent-encoding,
        // '/' excluded) are the contract the aura server expects.
        val payload = linkedMapOf<String, JsonNode>(
            "message" to json.createObjectNode().apply {
                set<ArrayNode>("actions", json.createArrayNode().add(action))
            },
            "aura.context" to json.createObjectNode().apply {
                put("mode", "PROD")
                put("fwuid", fwUid)
                put("app", "siteforce:loginApp2")
                set<JsonNode>("loaded", loaded)
                set<ArrayNode>("dn", json.createArrayNode())
                set<ObjectNode>("globals", json.createObjectNode())
                put("uad", false)
            },
            "aura.pageURI" to TextNode(pageUrl),
            "aura.token" to NullNode.instance,
        )
        val body = payload.entries.joinToString("&") { (k, v) ->
            "$k=${HonAuthHelpers.percentEncode(json.writeValueAsString(v))}"
        }

        val resp = http.post(
            config.authApiUrl + "/s/sfsites/aura?r=3&other.LightningLoginCustom.login=1",
        ) {
            ua()
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(body)
        }
        val result = json.readTree(resp.bodyAsText())
        val url = result.at("/events/0/attributes/values/url")
        if (url.isMissingNode) {
            // With rejected credentials the server still answers 200, WITHOUT `events`
            // and with the outcome in actions[0].returnValue (live-verified).
            val serverMessage = result.at("/actions/0/returnValue")
                .takeIf { it.isTextual }?.asText()
                ?: "no redirect url in aura response"
            return HonLoginFlowBroken("login submit: $serverMessage").left()
        }
        return url.asText().right()
    }

    // Guard clauses: missing href, OTP short-circuit, missing progressive href, success.
    @Suppress("ReturnCount")
    private suspend fun fetchTokens(redirectUrl: String): Either<HonAuthFailure, Unit> {
        val resp = http.get(HonAuthHelpers.absolutize(config.authApiUrl, redirectUrl)) { ua() }
        var href = HREF_RE.find(resp.bodyAsText())?.groupValues?.get(1)
            ?: return HonLoginFlowBroken("redirect href not found in token page").left()
        if ("ProgressiveLogin" in href) {
            val progText = http.get(HonAuthHelpers.absolutize(config.authApiUrl, href)) { ua() }
                .bodyAsText()
            // With email 2FA enabled THIS page is the OTP step: unattainable unattended.
            if (HonAuthHelpers.isProgressiveOtp(progText)) return HonMfaRequired.left()
            // Without OTP the interstitial's own href leads on to the token page (addhOn:
            // _HREF_RE_PROGRESSIVE, tolerant of an empty href, which the flow accepts) —
            // re-parsing the interstitial itself yields no tokens (live-verified 2026-07-17).
            href = HREF_RE_PROGRESSIVE.find(progText)?.groupValues?.get(1)
                ?: return HonLoginFlowBroken("progressive page: no follow-up href").left()
        }
        val tokenPage = http.get(HonAuthHelpers.absolutize(config.authApiUrl, href)) { ua() }
            .bodyAsText()
        return adopt(HonAuthHelpers.parseTokenFragment(tokenPage))
    }

    /**
     * Adopts freshly parsed tokens. Rejects an incomplete parse ([HonOAuthTokens.complete]
     * false, e.g. a token-page shape change) BEFORE persisting: otherwise an empty
     * `refreshToken` would overwrite the last known-good persisted one, downgrading every
     * future recovery to a full credential login.
     */
    private fun adopt(tokens: HonOAuthTokens): Either<HonAuthFailure, Unit> {
        if (!tokens.complete) {
            // Pinpointing the missing tokens turns a production 502 into a diagnosis.
            val missing = listOfNotNull(
                "access_token".takeIf { tokens.accessToken.isEmpty() },
                "refresh_token".takeIf { tokens.refreshToken.isEmpty() },
                "id_token".takeIf { tokens.idToken.isEmpty() },
            )
            return HonLoginFlowBroken("incomplete OAuth tokens (missing: ${missing.joinToString()})").left()
        }
        accessToken = tokens.accessToken
        refreshToken = tokens.refreshToken
        idToken = tokens.idToken
        fetchedAt = timeProvider.now()
        propertyRepository.upsert(HON_REFRESH_TOKEN_KEY, tokens.refreshToken)
        return Unit.right()
    }

    /** Exchanges the Salesforce id_token for the IoT APIs cognito-token. */
    private suspend fun apiAuth(mobileId: String): Either<HonAuthFailure, Unit> {
        val resp = http.post(config.apiUrl + "/auth/v1/login") {
            ua()
            header("id-token", idToken)
            contentType(ContentType.Application.Json)
            setBody(HonConstants.devicePayload(mobileId).toString())
        }
        val token = json.readTree(resp.bodyAsText()).at("/cognitoUser/Token").asText()
        if (token.isNullOrEmpty()) {
            return HonLoginFlowBroken(
                "api login: cognito token missing (status ${resp.status.value})",
            ).left()
        }
        cognitoToken = token
        return Unit.right()
    }

    private fun buildClient(followRedirects: Boolean) = HttpClient(engine) {
        this.followRedirects = followRedirects
        expectSuccess = false
        install(HttpCookies) { storage = cookies }
        install(HttpTimeout) {
            requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
            connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
        }
    }

    private fun HttpRequestBuilder.ua() {
        header(HttpHeaders.UserAgent, HonConstants.USER_AGENT)
    }

    companion object {
        const val HON_REFRESH_TOKEN_KEY = "HON_REFRESH_TOKEN"

        private val FWUID_RE = Regex("\"fwuid\":\"(.*?)\",\"loaded\":(\\{.*?})")
        private val HREF_RE = Regex("href\\s*=\\s*[\"'](.+?)[\"']")

        // ProgressiveLogin variant: `(.*?)` also matches an empty href, which the flow accepts.
        private val HREF_RE_PROGRESSIVE = Regex("href\\s*=\\s*[\"'](.*?)[\"']")
        private const val REQUEST_TIMEOUT_MILLIS = 30_000L
        private const val CONNECT_TIMEOUT_MILLIS = 10_000L
        private const val HTTP_BAD_REQUEST = 400
        private const val TOKEN_RENEW_HOURS = 7L
    }
}
