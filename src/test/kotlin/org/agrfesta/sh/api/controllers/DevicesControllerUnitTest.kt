package org.agrfesta.sh.api.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.shouldBe
import io.mockk.every
import org.agrfesta.sh.api.persistence.DevicesDao
import org.agrfesta.sh.api.persistence.jdbc.dao.SensorsAssignmentsDaoJdbcImpl
import org.agrfesta.sh.api.persistence.jdbc.repositories.DevicesJdbcRepository
import org.agrfesta.sh.api.persistence.jdbc.repositories.SensorsAssignmentsJdbcRepository
import org.agrfesta.sh.api.providers.switchbot.SwitchBotDevicesClient
import org.agrfesta.sh.api.providers.switchbot.SwitchBotService
import org.agrfesta.sh.api.services.DevicesService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(DevicesController::class)
@Import(SwitchBotService::class, DevicesService::class, SensorsAssignmentsDaoJdbcImpl::class)
@ActiveProfiles("test")
class DevicesControllerUnitTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired @MockkBean private val devicesDao: DevicesDao,
    @Autowired @MockkBean private val switchBotDevicesClient: SwitchBotDevicesClient,
    @Autowired @MockkBean private val sensorsAssignmentsJdbcRepository: SensorsAssignmentsJdbcRepository,
    @Autowired @MockkBean private val devicesJdbcRepository: DevicesJdbcRepository
) {

    @Test
    fun `refresh() returns 500 when is unable to fetch devices from db`() {
        val failure = Exception("devices fetch failure")
        every { devicesDao.getAll() } throws failure

        val resultContent: String = mockMvc.perform(post("/devices/refresh"))
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(resultContent, MessageResponse::class.java)
        response.message shouldBe "Unable to fetch persisted devices!"
    }

}
