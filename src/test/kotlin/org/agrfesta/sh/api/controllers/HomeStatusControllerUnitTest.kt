package org.agrfesta.sh.api.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.shouldBe
import io.mockk.every
import org.agrfesta.sh.api.persistence.jdbc.dao.AreasDaoJdbcImpl
import org.agrfesta.sh.api.persistence.jdbc.dao.AreasWithDevicesDaoJdbcImpl
import org.agrfesta.sh.api.persistence.jdbc.repositories.AreasJdbcRepository
import org.agrfesta.sh.api.persistence.jdbc.repositories.AreasWithDevicesJdbcRepository
import org.agrfesta.sh.api.security.SecurityConfig
import org.agrfesta.sh.api.services.AreasService
import org.agrfesta.sh.api.utils.Cache
import org.agrfesta.sh.api.utils.RandomGenerator
import org.agrfesta.sh.api.utils.SmartCache
import org.agrfesta.test.mothers.aRandomUniqueString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(HomeStatusController::class)
@ActiveProfiles("test")
@Import(
    AreasService::class,
    AreasDaoJdbcImpl::class,
    AreasWithDevicesDaoJdbcImpl::class,
    SmartCache::class,
    SecurityConfig::class
)
class HomeStatusControllerUnitTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired @MockkBean private val areasWithDevicesJdbcRepo: AreasWithDevicesJdbcRepository,
    @Autowired @MockkBean private val areasJdbcRepository: AreasJdbcRepository,
    @Autowired @MockkBean private val cache: Cache,
    @Autowired @MockkBean private val randomGenerator: RandomGenerator
) {

    @Test fun `getHomeStatus() return 401 when auth is missing`() {
        val responseBody: String = mockMvc.perform(
            get("/home/status"))
            .andExpect(status().isUnauthorized)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Missing Authorization header"
    }

    @Test fun `getHomeStatus() return 401 when token is empty`() {
        val responseBody: String = mockMvc.perform(
            get("/home/status")
                .header("Authorization", "Bearer "))
            .andExpect(status().isUnauthorized)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Empty token"
    }

    @Test fun `getHomeStatus() return 401 when token is invalid`() {
        val responseBody: String = mockMvc.perform(
            get("/home/status")
                .header("Authorization", "Bearer ${aRandomUniqueString()}"))
            .andExpect(status().isUnauthorized)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Invalid token"
    }

    @Test fun `getHomeStatus() returns 500 when is unable to fetch areas from db`() {
        val failure = Exception("area fetching failure")
        every { areasWithDevicesJdbcRepo.getAll() } throws failure

        val resultContent: String = mockMvc.perform(
            get("/home/status")
                .authenticated())
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(resultContent, MessageResponse::class.java)
        response.message shouldBe "Unable to fetch areas!"
    }

}
