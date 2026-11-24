package org.agrfesta.sh.api.providers.netatmo

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.maps.shouldContainAll
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.Parameters
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import java.math.BigDecimal
import org.agrfesta.test.mothers.aRandomNonNegativeInt
import org.agrfesta.test.mothers.aRandomUniqueString
import org.agrfesta.test.mothers.anUrl
import org.agrfesta.sh.api.providers.netatmo.ConditionalOnNetatmo
import org.springframework.stereotype.Service

@Service
@ConditionalOnNetatmo
class NetatmoClientAsserter(
    private val config: NetatmoConfiguration = NetatmoConfiguration(
        baseUrl = anUrl(),
        clientSecret = aRandomUniqueString(),
        clientId = aRandomUniqueString(),
        homeId = aRandomUniqueString(),
        roomId = aRandomUniqueString()
    ),
    private val registry: BehaviorRegistry = BehaviorRegistry(),
    private val mapper: ObjectMapper = NETATMO_OBJECT_MAPPER
) {

    fun clear() { registry.clear() }

    fun givenTokenFetchResponse(response: NetatmoRefreshTokenResponse) {
        registry.given(
            matcher = { req -> req.method == Post && req.url.encodedPath == "/oauth2/token" },
            responses = arrayOf(
                ResponseSpec(
                    content = mapper.writeValueAsString(response),
                    status = OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            )
        )
    }
    fun givenTokenFetchFailure(errorMessage: String) {
        val response = """{"error":"$errorMessage"}"""
        registry.given(
            matcher = { req -> req.method == Post && req.url.encodedPath == "/oauth2/token" },
            responses = arrayOf(
                ResponseSpec(
                    content = response,
                    status = BadRequest,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            )
        )
    }
    suspend fun verifyTokenFetchRequest(prevRefreshToken: String) {
        val expectedParams = mapOf(
            "grant_type" to "refresh_token",
            "refresh_token" to prevRefreshToken,
            "client_id" to config.clientId,
            "client_secret" to config.clientSecret
        )
        registry.verifyRequest(Post, "/oauth2/token") { request ->
            val parsedBody = parseFormBody(request.getBodyAsString())
            parsedBody shouldContainAll expectedParams
        }
    }

    fun givenHomeDataFetchResponse(data: JsonNode) {
        registry.given(
            matcher = { req -> req.method == Get && req.url.encodedPath == "/api/homesdata" },
            responses = arrayOf(
                ResponseSpec(
                    content = data.toString(),
                    status = OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            )
        )
    }
    fun givenHomeDataFetchFailure(
        accessToken: String? = null,
        errorMessage: String = aRandomUniqueString(),
        status: HttpStatusCode = BadRequest,
        errorCode: Int = aRandomNonNegativeInt()
    ) {
        val json = """
            {
                "error": {
                    "code": $errorCode,
                    "message": "$errorMessage"
                }
            }
        """.trimIndent()
        val matcher: (HttpRequestData) -> Boolean = accessToken?.let {
            { req ->
                req.method == Get
                        && req.url.encodedPath == "/api/homesdata"
                        && req.headers[Authorization] == "Bearer $accessToken"
            }
        } ?: { req -> req.method == Get && req.url.encodedPath == "/api/homesdata" }
        registry.given(
            matcher = matcher,
            responses = arrayOf(
                ResponseSpec(
                    content = json,
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            )
        )
    }

    /** Netatmo answers [status] to every `/api/homesdata` request, with a plain-text [content]. */
    fun givenHomeDataFetchTextResponse(status: HttpStatusCode, content: String) {
        registry.given(
            matcher = { req -> req.method == Get && req.url.encodedPath == "/api/homesdata" },
            responses = arrayOf(
                ResponseSpec(
                    content = content,
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, "text/plain")
                )
            )
        )
    }

    /** Every `/api/homesdata` request ends with [failure] thrown by the engine, before any response. */
    fun givenHomeDataFetchError(failure: Throwable) {
        registry.given(
            matcher = { req -> req.method == Get && req.url.encodedPath == "/api/homesdata" },
            responses = arrayOf(ResponseSpec(content = "", failure = failure))
        )
    }

    suspend fun verifyHomeDataFetchRequest(accessToken: String, homeId: String? = null) {
        registry.verifyRequest(Get, "/api/homesdata") { request ->
            request.headers[Authorization] shouldBe "Bearer $accessToken"
            homeId?.let {
                request.url.parameters.asMap() shouldContainExactly mapOf("home_id" to homeId)
                request.url.parameters.contains("home_id", homeId).shouldBeTrue()
            }
        }
    }

    fun givenHomeStatusFetchResponse(homeStatus: NetatmoHomeStatus) {
        val expectedResponse = """
            {
              "status": "ok",
              "time_server": 1761489635,
              "body": {
                "home": ${mapper.writeValueAsString(homeStatus)}
              }
            }
        """.trimIndent()
        givenHomeStatusFetchResponse(expectedResponse)
    }
    fun givenHomeStatusFetchResponse(expectedResponse: String) {
        registry.given(
            matcher = { req -> req.method == Get && req.url.encodedPath == "/api/homestatus" },
            responses = arrayOf(
                ResponseSpec(
                    content = expectedResponse,
                    status = OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            )
        )
    }
    fun givenHomeStatusFetchFailure(
        errorMessage: String = aRandomUniqueString(),
        status: HttpStatusCode = BadRequest,
        errorCode: Int = aRandomNonNegativeInt()
    ) {
        val json = """
            {
                "error": {
                    "code": $errorCode,
                    "message": "$errorMessage"
                }
            }
        """.trimIndent()
        registry.given(
            matcher = { req -> req.method == Get && req.url.encodedPath == "/api/homestatus" },
            responses = arrayOf(
                ResponseSpec(
                    content = json,
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            )
        )
    }
    /** Every `/api/homestatus` request ends with [failure] thrown by the engine, before any response. */
    fun givenHomeStatusFetchError(failure: Throwable) {
        registry.given(
            matcher = { req -> req.method == Get && req.url.encodedPath == "/api/homestatus" },
            responses = arrayOf(ResponseSpec(content = "", failure = failure))
        )
    }
    suspend fun verifyHomeStatusFetchRequest(accessToken: String, homeId: String) {
        registry.verifyRequest(Get, "/api/homestatus") { request ->
            request.headers[Authorization] shouldBe "Bearer $accessToken"
            request.url.parameters.asMap() shouldContainExactly mapOf("home_id" to homeId)
            request.url.parameters.contains("home_id", homeId).shouldBeTrue()
        }
    }
    suspend fun verifyNoHomeStatusFetchRequest() {
        registry.verifyRequest(Get, "/api/homestatus", times = 0)
    }

    /** Netatmo answers [response] to the next [times] `/api/setstate` requests. */
    fun givenSetStatusResponse(response: JsonNode, times: Int = 1) {
        registry.given(
            matcher = { req -> req.method == Post && req.url.encodedPath == "/api/setstate" },
            responses = Array(times) {
                ResponseSpec(
                    content = response.toString(),
                    status = OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        )
    }
    fun givenSetStatusFailure(
        errorMessage: String = aRandomUniqueString(),
        status: HttpStatusCode = BadRequest,
        errorCode: Int = aRandomNonNegativeInt()
    ) {
        val json = """
            {
                "error": {
                    "code": $errorCode,
                    "message": "$errorMessage"
                }
            }
        """.trimIndent()
        registry.given(
            matcher = { req -> req.method == Post && req.url.encodedPath == "/api/setstate" },
            responses = arrayOf(
                ResponseSpec(
                    content = json,
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            )
        )
    }
    /** Netatmo answers [status] to every `/api/setstate` request, with a plain-text [content]. */
    fun givenSetStatusTextFailure(status: HttpStatusCode, content: String) {
        registry.given(
            matcher = { req -> req.method == Post && req.url.encodedPath == "/api/setstate" },
            responses = arrayOf(
                ResponseSpec(
                    content = content,
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, "text/plain")
                )
            )
        )
    }
    /** Every `/api/setstate` request ends with [failure] thrown by the engine, before any response. */
    fun givenSetStatusError(failure: Throwable) {
        registry.given(
            matcher = { req -> req.method == Post && req.url.encodedPath == "/api/setstate" },
            responses = arrayOf(ResponseSpec(content = "", failure = failure))
        )
    }
    /** Netatmo answers [status] only to the `/api/setstate` requests that set [setPointTemperature]. */
    fun givenSetStatusFailureFor(
        setPointTemperature: BigDecimal,
        status: HttpStatusCode,
        content: String = """{"error":{"code":${aRandomNonNegativeInt()},"message":"${aRandomUniqueString()}"}}"""
    ) {
        registry.given(
            matcher = { req ->
                req.method == Post && req.url.encodedPath == "/api/setstate" &&
                    (req.body as? TextContent)?.text
                        ?.let { mapper.readTree(it).at("/home/rooms/0/therm_setpoint_temperature").decimalValue() }
                        ?.compareTo(setPointTemperature) == 0
            },
            responses = arrayOf(
                ResponseSpec(
                    content = content,
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            )
        )
    }
    suspend fun verifySetStatusRequest(accessToken: String, status: NetatmoHomeStatusChange) {
        val expectedRequest: JsonNode = mapper.valueToTree(NetatmoStatusChangeRequest(status))
        registry.verifyRequest(Post, "/api/setstate") { request ->
            request.headers[Authorization] shouldBe "Bearer $accessToken"
            val requestBody = request.body.toByteArray().decodeToString()
            requestBody shouldEqualJson expectedRequest.toString()
        }
    }
    suspend fun verifyNoSetStatusRequest() {
        registry.verifyRequest(Post, "/api/setstate", times = 0)
    }
    /** The room setpoint of every `/api/setstate` request, in the order they were sent, without trailing zeros. */
    suspend fun setPointTemperaturesSent(): List<BigDecimal> =
        registry.recordedRequests(Post, "/api/setstate").map { request ->
            mapper.readTree(request.getBodyAsString())
                .at("/home/rooms/0/therm_setpoint_temperature").decimalValue().stripTrailingZeros()
        }


    private fun parseFormBody(body: String): Map<String, String> {
        return body.split("&").associate {
            val (key, value) = it.split("=")
            key to value
        }
    }

    private fun Parameters.asMap() = entries().associate { it.key to it.value.firstOrNull() }

}
