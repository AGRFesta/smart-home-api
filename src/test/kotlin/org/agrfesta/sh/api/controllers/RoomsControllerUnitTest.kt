package org.agrfesta.sh.api.controllers

import arrow.core.left
import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.shouldBe
import io.mockk.every
import org.agrfesta.sh.api.persistence.PersistenceFailure
import org.agrfesta.sh.api.persistence.jdbc.dao.RoomsDaoJdbcImpl
import org.agrfesta.sh.api.persistence.jdbc.repositories.RoomsJdbcRepository
import org.agrfesta.sh.api.utils.RandomGenerator
import org.agrfesta.sh.api.utils.TimeService
import org.agrfesta.test.mothers.aRandomUniqueString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.*

@WebMvcTest(RoomsController::class)
@Import(RoomsDaoJdbcImpl::class)
@ActiveProfiles("test")
class RoomsControllerUnitTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired @MockkBean private val randomGenerator: RandomGenerator,
    @Autowired @MockkBean private val timeService: TimeService,
    @Autowired @MockkBean private val roomsRepository: RoomsJdbcRepository
) {

    init {
        every { randomGenerator.uuid() } returns UUID.randomUUID()
        every { timeService.now() } returns Instant.now()
    }

    @Test fun `create() return 500 when persistence creation fails`() {
        val name = aRandomUniqueString()
        every { roomsRepository.persist(any()) } returns PersistenceFailure(Exception("room creation failure")).left()

        val responseBody: String = mockMvc.perform(post("/rooms")
            .contentType("application/json")
            .content("""{"name": "$name"}"""))
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Unable to create Room '$name'!"
    }

}
