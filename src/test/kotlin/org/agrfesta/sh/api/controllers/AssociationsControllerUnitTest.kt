package org.agrfesta.sh.api.controllers

import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.slot
import org.agrfesta.sh.api.persistence.AssociationSuccess
import org.agrfesta.sh.api.persistence.PersistenceFailure
import org.agrfesta.sh.api.persistence.jdbc.dao.AssociationsDaoJdbcImpl
import org.agrfesta.sh.api.persistence.jdbc.entities.AssociationEntity
import org.agrfesta.sh.api.persistence.jdbc.entities.anAssociationEntity
import org.agrfesta.sh.api.persistence.jdbc.repositories.AssociationsJdbcRepository
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
import java.time.Period
import java.time.ZonedDateTime
import java.util.*

@WebMvcTest(AssociationsController::class)
@Import(AssociationsDaoJdbcImpl::class)
@ActiveProfiles("test")
class AssociationsControllerUnitTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired @MockkBean private val randomGenerator: RandomGenerator,
    @Autowired @MockkBean private val timeService: TimeService,
    @Autowired @MockkBean private val associationsRepository: AssociationsJdbcRepository
) {
    private val now = ZonedDateTime.now()

    init {
        every { randomGenerator.uuid() } returns UUID.randomUUID()
        every { timeService.now() } returns now.toInstant()
    }

    @Test fun `create() returns 500 when device association fetch fails`() {
        val failure = aRandomUniqueString()
        val areaUuid = UUID.randomUUID()
        val deviceUuid = UUID.randomUUID()
        every { associationsRepository.findByDevice(deviceUuid) } returns PersistenceFailure(Exception(failure)).left()

        val responseBody: String = mockMvc.perform(
            post("/associations")
            .contentType("application/json")
            .content("""{"areaId": "$areaUuid", "deviceId": "$deviceUuid"}"""))
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Unable to associate device $deviceUuid to area $areaUuid! [$failure]"
    }

    @Test fun `create() returns 500 when association persistence fails`() {
        val failure = aRandomUniqueString()
        val areaUuid = UUID.randomUUID()
        val deviceUuid = UUID.randomUUID()
        every { associationsRepository.findByDevice(deviceUuid) } returns
                emptyList<AssociationEntity>().right() // no previous associations
        every { associationsRepository.persistAssociation(any(), any()) } returns
                PersistenceFailure(Exception(failure)).left()

        val responseBody: String = mockMvc.perform(
            post("/associations")
                .contentType("application/json")
                .content("""{"areaId": "$areaUuid", "deviceId": "$deviceUuid"}"""))
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Unable to associate device $deviceUuid to area $areaUuid! [$failure]"
    }

    @Test fun `create() successfully assigns device, assigned to another area in the past but not anymore`() {
        val areaUuid = UUID.randomUUID()
        val deviceUuid = UUID.randomUUID()
        val associationEntity = anAssociationEntity(
            deviceUuid = deviceUuid,
            connectedOn = now.minus(Period.ofYears(1)).toInstant(), // A year ago
            disconnectedOn = now.minus(Period.ofMonths(6)).toInstant() // six months ago
        )
        every { associationsRepository.findByDevice(deviceUuid) } returns listOf(associationEntity).right()
        val areaUuidSlot = slot<UUID>()
        val deviceUuidSlot = slot<UUID>()
        every { associationsRepository.persistAssociation(capture(areaUuidSlot), capture(deviceUuidSlot)) } returns
                AssociationSuccess.right()

        val responseBody: String = mockMvc.perform(
            post("/associations")
                .contentType("application/json")
                .content("""{"areaId": "$areaUuid", "deviceId": "$deviceUuid"}"""))
            .andExpect(status().isCreated)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Device with id '$deviceUuid' successfully assigned to area with id '$areaUuid'!"
        areaUuidSlot.captured shouldBe areaUuid
        deviceUuidSlot.captured shouldBe deviceUuid
    }

}
