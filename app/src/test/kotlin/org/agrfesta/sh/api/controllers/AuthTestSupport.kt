package org.agrfesta.sh.api.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.matchers.shouldBe
import org.agrfesta.test.mothers.aRandomUniqueString
import org.junit.jupiter.api.DynamicTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class AuthTestSupport(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper
) {

    private fun testAuth(
        requestBuilder: () -> MockHttpServletRequestBuilder,
        expectedMessage: String,
        enricher: (MockHttpServletRequestBuilder) -> MockHttpServletRequestBuilder = { it }
    ) {
        val responseBody: String = mockMvc.perform(enricher(requestBuilder()))
            .andExpect(status().isUnauthorized)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe expectedMessage
    }

    private fun testMissingHeader(requestBuilder: () -> MockHttpServletRequestBuilder, expectedMessage: String) {
        testAuth(requestBuilder, expectedMessage) {
            it // Same builder -> no auth header
        }
    }
    private fun testEmptyToken(requestBuilder: () -> MockHttpServletRequestBuilder, expectedMessage: String) {
        testAuth(requestBuilder, expectedMessage) {
            it.header("Authorization", "Bearer ")
        }
    }
    private fun testInvalidToken(requestBuilder: () -> MockHttpServletRequestBuilder, expectedMessage: String) {
        testAuth(requestBuilder, expectedMessage) {
            it.header("Authorization", "Bearer ${aRandomUniqueString()}")
        }
    }

    fun dynamicTestsBy(requestBuilder: () -> MockHttpServletRequestBuilder) = listOf(

        DynamicTest.dynamicTest("return 401 when authorization header is missing") {
            testMissingHeader(requestBuilder, "Missing Authorization header")
        },
        DynamicTest.dynamicTest("return 401 when token is empty") {
            testEmptyToken(requestBuilder, "Empty token")
        },
        DynamicTest.dynamicTest("return 401 when token is invalid") {
            testInvalidToken(requestBuilder, "Invalid token")
        }
    )
}
