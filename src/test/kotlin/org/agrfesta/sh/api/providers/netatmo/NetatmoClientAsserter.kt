package org.agrfesta.sh.api.providers.netatmo

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.maps.shouldContainAll
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.Parameters
import io.ktor.http.headersOf
import org.agrfesta.test.mothers.aRandomUniqueString
import org.agrfesta.test.mothers.anUrl
import org.springframework.stereotype.Service

@Service
class NetatmoClientAsserter(
    private val config: NetatmoConfiguration = NetatmoConfiguration(
        baseUrl = anUrl(),
        clientSecret = aRandomUniqueString(),
        clientId = aRandomUniqueString(),
        homeId = aRandomUniqueString(),
        roomId = aRandomUniqueString()
    ),
    private val registry: BehaviorRegistry = BehaviorRegistry(),
    private val mapper: ObjectMapper = jacksonObjectMapper()
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
                    content = mapper.writeValueAsString(response),
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
    fun givenHomeDataFetchFailure() {
        val expectedResponse = """
            {
              "error": {
                "code": 3,
                "message": "Bad request"
              }
            }
        """.trimIndent()
        registry.given(
            matcher = { req -> req.method == Get && req.url.encodedPath == "/api/homesdata" },
            responses = arrayOf(
                ResponseSpec(
                    content = expectedResponse,
                    status = BadRequest,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            )
        )
    }
    suspend fun verifyHomeDataFetchRequest(accessToken: String, homeId: String? = null) {
        registry.verifyRequest(Get, "/api/homesdata") { request ->
            request.headers[Authorization] shouldBe "Bearer $accessToken"
            homeId?.let {
                request.url.parameters.asMap() shouldContainExactly mapOf("homeId" to homeId)
                request.url.parameters.contains("homeId", homeId).shouldBeTrue()
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
    fun givenHomeStatusFetchFailure() {
        val expectedResponse = """
            {
              "error": {
                "code": 3,
                "message": "Bad request"
              }
            }
        """.trimIndent()
        registry.given(
            matcher = { req -> req.method == Get && req.url.encodedPath == "/api/homestatus" },
            responses = arrayOf(
                ResponseSpec(
                    content = expectedResponse,
                    status = BadRequest,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            )
        )
    }
    suspend fun verifyHomeStatusFetchRequest(accessToken: String, homeId: String) {
        registry.verifyRequest(Get, "/api/homestatus") { request ->
            request.headers[Authorization] shouldBe "Bearer $accessToken"
            request.url.parameters.asMap() shouldContainExactly mapOf("homeId" to homeId)
            request.url.parameters.contains("homeId", homeId).shouldBeTrue()
        }
    }
    suspend fun verifyNoHomeStatusFetchRequest() {
        registry.verifyRequest(Get, "/api/homestatus", times = 0)
    }

    fun givenSetStatusResponse(response: JsonNode) {
        registry.given(
            matcher = { req -> req.method == Post && req.url.encodedPath == "/api/setstate" },
            responses = arrayOf(
                ResponseSpec(
                    content = response.toString(),
                    status = OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            )
        )
    }
    fun givenSetStatusFailure() {
        val expectedResponse = """
            {
              "error": {
                "code": 3,
                "message": "Bad request"
              }
            }
        """.trimIndent()
        registry.given(
            matcher = { req -> req.method == Post && req.url.encodedPath == "/api/setstate" },
            responses = arrayOf(
                ResponseSpec(
                    content = expectedResponse,
                    status = BadRequest,
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


    private fun parseFormBody(body: String): Map<String, String> {
        return body.split("&").associate {
            val (key, value) = it.split("=")
            key to value
        }
    }

    private fun Parameters.asMap() = entries().associate { it.key to it.value.firstOrNull() }

}
