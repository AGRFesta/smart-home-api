package org.agrfesta.sh.api.controllers

import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import java.util.*
import org.agrfesta.sh.api.domain.aDevice
import org.agrfesta.sh.api.domain.aDeviceDataValue
import org.agrfesta.sh.api.core.domain.devices.DeviceDataValue
import org.agrfesta.sh.api.core.domain.devices.DeviceDto
import org.agrfesta.sh.api.core.domain.devices.DevicesProvider
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.core.domain.failures.MessageFailure
import org.agrfesta.sh.api.core.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.security.SecurityConfig
import org.agrfesta.sh.api.services.DevicesRefreshResult
import org.agrfesta.sh.api.services.DevicesService
import org.agrfesta.test.mothers.aRandomUniqueString
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(DevicesController::class)
@Import(SecurityConfig::class)
@ActiveProfiles("test")
class DevicesControllerMvcSliceTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired @MockkBean private val devicesService: DevicesService,
    @Autowired @MockkBean private val devicesProvider: DevicesProvider
) {
    private val authTestSupport = AuthTestSupport(mockMvc, objectMapper)

    ///// refresh /////////////////////////////////////////////////////////////////////////////////////////////////////////

    @TestFactory fun `refresh() auth tests`() = authTestSupport.dynamicTestsBy {
        post("/devices/refresh")
    }

    @Test fun `refresh() returns 500 when is unable to fetch devices from db`() {
        every { devicesService.getAllDto() } returns PersistenceFailure(Exception("db failure")).left()

        val resultContent: String = mockMvc.perform(
            post("/devices/refresh")
                .authenticated())
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(resultContent, MessageResponse::class.java)
        response.message shouldBe "Unable to fetch persisted devices!"
    }

    @Test fun `refresh() returns 200 with empty response when no stored devices and provider returns empty`() {
        every { devicesService.getAllDto() } returns emptyList<DeviceDto>().right()
        every { devicesProvider.getAllDevices() } returns emptyList<DeviceDataValue>().right()
        every { devicesService.refresh(any(), any()) } returns DevicesRefreshResult()

        val resultContent: String = mockMvc.perform(
            post("/devices/refresh")
                .authenticated())
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        val response: DevicesRefreshResponse = objectMapper.readValue(resultContent, DevicesRefreshResponse::class.java)
        response.newDevices.shouldBeEmpty()
        response.updatedDevices.shouldBeEmpty()
        response.detachedDevices.shouldBeEmpty()
    }

    @Test fun `refresh() returns 200 with new devices when provider returns unknown devices`() {
        val deviceData = aDeviceDataValue()
        val createdUuid = UUID.randomUUID()
        every { devicesService.getAllDto() } returns emptyList<DeviceDto>().right()
        every { devicesProvider.getAllDevices() } returns listOf(deviceData).right()
        every { devicesService.refresh(any(), any()) } returns DevicesRefreshResult(newDevices = listOf(deviceData))
        every { devicesService.createDevice(deviceData) } returns createdUuid.right()

        val resultContent: String = mockMvc.perform(
            post("/devices/refresh")
                .authenticated())
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        val response: DevicesRefreshResponse = objectMapper.readValue(resultContent, DevicesRefreshResponse::class.java)
        response.newDevices shouldHaveSize 1
        response.updatedDevices.shouldBeEmpty()
        response.detachedDevices.shouldBeEmpty()
    }

    @Test fun `refresh() returns 200 with updated devices when provider returns known devices`() {
        val storedDevice = aDevice()
        val updatedDevice = storedDevice.copy(name = aRandomUniqueString())
        every { devicesService.getAllDto() } returns listOf(storedDevice).right()
        every { devicesProvider.getAllDevices() } returns emptyList<DeviceDataValue>().right()
        every { devicesService.refresh(any(), any()) } returns DevicesRefreshResult(updatedDevices = listOf(updatedDevice))
        every { devicesService.update(updatedDevice) } returns Unit.right()

        val resultContent: String = mockMvc.perform(
            post("/devices/refresh")
                .authenticated())
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        val response: DevicesRefreshResponse = objectMapper.readValue(resultContent, DevicesRefreshResponse::class.java)
        response.newDevices.shouldBeEmpty()
        response.updatedDevices shouldHaveSize 1
        response.detachedDevices.shouldBeEmpty()
    }

    @Test fun `refresh() returns 200 with detached devices when stored devices are not in provider response`() {
        val storedDevice = aDevice()
        val detachedDevice = storedDevice.copy(name = aRandomUniqueString())
        every { devicesService.getAllDto() } returns listOf(storedDevice).right()
        every { devicesProvider.getAllDevices() } returns emptyList<DeviceDataValue>().right()
        every { devicesService.refresh(any(), any()) } returns DevicesRefreshResult(detachedDevices = listOf(detachedDevice))
        every { devicesService.update(detachedDevice) } returns Unit.right()

        val resultContent: String = mockMvc.perform(
            post("/devices/refresh")
                .authenticated())
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        val response: DevicesRefreshResponse = objectMapper.readValue(resultContent, DevicesRefreshResponse::class.java)
        response.newDevices.shouldBeEmpty()
        response.updatedDevices.shouldBeEmpty()
        response.detachedDevices shouldHaveSize 1
    }

    @Test fun `refresh() returns 200 when provider fails`() {
        every { devicesService.getAllDto() } returns emptyList<DeviceDto>().right()
        every { devicesProvider.provider } returns Provider.SWITCHBOT
        every { devicesProvider.getAllDevices() } returns MessageFailure("provider unavailable").left()
        every { devicesService.refresh(any(), any()) } returns DevicesRefreshResult()

        val resultContent: String = mockMvc.perform(
            post("/devices/refresh")
                .authenticated())
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        val response: DevicesRefreshResponse = objectMapper.readValue(resultContent, DevicesRefreshResponse::class.java)
        response.newDevices.shouldBeEmpty()
        response.updatedDevices.shouldBeEmpty()
        response.detachedDevices.shouldBeEmpty()
    }

    @Test fun `refresh() returns 200 excluding new devices that fail to persist`() {
        val deviceData = aDeviceDataValue()
        every { devicesService.getAllDto() } returns emptyList<DeviceDto>().right()
        every { devicesProvider.getAllDevices() } returns listOf(deviceData).right()
        every { devicesService.refresh(any(), any()) } returns DevicesRefreshResult(newDevices = listOf(deviceData))
        every { devicesService.createDevice(deviceData) } returns PersistenceFailure(Exception("insert failure")).left()

        val resultContent: String = mockMvc.perform(
            post("/devices/refresh")
                .authenticated())
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        val response: DevicesRefreshResponse = objectMapper.readValue(resultContent, DevicesRefreshResponse::class.java)
        response.newDevices.shouldBeEmpty()
        response.updatedDevices.shouldBeEmpty()
        response.detachedDevices.shouldBeEmpty()
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

}
