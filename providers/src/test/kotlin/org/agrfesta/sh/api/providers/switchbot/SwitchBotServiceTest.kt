package org.agrfesta.sh.api.providers.switchbot

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import org.agrfesta.sh.api.core.domain.devices.DeviceFeature.SENSOR
import org.agrfesta.sh.api.core.domain.devices.DeviceModel
import org.junit.jupiter.api.Test

class SwitchBotServiceTest {
    private val devicesClient: SwitchBotDevicesClient = mockk()
    private val mapper = jacksonObjectMapper()

    private val sut = SwitchBotService(devicesClient, mapper)

    @Test
    fun `getAllDevices() maps the device model from its type while keeping features`() {
        // Given
        val meterDevice = mapper.aSwitchBotDevice(deviceType = SwitchBotDeviceType.METER)
        coEvery { devicesClient.getDevices() } returns
            mapper.aSwitchBotDevicesListSuccessResponse(listOf(meterDevice))

        // When
        val result = sut.getAllDevices().shouldBeRight()

        // Then
        val device = result.single()
        withClue("model should be derived from the SwitchBot device type") {
            device.model shouldBe DeviceModel("switchbot/Meter")
        }
        withClue("features must still be mapped from the device type") {
            device.features shouldBe setOf(SENSOR)
        }
    }
}
