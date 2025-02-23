package org.agrfesta.sh.api.providers.netatmo

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.maps.shouldContainAll
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.agrfesta.test.mothers.aJsonNode
import org.agrfesta.test.mothers.aRandomUniqueString
import org.agrfesta.test.mothers.anUrl
import org.junit.jupiter.api.Test

class NetatmoClientTest {
    private val mapper = jacksonObjectMapper()
    private val config = NetatmoConfiguration(
        baseUrl = anUrl(),
        clientSecret = aRandomUniqueString(),
        clientId = aRandomUniqueString(),
        homeId = aRandomUniqueString()
    )

    @Test fun `refreshToken() handle ok response`() {
        val prevRefreshToken = aRandomUniqueString()
        val expectedParams = mapOf(
            "grant_type" to "refresh_token",
            "refresh_token" to prevRefreshToken,
            "client_id" to config.clientId,
            "client_secret" to config.clientSecret
        )
        val expectedResponse = NetatmoRefreshTokenResponse(
            accessToken = aRandomUniqueString(),
            refreshToken = aRandomUniqueString()
        )
        runBlocking {
            val engine = MockEngine { request ->
                request.method shouldBe HttpMethod.Post
                request.url.toString() shouldBe "${config.baseUrl}/oauth2/token"
                //request.headers[HttpHeaders.ContentType] shouldBe ContentType.Application.FormUrlEncoded.toString()
                val requestBody = request.body.toByteArray().decodeToString()
                val parsedBody = parseFormBody(requestBody)
                parsedBody shouldContainAll expectedParams
                respond(
                    content = mapper.writeValueAsString(expectedResponse),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
            val client = NetatmoClient(config, mapper, engine)

            val result = client.refreshToken(prevRefreshToken)

            result shouldBeRight expectedResponse
        }
    }

    @Test fun `refreshToken() handle error response`() {
        val prevRefreshToken = aRandomUniqueString()
        val errorMessage = aRandomUniqueString()
        val response = """{"error":"$errorMessage"}"""
        runBlocking {
            val engine = MockEngine { request ->
                request.method shouldBe HttpMethod.Post
                request.url.toString() shouldBe "${config.baseUrl}/oauth2/token"
                respond(
                    content = mapper.writeValueAsString(response),
                    status = HttpStatusCode.BadRequest,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
            val client = NetatmoClient(config, mapper, engine)

            val result = client.refreshToken(prevRefreshToken)

            val failure = result.shouldBeLeft()
            failure.exception.shouldBeInstanceOf<ClientRequestException>()
        }
    }

    @Test fun `getHomesData() Returns as right, json response, when it is ok`() {
        val accessToken = aRandomUniqueString()
        val expectedResponse = aJsonNode()
        runBlocking {
            val engine = MockEngine { request ->
                request.method shouldBe HttpMethod.Get
                request.url.toString() shouldBe "${config.baseUrl}/api/homesdata"
                request.headers[HttpHeaders.Authorization] shouldBe "Bearer $accessToken"
                respond(
                    content = expectedResponse.toString(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
            val client = NetatmoClient(config, mapper, engine)

            val result = client.getHomesData(accessToken)

            result shouldBeRight expectedResponse
        }
    }

    @Test fun `getHomesData() Returns as right, json response, by homeId when it is ok`() {
        val homeId = aRandomUniqueString()
        val accessToken = aRandomUniqueString()
        val expectedResponse = aJsonNode()
        runBlocking {
            val engine = MockEngine { request ->
                request.method shouldBe HttpMethod.Get
                "https://${request.url.host}" shouldBe config.baseUrl
                request.url.encodedPath shouldBe "/api/homesdata"
                request.url.parameters.asMap() shouldContainExactly mapOf("homeId" to homeId)
                request.url.parameters.contains("homeId", homeId).shouldBeTrue()
                request.headers[HttpHeaders.Authorization] shouldBe "Bearer $accessToken"
                respond(
                    content = expectedResponse.toString(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
            val client = NetatmoClient(config, mapper, engine)

            val result = client.getHomesData(accessToken, homeId)

            result shouldBeRight expectedResponse
        }
    }

    @Test fun `getHomeStatus() Returns as right, json response, when it is ok`() {
        val homeId = aRandomUniqueString()
        val accessToken = aRandomUniqueString()
        val expectedResponse = aJsonNode()
        runBlocking {
            val engine = MockEngine { request ->
                request.method shouldBe HttpMethod.Get
                "https://${request.url.host}" shouldBe config.baseUrl
                request.url.encodedPath shouldBe "/api/homestatus"
                request.url.parameters.asMap() shouldContainExactly mapOf("homeId" to homeId)
                request.url.parameters.contains("homeId", homeId).shouldBeTrue()
                request.headers[HttpHeaders.Authorization] shouldBe "Bearer $accessToken"
                respond(
                    content = expectedResponse.toString(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
            val client = NetatmoClient(config, mapper, engine)

            val result = client.getHomeStatus(accessToken, homeId)

            result shouldBeRight expectedResponse
        }
    }

    @Test fun `setState() Returns as right, json response, when it is ok`() {
        val expectedRequest = aJsonNode()
        val accessToken = aRandomUniqueString()
        val expectedResponse = aJsonNode()
        runBlocking {
            val engine = MockEngine { request ->
                request.method shouldBe HttpMethod.Post
                "https://${request.url.host}" shouldBe config.baseUrl
                request.url.encodedPath shouldBe "/api/setstate"
                request.headers[HttpHeaders.Authorization] shouldBe "Bearer $accessToken"
                val requestBody = request.body.toByteArray().decodeToString()
                val parsedBody = mapper.readTree(requestBody)
                parsedBody shouldBe expectedRequest
                respond(
                    content = expectedResponse.toString(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
            val client = NetatmoClient(config, mapper, engine)

            val result = client.setState(accessToken, expectedRequest)

            result shouldBeRight NetatmoSetStatusSuccess
        }
    }

    private fun parseFormBody(body: String): Map<String, String> {
        return body.split("&").associate {
            val (key, value) = it.split("=")
            key to value
        }
    }

    private fun Parameters.asMap() = entries().associate { it.key to it.value.firstOrNull() }

}
