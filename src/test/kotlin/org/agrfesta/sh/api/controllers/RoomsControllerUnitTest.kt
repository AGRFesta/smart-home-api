package org.agrfesta.sh.api.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.shouldBe
import io.mockk.every
import org.agrfesta.sh.api.persistence.AssociationsDaoImpl
import org.agrfesta.sh.api.persistence.RoomsDaoImpl
import org.agrfesta.sh.api.persistence.repositories.DevicesRepository
import org.agrfesta.sh.api.persistence.repositories.AssociationsRepository
import org.agrfesta.sh.api.persistence.repositories.RoomsRepository
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
import java.util.*

@WebMvcTest(RoomsController::class)
@Import(RoomsDaoImpl::class)
@ActiveProfiles("test")
class RoomsControllerUnitTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired @MockkBean private val randomGenerator: RandomGenerator,
    @Autowired @MockkBean private val timeService: TimeService,
    @Autowired @MockkBean private val roomsRepository: RoomsRepository
) {

    init {
        every { randomGenerator.uuid() } returns UUID.randomUUID()
    }

    @Test fun `create() return 500 when persistence creation fails`() {
        val name = aRandomUniqueString()
        every { roomsRepository.save(any()) } throws Exception("room creation failure")

        val responseBody: String = mockMvc.perform(post("/rooms")
            .contentType("application/json")
            .content("""{"name": "$name"}"""))
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Unable to create Room '$name'!"
    }

}
