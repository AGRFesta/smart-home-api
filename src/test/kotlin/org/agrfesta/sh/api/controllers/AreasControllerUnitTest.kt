package org.agrfesta.sh.api.controllers

import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.slot
import java.time.Instant
import java.util.*
import org.agrfesta.sh.api.domain.Area
import org.agrfesta.sh.api.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.persistence.jdbc.dao.AreasDaoJdbcImpl
import org.agrfesta.sh.api.persistence.jdbc.repositories.AreasJdbcRepository
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

@WebMvcTest(AreasController::class)
@Import(AreasDaoJdbcImpl::class)
@ActiveProfiles("test")
class AreasControllerUnitTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired @MockkBean private val randomGenerator: RandomGenerator,
    @Autowired @MockkBean private val timeService: TimeService,
    @Autowired @MockkBean private val areasRepository: AreasJdbcRepository
) {

    init {
        every { randomGenerator.uuid() } returns UUID.randomUUID()
        every { timeService.now() } returns Instant.now()
    }

    @Test fun `create() return 500 when persistence creation fails`() {
        val name = aRandomUniqueString()
        every { areasRepository.persist(any()) } returns PersistenceFailure(Exception("area creation failure")).left()

        val responseBody: String = mockMvc.perform(post("/areas")
            .contentType("application/json")
            .content("""{"name": "$name"}"""))
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Unable to create Area '$name'!"
    }

    @Test fun `create() an indoor area when indoor is true`() {
        val name = aRandomUniqueString()
        val areaSlot = slot<Area>()
        every { areasRepository.persist(capture(areaSlot)) } answers { areaSlot.captured.right() }

        val responseBody: String = mockMvc.perform(post("/areas")
            .contentType("application/json")
            .content("""{"name": "$name", "isIndoor": true}"""))
            .andExpect(status().isCreated)
            .andReturn().response.contentAsString

        areaSlot.captured.isIndoor.shouldBeTrue()
        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Area '$name' successfully created!"
    }

    @Test fun `create() an outdoor area when indoor is false`() {
        val name = aRandomUniqueString()
        val areaSlot = slot<Area>()
        every { areasRepository.persist(capture(areaSlot)) } answers { areaSlot.captured.right() }

        val responseBody: String = mockMvc.perform(post("/areas")
            .contentType("application/json")
            .content("""{"name": "$name", "isIndoor": false}"""))
            .andExpect(status().isCreated)
            .andReturn().response.contentAsString

        areaSlot.captured.isIndoor.shouldBeFalse()
        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Area '$name' successfully created!"
    }

}
