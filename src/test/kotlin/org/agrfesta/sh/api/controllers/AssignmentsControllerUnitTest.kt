package org.agrfesta.sh.api.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.slot
import java.time.Period
import java.time.ZonedDateTime
import java.util.*
import org.agrfesta.sh.api.persistence.jdbc.dao.ActuatorsAssignmentsDaoJdbcImpl
import org.agrfesta.sh.api.persistence.jdbc.dao.SensorsAssignmentsDaoJdbcImpl
import org.agrfesta.sh.api.persistence.jdbc.entities.aSensorAssignmentEntity
import org.agrfesta.sh.api.persistence.jdbc.entities.aSensorEntity
import org.agrfesta.sh.api.persistence.jdbc.entities.anActuatorEntity
import org.agrfesta.sh.api.persistence.jdbc.repositories.ActuatorsAssignmentsJdbcRepository
import org.agrfesta.sh.api.persistence.jdbc.repositories.DevicesJdbcRepository
import org.agrfesta.sh.api.persistence.jdbc.repositories.SensorsAssignmentsJdbcRepository
import org.agrfesta.sh.api.security.SecurityConfig
import org.agrfesta.sh.api.services.AssignmentsService
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

@WebMvcTest(AssignmentsController::class)
@Import(
    AssignmentsService::class,
    SensorsAssignmentsDaoJdbcImpl::class,
    ActuatorsAssignmentsDaoJdbcImpl::class,
    SecurityConfig::class
)
@ActiveProfiles("test")
class AssignmentsControllerUnitTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired @MockkBean private val randomGenerator: RandomGenerator,
    @Autowired @MockkBean private val timeService: TimeService,
    @Autowired @MockkBean private val sensorsAssignmentsRepository: SensorsAssignmentsJdbcRepository,
    @Autowired @MockkBean private val actuatorsAssignmentsRepository: ActuatorsAssignmentsJdbcRepository,
    @Autowired @MockkBean private val devicesJdbcRepository: DevicesJdbcRepository
) {
    private val now = ZonedDateTime.now()

    init {
        every { randomGenerator.uuid() } returns UUID.randomUUID()
        every { timeService.now() } returns now.toInstant()
    }

    ///// assignSensorToArea ///////////////////////////////////////////////////////////////////////////////////////////
    @Test fun `assignSensorToArea() return 401 when auth is missing`() {
        val responseBody: String = mockMvc.perform(
            post("/assignments/sensors")
                .contentType("application/json")
                .content("""{"areaId": "${UUID.randomUUID()}", "deviceId": "${UUID.randomUUID()}"}"""))
            .andExpect(status().isUnauthorized)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Missing Authorization header"
    }

    @Test fun `assignSensorToArea() return 401 when token is empty`() {
        val responseBody: String = mockMvc.perform(
            post("/assignments/sensors")
                .contentType("application/json")
                .header("Authorization", "Bearer ")
                .content("""{"areaId": "${UUID.randomUUID()}", "deviceId": "${UUID.randomUUID()}"}"""))
            .andExpect(status().isUnauthorized)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Empty token"
    }

    @Test fun `assignSensorToArea() return 401 when token is invalid`() {
        val responseBody: String = mockMvc.perform(
            post("/assignments/sensors")
                .contentType("application/json")
                .header("Authorization", "Bearer ${aRandomUniqueString()}")
                .content("""{"areaId": "${UUID.randomUUID()}", "deviceId": "${UUID.randomUUID()}"}"""))
            .andExpect(status().isUnauthorized)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Invalid token"
    }

    @Test fun `assignSensorToArea() returns 500 when device fetch fails`() {
        val failure = aRandomUniqueString()
        val areaUuid = UUID.randomUUID()
        val deviceUuid = UUID.randomUUID()
        every { devicesJdbcRepository.getDeviceById(deviceUuid) } throws Exception(failure)

        val responseBody: String = mockMvc.perform(
            post("/assignments/sensors")
                .contentType("application/json")
                .authenticated()
                .content("""{"areaId": "$areaUuid", "deviceId": "$deviceUuid"}"""))
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Unable to assign device $deviceUuid to area $areaUuid! [$failure]"
    }

    @Test fun `assignSensorToArea() returns 500 when device assignment fetch fails`() {
        val failure = aRandomUniqueString()
        val areaUuid = UUID.randomUUID()
        val sensorDeviceEntity = aSensorEntity()
        val deviceUuid = sensorDeviceEntity.uuid
        every { devicesJdbcRepository.getDeviceById(deviceUuid) } returns sensorDeviceEntity
        every { sensorsAssignmentsRepository.findByDevice(deviceUuid) } throws Exception(failure)

        val responseBody: String = mockMvc.perform(
            post("/assignments/sensors")
            .contentType("application/json")
                .authenticated()
            .content("""{"areaId": "$areaUuid", "deviceId": "$deviceUuid"}"""))
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Unable to assign device $deviceUuid to area $areaUuid! [$failure]"
    }

    @Test fun `assignSensorToArea() returns 500 when assignment persistence fails`() {
        val failure = aRandomUniqueString()
        val areaUuid = UUID.randomUUID()
        val sensorDeviceEntity = aSensorEntity()
        val deviceUuid = sensorDeviceEntity.uuid
        every { devicesJdbcRepository.getDeviceById(deviceUuid) } returns sensorDeviceEntity
        every { sensorsAssignmentsRepository.findByDevice(deviceUuid) } returns emptyList() // no previous assignments
        every { sensorsAssignmentsRepository.persistAssignment(any(), any()) } throws Exception(failure)

        val responseBody: String = mockMvc.perform(
            post("/assignments/sensors")
                .contentType("application/json")
                .authenticated()
                .content("""{"areaId": "$areaUuid", "deviceId": "$deviceUuid"}"""))
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Unable to assign device $deviceUuid to area $areaUuid! [$failure]"
    }

    @Test
    fun `assignSensorToArea() successfully assigns device, assigned to another area in the past but not anymore`() {
        val areaUuid = UUID.randomUUID()
        val sensorDeviceEntity = aSensorEntity()
        val deviceUuid = sensorDeviceEntity.uuid
        every { devicesJdbcRepository.getDeviceById(deviceUuid) } returns sensorDeviceEntity
        val assignmentEntity = aSensorAssignmentEntity(
            deviceUuid = deviceUuid,
            connectedOn = now.minus(Period.ofYears(1)).toInstant(), // A year ago
            disconnectedOn = now.minus(Period.ofMonths(6)).toInstant() // six months ago
        )
        every { sensorsAssignmentsRepository.findByDevice(deviceUuid) } returns listOf(assignmentEntity)
        val areaUuidSlot = slot<UUID>()
        val deviceUuidSlot = slot<UUID>()
        every {
            sensorsAssignmentsRepository.persistAssignment(capture(areaUuidSlot), capture(deviceUuidSlot))
        } returns Unit

        val responseBody: String = mockMvc.perform(
            post("/assignments/sensors")
                .contentType("application/json")
                .authenticated()
                .content("""{"areaId": "$areaUuid", "deviceId": "$deviceUuid"}"""))
            .andExpect(status().isCreated)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Device with id '$deviceUuid' successfully assigned to area with id '$areaUuid'!"
        areaUuidSlot.captured shouldBe areaUuid
        deviceUuidSlot.captured shouldBe deviceUuid
    }
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    ///// assignActuatorToArea /////////////////////////////////////////////////////////////////////////////////////////
    @Test fun `assignActuatorToArea() return 401 when auth is missing`() {
        val responseBody: String = mockMvc.perform(
            post("/assignments/actuators")
                .contentType("application/json")
                .content("""{"areaId": "${UUID.randomUUID()}", "deviceId": "${UUID.randomUUID()}"}"""))
            .andExpect(status().isUnauthorized)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Missing Authorization header"
    }

    @Test fun `assignActuatorToArea() return 401 when token is empty`() {
        val responseBody: String = mockMvc.perform(
            post("/assignments/actuators")
                .contentType("application/json")
                .header("Authorization", "Bearer ")
                .content("""{"areaId": "${UUID.randomUUID()}", "deviceId": "${UUID.randomUUID()}"}"""))
            .andExpect(status().isUnauthorized)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Empty token"
    }

    @Test fun `assignActuatorToArea() return 401 when token is invalid`() {
        val responseBody: String = mockMvc.perform(
            post("/assignments/actuators")
                .contentType("application/json")
                .header("Authorization", "Bearer ${aRandomUniqueString()}")
                .content("""{"areaId": "${UUID.randomUUID()}", "deviceId": "${UUID.randomUUID()}"}"""))
            .andExpect(status().isUnauthorized)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Invalid token"
    }

    @Test fun `assignActuatorToArea() returns 500 when device fetch fails`() {
        val failure = aRandomUniqueString()
        val areaUuid = UUID.randomUUID()
        val deviceUuid = UUID.randomUUID()
        every { devicesJdbcRepository.getDeviceById(deviceUuid) } throws Exception(failure)

        val responseBody: String = mockMvc.perform(
            post("/assignments/actuators")
                .contentType("application/json")
                .authenticated()
                .content("""{"areaId": "$areaUuid", "deviceId": "$deviceUuid"}"""))
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Unable to assign device $deviceUuid to area $areaUuid! [$failure]"
    }

    @Test fun `assignActuatorToArea() returns 500 when device assignment fetch fails`() {
        val failure = aRandomUniqueString()
        val areaUuid = UUID.randomUUID()
        val actuatorDeviceEntity = anActuatorEntity()
        val deviceUuid = actuatorDeviceEntity.uuid
        every { devicesJdbcRepository.getDeviceById(deviceUuid) } returns actuatorDeviceEntity
        every { actuatorsAssignmentsRepository.findByDevice(deviceUuid) } throws Exception(failure)

        val responseBody: String = mockMvc.perform(
            post("/assignments/actuators")
                .contentType("application/json")
                .authenticated()
                .content("""{"areaId": "$areaUuid", "deviceId": "$deviceUuid"}"""))
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Unable to assign device $deviceUuid to area $areaUuid! [$failure]"
    }

    @Test fun `assignActuatorToArea() returns 500 when assignment persistence fails`() {
        val failure = aRandomUniqueString()
        val areaUuid = UUID.randomUUID()
        val actuatorDeviceEntity = anActuatorEntity()
        val deviceUuid = actuatorDeviceEntity.uuid
        every { devicesJdbcRepository.getDeviceById(deviceUuid) } returns actuatorDeviceEntity
        every { actuatorsAssignmentsRepository.findByDevice(deviceUuid) } returns emptyList() // no previous assignments
        every { actuatorsAssignmentsRepository.persistAssignment(any(), any()) } throws Exception(failure)

        val responseBody: String = mockMvc.perform(
            post("/assignments/actuators")
                .contentType("application/json")
                .authenticated()
                .content("""{"areaId": "$areaUuid", "deviceId": "$deviceUuid"}"""))
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Unable to assign device $deviceUuid to area $areaUuid! [$failure]"
    }
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

}
