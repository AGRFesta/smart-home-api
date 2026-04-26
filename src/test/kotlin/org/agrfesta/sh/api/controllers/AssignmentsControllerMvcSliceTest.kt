package org.agrfesta.sh.api.controllers

import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.shouldBe
import io.mockk.every
import java.util.*
import org.agrfesta.sh.api.core.domain.failures.AreaNotFound
import org.agrfesta.sh.api.core.domain.failures.DeviceNotFound
import org.agrfesta.sh.api.core.domain.failures.NotASensor
import org.agrfesta.sh.api.core.domain.failures.NotAnActuator
import org.agrfesta.sh.api.core.domain.failures.SameAreaAssignment
import org.agrfesta.sh.api.core.domain.failures.SensorAlreadyAssigned
import org.agrfesta.sh.api.domain.anActuator
import org.agrfesta.sh.api.security.SecurityConfig
import org.agrfesta.sh.api.services.AssignmentsService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestConstructor
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(AssignmentsController::class)
@Import(SecurityConfig::class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@ActiveProfiles("test")
class AssignmentsControllerMvcSliceTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    @MockkBean private val assignmentsService: AssignmentsService
) {
    private val authTestSupport = AuthTestSupport(mockMvc, objectMapper)

    ///// assignSensorToArea ///////////////////////////////////////////////////////////////////////////////////////////

    @TestFactory fun `assignSensorToArea() auth tests`() = authTestSupport.dynamicTestsBy {
        post("/assignments/sensors")
            .contentType("application/json")
            .content("""{"areaId": "${UUID.randomUUID()}", "deviceId": "${UUID.randomUUID()}"}""")
    }

    @Test fun `assignSensorToArea() returns 404 when area is not found`() {
        val areaId = UUID.randomUUID()
        val deviceId = UUID.randomUUID()
        every { assignmentsService.assignSensorToArea(areaId, deviceId) } returns AreaNotFound(areaId).left()

        val responseBody: String = mockMvc.perform(
            post("/assignments/sensors")
                .contentType("application/json")
                .authenticated()
                .content("""{"areaId": "$areaId", "deviceId": "$deviceId"}"""))
            .andExpect(status().isNotFound)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Area with id '$areaId' is missing!"
    }

    @Test fun `assignSensorToArea() returns 404 when device is not found`() {
        val areaId = UUID.randomUUID()
        val deviceId = UUID.randomUUID()
        every { assignmentsService.assignSensorToArea(areaId, deviceId) } returns DeviceNotFound(deviceId).left()

        val responseBody: String = mockMvc.perform(
            post("/assignments/sensors")
                .contentType("application/json")
                .authenticated()
                .content("""{"areaId": "$areaId", "deviceId": "$deviceId"}"""))
            .andExpect(status().isNotFound)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Device with id '$deviceId' is missing!"
    }

    @Test fun `assignSensorToArea() returns 400 when device is not a sensor`() {
        val areaId = UUID.randomUUID()
        val device = anActuator()
        every { assignmentsService.assignSensorToArea(areaId, device.uuid) } returns
                NotASensor(device.uuid, device.features).left()

        val responseBody: String = mockMvc.perform(
            post("/assignments/sensors")
                .contentType("application/json")
                .authenticated()
                .content("""{"areaId": "$areaId", "deviceId": "${device.uuid}"}"""))
            .andExpect(status().isBadRequest)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Device with id '${device.uuid}' is not a sensor!"
    }

    @Test fun `assignSensorToArea() returns 400 when sensor is already assigned to the same area`() {
        val areaId = UUID.randomUUID()
        val deviceId = UUID.randomUUID()
        every { assignmentsService.assignSensorToArea(areaId, deviceId) } returns SameAreaAssignment.left()

        val responseBody: String = mockMvc.perform(
            post("/assignments/sensors")
                .contentType("application/json")
                .authenticated()
                .content("""{"areaId": "$areaId", "deviceId": "$deviceId"}"""))
            .andExpect(status().isBadRequest)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Device with id '$deviceId' is already assigned to area with id '$areaId'!"
    }

    @Test fun `assignSensorToArea() returns 400 when sensor is already assigned to another area`() {
        val areaId = UUID.randomUUID()
        val deviceId = UUID.randomUUID()
        every { assignmentsService.assignSensorToArea(areaId, deviceId) } returns SensorAlreadyAssigned.left()

        val responseBody: String = mockMvc.perform(
            post("/assignments/sensors")
                .contentType("application/json")
                .authenticated()
                .content("""{"areaId": "$areaId", "deviceId": "$deviceId"}"""))
            .andExpect(status().isBadRequest)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Device with id '$deviceId' is already assigned to another area!"
    }

    @Test fun `assignSensorToArea() returns 201 on success`() {
        val areaId = UUID.randomUUID()
        val deviceId = UUID.randomUUID()
        every { assignmentsService.assignSensorToArea(areaId, deviceId) } returns Unit.right()

        val responseBody: String = mockMvc.perform(
            post("/assignments/sensors")
                .contentType("application/json")
                .authenticated()
                .content("""{"areaId": "$areaId", "deviceId": "$deviceId"}"""))
            .andExpect(status().isCreated)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Device with id '$deviceId' successfully assigned to area with id '$areaId'!"
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    ///// assignActuatorToArea /////////////////////////////////////////////////////////////////////////////////////////

    @TestFactory fun `assignActuatorToArea() auth tests`() = authTestSupport.dynamicTestsBy {
        post("/assignments/actuators")
            .contentType("application/json")
            .content("""{"areaId": "${UUID.randomUUID()}", "deviceId": "${UUID.randomUUID()}"}""")
    }

    @Test fun `assignActuatorToArea() returns 404 when area is not found`() {
        val areaId = UUID.randomUUID()
        val deviceId = UUID.randomUUID()
        every { assignmentsService.assignActuatorToArea(areaId, deviceId) } returns AreaNotFound(areaId).left()

        val responseBody: String = mockMvc.perform(
            post("/assignments/actuators")
                .contentType("application/json")
                .authenticated()
                .content("""{"areaId": "$areaId", "deviceId": "$deviceId"}"""))
            .andExpect(status().isNotFound)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Area with id '$areaId' is missing!"
    }

    @Test fun `assignActuatorToArea() returns 404 when device is not found`() {
        val areaId = UUID.randomUUID()
        val deviceId = UUID.randomUUID()
        every { assignmentsService.assignActuatorToArea(areaId, deviceId) } returns DeviceNotFound(deviceId).left()

        val responseBody: String = mockMvc.perform(
            post("/assignments/actuators")
                .contentType("application/json")
                .authenticated()
                .content("""{"areaId": "$areaId", "deviceId": "$deviceId"}"""))
            .andExpect(status().isNotFound)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Device with id '$deviceId' is missing!"
    }

    @Test fun `assignActuatorToArea() returns 400 when device is not an actuator`() {
        val areaId = UUID.randomUUID()
        val deviceId = UUID.randomUUID()
        every {
            assignmentsService.assignActuatorToArea(areaId, deviceId)
        } returns NotAnActuator(deviceId, emptySet()).left()

        val responseBody: String = mockMvc.perform(
            post("/assignments/actuators")
                .contentType("application/json")
                .authenticated()
                .content("""{"areaId": "$areaId", "deviceId": "$deviceId"}"""))
            .andExpect(status().isBadRequest)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Device with id '$deviceId' is not an actuator!"
    }

    @Test fun `assignActuatorToArea() returns 400 when actuator is already assigned to the same area`() {
        val areaId = UUID.randomUUID()
        val deviceId = UUID.randomUUID()
        every { assignmentsService.assignActuatorToArea(areaId, deviceId) } returns SameAreaAssignment.left()

        val responseBody: String = mockMvc.perform(
            post("/assignments/actuators")
                .contentType("application/json")
                .authenticated()
                .content("""{"areaId": "$areaId", "deviceId": "$deviceId"}"""))
            .andExpect(status().isBadRequest)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Device with id '$deviceId' is already assigned to area with id '$areaId'!"
    }

    @Test fun `assignActuatorToArea() returns 201 on success`() {
        val areaId = UUID.randomUUID()
        val deviceId = UUID.randomUUID()
        every { assignmentsService.assignActuatorToArea(areaId, deviceId) } returns Unit.right()

        val responseBody: String = mockMvc.perform(
            post("/assignments/actuators")
                .contentType("application/json")
                .authenticated()
                .content("""{"areaId": "$areaId", "deviceId": "$deviceId"}"""))
            .andExpect(status().isCreated)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Device with id '$deviceId' successfully assigned to area with id '$areaId'!"
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

}
