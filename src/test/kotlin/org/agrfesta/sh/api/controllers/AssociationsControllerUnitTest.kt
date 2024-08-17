package org.agrfesta.sh.api.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.slot
import org.agrfesta.sh.api.persistence.AssociationsDaoImpl
import org.agrfesta.sh.api.persistence.RoomsDaoImpl
import org.agrfesta.sh.api.persistence.entities.AssociationEntity
import org.agrfesta.sh.api.persistence.entities.aDeviceEntity
import org.agrfesta.sh.api.persistence.entities.anAssociationEntity
import org.agrfesta.sh.api.persistence.entities.aRoomEntity
import org.agrfesta.sh.api.persistence.repositories.AssociationsRepository
import org.agrfesta.sh.api.persistence.repositories.DevicesRepository
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
import java.time.Period
import java.time.ZonedDateTime
import java.util.*

@WebMvcTest(AssociationsController::class)
@Import(RoomsDaoImpl::class, AssociationsDaoImpl::class)
@ActiveProfiles("test")
class AssociationsControllerUnitTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired @MockkBean private val randomGenerator: RandomGenerator,
    @Autowired @MockkBean private val timeService: TimeService,
    @Autowired @MockkBean private val roomsRepository: RoomsRepository,
    @Autowired @MockkBean private val devicesRepository: DevicesRepository,
    @Autowired @MockkBean private val associationsRepository: AssociationsRepository
) {
    private val now = ZonedDateTime.now()

    init {
        every { randomGenerator.uuid() } returns UUID.randomUUID()
        every { timeService.now() } returns now.toInstant()
    }

    @Test fun `create() returns 500 when room fetch fails`() {
        val failure = aRandomUniqueString()
        val room = aRoomEntity()
        every { roomsRepository.findById(room.uuid) } throws Exception(failure)
        val device = aDeviceEntity()
        every { devicesRepository.findById(device.uuid) } returns Optional.of(device)

        val responseBody: String = mockMvc.perform(
            post("/associations")
            .contentType("application/json")
            .content("""{"roomId": "${room.uuid}", "deviceId": "${device.uuid}"}"""))
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Unable to associate device ${device.uuid} to room ${room.uuid}! [$failure]"
    }

    @Test fun `create() returns 500 when device fetch fails`() {
        val failure = aRandomUniqueString()
        val room = aRoomEntity()
        every { roomsRepository.findById(room.uuid) } returns Optional.of(room)
        val device = aDeviceEntity()
        every { devicesRepository.findById(device.uuid) } throws Exception(failure)

        val responseBody: String = mockMvc.perform(
            post("/associations")
                .contentType("application/json")
                .content("""{"roomId": "${room.uuid}", "deviceId": "${device.uuid}"}"""))
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Unable to associate device ${device.uuid} to room ${room.uuid}! [$failure]"
    }

    @Test fun `create() returns 500 when association persistence fails`() {
        val failure = aRandomUniqueString()
        val room = aRoomEntity()
        every { roomsRepository.findById(room.uuid) } returns Optional.of(room)
        val device = aDeviceEntity()
        every { devicesRepository.findById(device.uuid) } throws Exception(failure)
        every { associationsRepository.findByDevice(device) } returns emptyList() // no previous associations
        every { associationsRepository.save(any()) } throws Exception(failure)

        val responseBody: String = mockMvc.perform(
            post("/associations")
                .contentType("application/json")
                .content("""{"roomId": "${room.uuid}", "deviceId": "${device.uuid}"}"""))
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Unable to associate device ${device.uuid} to room ${room.uuid}! [$failure]"
    }

    @Test fun `create() successfully assigns device, assigned to another room in the past but not anymore`() {
        val room = aRoomEntity()
        every { roomsRepository.findById(room.uuid) } returns Optional.of(room)
        val device = aDeviceEntity()
        every { devicesRepository.findById(device.uuid) } returns Optional.of(device)
        val associationEntity = anAssociationEntity(
            device = device,
            connectedOn = now.minus(Period.ofYears(1)).toInstant(), // A year ago
            disconnectedOn = now.minus(Period.ofMonths(6)).toInstant() // six months ago
        )
        every { associationsRepository.findByDevice(device) } returns listOf(associationEntity)
        val associationSlot = slot<AssociationEntity>()
        every { associationsRepository.save(capture(associationSlot)) } answers
                { call.invocation.args[0] as AssociationEntity }

        val responseBody: String = mockMvc.perform(
            post("/associations")
                .contentType("application/json")
                .content("""{"roomId": "${room.uuid}", "deviceId": "${device.uuid}"}"""))
            .andExpect(status().isCreated)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Device with id '${device.uuid}' successfully assigned to room with id '${room.uuid}'!"
        val association = associationSlot.captured
        association.room shouldBe room
        association.device shouldBe device
        association.connectedOn shouldBe now.toInstant()
        association.disconnectedOn.shouldBeNull()
    }

}
