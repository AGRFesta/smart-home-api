package org.agrfesta.sh.api.providers.netatmo

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttpConfig
import io.ktor.client.engine.okhttp.OkHttpEngine
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.serialization.jackson.jackson
import org.agrfesta.sh.api.domain.failures.Failure
import org.agrfesta.sh.api.domain.failures.KtorRequestFailure
import org.agrfesta.sh.api.providers.netatmo.NetatmoService.Companion.ACCESS_TOKEN_CACHE_KEY
import org.agrfesta.sh.api.providers.netatmo.NetatmoService.Companion.REFRESH_TOKEN_CACHE_KEY
import org.agrfesta.sh.api.services.PersistedCacheService
import org.agrfesta.sh.api.services.onLeftLogOn
import org.agrfesta.sh.api.utils.Cache
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.agrfesta.sh.api.utils.toDetailedString
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class NetatmoClient(
    private val config: NetatmoConfiguration,
    private val cache: Cache,
    private val cacheService: PersistedCacheService,
    private val mapper: ObjectMapper,
    @Autowired(required = false) netatmoClientEngine: HttpClientEngine = OkHttpEngine(OkHttpConfig())
) {
    private val logger by LoggerDelegate()
    private val client = HttpClient(netatmoClientEngine) {
        expectSuccess = true
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
        }
        install(ContentNegotiation) {
            jackson()
        }
    }

    /**
     * Starting from a never used refresh token we can fetch both a new refresh token and an access token to use for
     * further api calls, once the access token expires we can refresh it again using this method.
     *
     * @param prevRefreshToken previous unused refresh token.
     */
    suspend fun refreshToken(prevRefreshToken: String): Either<NetatmoAuthFailure, NetatmoRefreshTokenResponse> = try {
            val response = client.submitForm(
                url = "${config.baseUrl}/oauth2/token",
                formParameters = Parameters.build {
                    append("grant_type", "refresh_token")
                    append("refresh_token", prevRefreshToken)
                    append("client_id", config.clientId)
                    append("client_secret", config.clientSecret)
                }
            )
            val body = response.body<String>()
            mapper.readValue<NetatmoRefreshTokenResponse>(body).right()
        } catch (e: Exception) {
            NetatmoAuthFailure(e).left()
        }

    /**
     * This endpoint permits to retrieve the actual topology and static information of all devices present into a user
     * account. It is possible to specify a [homeId] to focus on one home.
     */
    suspend fun getHomesData(homeId: String? = null): Either<Failure, JsonNode> {
        return getToken().flatMap { token ->
            try {
                val content = client.get("${config.baseUrl}/api/homesdata") {
                    homeId?.let { parameter("homeId", it) }
                    headers { append(HttpHeaders.Authorization, "Bearer $token") }
                }.bodyAsText()
                mapper.readTree(content).right()
            } catch (e: ClientRequestException) {
                KtorRequestFailure(
                    failureStatusCode = e.response.status,
                    body = e.response.bodyAsText()
                ).left()
            }
        }
    }

    /**
     * This endpoint permits to retrieve the actual status of all devices present into a specific home.
     */
    suspend fun getHomeStatus(homeId: String): Either<Failure, NetatmoHomeStatus> =
        getToken().flatMap { token ->
            try {
                val content = client.get("${config.baseUrl}/api/homestatus") {
                    parameter("homeId", homeId)
                    headers { append(HttpHeaders.Authorization, "Bearer $token") }
                }.bodyAsText()
                try {
                    mapper.readTree(content).at("/body/home").let {
                        mapper.treeToValue(it, NetatmoHomeStatus::class.java)
                    }?.right() ?: NetatmoContractBreak("Unexpected home status response", content).left()
                } catch (e: Exception) {
                    NetatmoContractBreak("Unexpected home status response", content).left()
                }
            } catch (e: ClientRequestException) {
                KtorRequestFailure(
                    failureStatusCode = e.response.status,
                    body = e.response.bodyAsText()
                ).left()
            }
        }

    /**
     * This endpoint permits to modify the actual status of devices present into a specific home.
     * Parameters are sent through the request body using a json format. The json contains the same format as the body
     * of the answer of a [getHomeStatus] method.
     * For heating modules, the state can be controlled at the room level.
     * For other modules, the state is controlled at the module level.
     */
    suspend fun setState(newState: NetatmoHomeStatus): Either<Failure, NetatmoSetStatusSuccess> {
        return getToken().flatMap { token ->
            try {
                client.post("${config.baseUrl}/api/setstate") {
                    header(HttpHeaders.Accept, ContentType.Application.Json)
                    header(HttpHeaders.ContentType, ContentType.Application.Json)
                    setBody(newState)
                    headers {
                        append(HttpHeaders.Authorization, "Bearer $token")
                    }
                }
                NetatmoSetStatusSuccess.right()
            } catch (e: ClientRequestException) {
                KtorRequestFailure(
                    failureStatusCode = e.response.status,
                    body = e.response.bodyAsText()
                ).left()
            }
        }
    }

    private suspend fun getToken(): Either<Failure, String> =
        cache.get(ACCESS_TOKEN_CACHE_KEY).fold(
            ifLeft = { fetchAndCacheNewToken() },
            ifRight = { it.right() }
        )

    private suspend fun fetchAndCacheNewToken(): Either<Failure, String> =
        cacheService.getEntry(REFRESH_TOKEN_CACHE_KEY)
            .flatMap { refreshToken(it.value) }
            .map { refreshResp ->
                try {
                    cache.set(ACCESS_TOKEN_CACHE_KEY, refreshResp.accessToken)
                } catch (e: Exception) {
                    logger.error("Unable to refresh Netatmo token in cache. ${e.toDetailedString()}")
                }
                cacheService.upsert(REFRESH_TOKEN_CACHE_KEY, refreshResp.refreshToken)
                    .onLeftLogOn(logger)
                refreshResp.accessToken
            }

}
