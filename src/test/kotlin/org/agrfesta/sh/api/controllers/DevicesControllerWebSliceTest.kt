package org.agrfesta.sh.api.controllers

import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.shouldBe
import io.mockk.every
import org.agrfesta.sh.api.domain.DevicesService
import org.agrfesta.sh.api.persistence.DevicesDao
import org.agrfesta.sh.api.providers.switchbot.SwitchBotDevicesClient
import org.agrfesta.sh.api.providers.switchbot.SwitchBotService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status


@WebMvcTest(DevicesController::class)
@Import(SwitchBotService::class)
@ActiveProfiles("test")
class DevicesControllerWebSliceTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired @MockkBean private val devicesDao: DevicesDao,
    @Autowired @MockkBean private val switchBotDevicesClient: SwitchBotDevicesClient,
    @Autowired @MockkBean private val devicesService: DevicesService
) {

    @Test
    fun `refresh() returns 500 when is unable to fetch devices from db`() {
        every { devicesDao.getAll() } throws Exception("devices fetch failure")

        val resultContent: String = mockMvc.perform(post("/devices/refresh"))
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        resultContent shouldBe "devices fetch failure"
    }

}
