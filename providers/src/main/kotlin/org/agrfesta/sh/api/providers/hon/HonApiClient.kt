package org.agrfesta.sh.api.providers.hon

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttpConfig
import io.ktor.client.engine.okhttp.OkHttpEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.agrfesta.sh.api.core.application.ports.outbounds.RandomGenerator
import org.agrfesta.sh.api.core.application.ports.outbounds.TimeProvider
import org.agrfesta.sh.api.core.application.ports.outbounds.settings.PropertyRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.io.IOException
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Authenticated client of the hOn IoT APIs.
 *
 * Ported and adapted from the addhOn project (https://github.com/tis24dev/addhOn,
 * MIT License, Copyright (c) tis24dev) — `client/transport/api.py` and
 * `examples/ktor-client/HonApiClient.kt`.
 */
@Service
@ConditionalOnHon
// A transport client with one method per hOn endpoint plus the auth/retry helpers.
@Suppress("TooManyFunctions")
class HonApiClient(
    private val config: HonConfiguration,
    private val propertyRepository: PropertyRepository,
    private val randomGenerator: RandomGenerator,
    private val timeProvider: TimeProvider,
    @Autowired(required = false) private val engine: HttpClientEngine = OkHttpEngine(OkHttpConfig()),
) : AutoCloseable {

    private val auth = HonAuth(config, propertyRepository, randomGenerator, timeProvider, engine)
    private val json = HON_OBJECT_MAPPER
    private var mobileId: String = ""

    // Serializes (re)authentication: with refresh-token rotation, two concurrent refreshes
    // would consume the same token, the second getting invalid_grant and persisting a stale
    // one — breaking every future refresh. This is the Mutex HonAuth's KDoc promises.
    private val authMutex = Mutex()

    private val http = HttpClient(engine) {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
            connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
        }
    }

    /**
     * Appliance list of the account (offline ones included).
     * `POST /unified-api/v1/view/appliance-list` — the legacy
     * `GET /commands/v1/appliance` answers `[]` for every account: never use it.
     */
    suspend fun loadAppliances(): Either<HonFailure, List<JsonNode>> {
        val body = json.createObjectNode().put("deviceId", mobileId())
        return post("/unified-api/v1/view/appliance-list", body).map { result ->
            // Defensive: the cloud changes shapes without notice — fall back to empty.
            val appliances = result.at("/modules/applianceList/payload/appliances") as? ArrayNode
            appliances?.toList().orEmpty()
        }
    }

    /** Current state of the device (the polling call): payload of `GET /commands/v1/context`. */
    suspend fun loadAttributes(appliance: HonApplianceRef): Either<HonFailure, JsonNode> =
        get(
            "/commands/v1/context",
            mapOf(
                "macAddress" to appliance.macAddress,
                "applianceType" to appliance.applianceType,
                "category" to "CYCLE",
            ),
        ).map { it.at("/payload") }

    /** Model sheet (`payload.applianceModel` of `GET /commands/v1/appliance-model`). */
    suspend fun loadApplianceModel(appliance: HonApplianceRef): Either<HonFailure, JsonNode> =
        get(
            "/commands/v1/appliance-model",
            mapOf("code" to appliance.code, "macAddress" to appliance.macAddress),
        ).map { it.at("/payload/applianceModel") }

    /**
     * Command/program catalog of the device (payload of `GET /commands/v1/retrieve`,
     * `resultCode` stripped). Empty when resultCode != "0".
     */
    suspend fun loadCommands(appliance: HonApplianceRef): Either<HonFailure, JsonNode> {
        val params = buildMap {
            put("applianceType", appliance.applianceType)
            put("applianceModelId", appliance.applianceModelId)
            put("macAddress", appliance.macAddress)
            put("os", HonConstants.OS)
            put("appVersion", HonConstants.APP_VERSION)
            put("code", appliance.code)
            appliance.firmwareId?.let { put("firmwareId", it) }
            appliance.fwVersion?.let { put("fwVersion", it) }
            appliance.series?.let { put("series", it) }
        }
        return get("/commands/v1/retrieve", params).map { response ->
            val payload = response.at("/payload") as? ObjectNode
            if (payload?.get("resultCode")?.asText() != "0") {
                json.createObjectNode()
            } else {
                payload.deepCopy().apply { remove("resultCode") }
            }
        }
    }

    /**
     * Sends a command to the device (`POST /commands/v1/send`). True when the cloud
     * answers resultCode == "0".
     */
    suspend fun sendCommand(
        appliance: HonApplianceRef,
        command: String,
        parameters: Map<String, String>,
        programName: String = "",
    ): Either<HonFailure, Unit> {
        // No pre-auth here: post() -> request() authenticates once. A second
        // ensureAuthenticated() would double the credential submissions per call.
        // The cloud wants EXACTLY 3 fraction digits + "Z" (naive UTC).
        val timestamp = timeProvider.now().atOffset(ZoneOffset.UTC).format(TIMESTAMP_FMT) + "Z"
        val body = json.createObjectNode().apply {
            put("macAddress", appliance.macAddress)
            put("timestamp", timestamp)
            put("commandName", command)
            put("transactionId", "${appliance.macAddress}_$timestamp")
            set<JsonNode>("device", HonConstants.devicePayload(mobileId(), mobile = true))
            set<JsonNode>(
                "parameters",
                json.createObjectNode().apply { parameters.forEach { (k, v) -> put(k, v) } },
            )
            put("applianceType", appliance.applianceType)
            // programName is sent ONLY for startProgram, upper-cased.
            if (command == "startProgram") put("programName", programName.uppercase())
        }
        return post("/commands/v1/send", body).flatMap { response ->
            val resultCode = response.at("/payload/resultCode").asText()
            if (resultCode == "0") Unit.right() else HonCommandRejected.left()
        }
    }

    private suspend fun get(path: String, params: Map<String, String>): Either<HonFailure, JsonNode> =
        request {
            http.get(config.apiUrl + path) {
                authHeaders()
                url { params.forEach { (k, v) -> parameters.append(k, v) } }
            }
        }

    private suspend fun post(path: String, body: JsonNode): Either<HonFailure, JsonNode> =
        request {
            http.post(config.apiUrl + path) {
                authHeaders()
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
        }

    /**
     * Runs the request with valid tokens; on 401/403 refreshes the session and retries
     * — `exec` is re-evaluated so the headers pick up the fresh tokens. An auth failure
     * short-circuits (so a broken/MFA account surfaces the real [HonAuthFailure] and does
     * not hammer Salesforce with repeated logins); a 401/403 surviving the retry becomes
     * [HonUnauthorized]; any other non-2xx becomes [HonServerError]. Raw ktor network
     * exceptions (timeout, unknown host — all [IOException]) become [HonNetworkError], so
     * the `Either` contract is total; [CancellationException] is not an [IOException] and
     * propagates. Mirrors [HonAuth]'s `runFlow` boundary.
     */
    private suspend fun request(
        exec: suspend () -> HttpResponse,
    ): Either<HonFailure, JsonNode> =
        try {
            requestFlow(exec)
        } catch (e: IOException) {
            HonNetworkError(e.message).left()
        }

    // Guard clauses: auth short-circuit, 401 retry, unauthorized, server error, success.
    @Suppress("ReturnCount")
    private suspend fun requestFlow(
        exec: suspend () -> HttpResponse,
    ): Either<HonFailure, JsonNode> {
        ensureAuthenticated().getOrElse { return it.left() }
        var resp = exec()
        if (resp.status.value in RETRY_STATUSES) {
            forceReauthenticate().getOrElse { return it.left() }
            resp = exec()
        }
        if (resp.status.value in RETRY_STATUSES) return HonUnauthorized.left()
        if (resp.status.value >= HTTP_BAD_REQUEST) return HonServerError(resp.status.value).left()
        // bodyAsText() is suspend: keep it OUT of runCatching so a CancellationException is
        // not swallowed as a parse failure. Only the (non-suspend) parse is guarded.
        val bodyText = resp.bodyAsText()
        return runCatching { json.readTree(bodyText) }
            .fold({ it.right() }, { HonNonJsonResponse.left() })
    }

    override fun close() {
        http.close()
        auth.close()
    }

    // --------------------------------------------------------------- internals

    private fun mobileId(): String {
        if (mobileId.isEmpty()) {
            val persisted = propertyRepository.findEntry(HON_MOBILE_ID_KEY).getOrNull()
            mobileId = persisted?.value ?: randomGenerator.uuid().toString().also {
                propertyRepository.upsert(HON_MOBILE_ID_KEY, it)
            }
        }
        return mobileId
    }

    /** Ensures a fresh session, serialized by [authMutex] with a double-check. */
    private suspend fun ensureAuthenticated(): Either<HonFailure, Unit> {
        if (auth.hasTokens && !auth.tokenExpiresSoon) return Unit.right()
        return authMutex.withLock {
            if (auth.hasTokens && !auth.tokenExpiresSoon) Unit.right() else doReauthenticate()
        }
    }

    /** Forces a re-authentication (used after a 401), serialized by [authMutex]. */
    private suspend fun forceReauthenticate(): Either<HonFailure, Unit> =
        authMutex.withLock { doReauthenticate() }

    /** Renews the session: refresh from the persisted token, else full login. */
    private suspend fun doReauthenticate(): Either<HonFailure, Unit> {
        val persistedRefreshToken = propertyRepository.findEntry(HonAuth.HON_REFRESH_TOKEN_KEY)
            .getOrNull()?.value
        if (persistedRefreshToken != null && auth.refresh(mobileId(), persistedRefreshToken).isRight()) {
            return Unit.right()
        }
        return auth.authenticate(mobileId())
    }

    private fun HttpRequestBuilder.authHeaders() {
        header(HttpHeaders.UserAgent, HonConstants.USER_AGENT)
        header("cognito-token", auth.cognitoToken)
        header("id-token", auth.idToken)
    }

    companion object {
        const val HON_MOBILE_ID_KEY = "HON_MOBILE_ID"
        private const val REQUEST_TIMEOUT_MILLIS = 30_000L
        private const val CONNECT_TIMEOUT_MILLIS = 10_000L
        private const val HTTP_BAD_REQUEST = 400
        private val RETRY_STATUSES = setOf(401, 403)
        private val TIMESTAMP_FMT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")
    }
}
