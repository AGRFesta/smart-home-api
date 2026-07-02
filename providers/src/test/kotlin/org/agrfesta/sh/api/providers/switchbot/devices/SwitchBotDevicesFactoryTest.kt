package org.agrfesta.sh.api.providers.switchbot.devices

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.mockk
import org.agrfesta.sh.api.core.domain.devices.DeviceModel
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.domain.aDevice
import org.agrfesta.sh.api.providers.switchbot.SwitchBotDevicesClient
import org.junit.jupiter.api.Test

class SwitchBotDevicesFactoryTest {
    private val client: SwitchBotDevicesClient = mockk()
    private val factory = SwitchBotDevicesFactory(client)

    @Test fun `builds a SwitchBotMeter for the Meter model`() {
        // Given
        val device = aDevice(model = DeviceModel("switchbot/Meter"))

        // When
        val driver = factory.createDevice(device)

        // Then
        driver.shouldBeInstanceOf<SwitchBotMeter>()
        driver.provider shouldBe Provider.SWITCHBOT
    }

    @Test fun `builds a SwitchBotMeter for the MeterPlus model`() {
        // Given
        val device = aDevice(model = DeviceModel("switchbot/MeterPlus"))

        // When
        val driver = factory.createDevice(device)

        // Then
        driver.shouldBeInstanceOf<SwitchBotMeter>()
    }

    @Test fun `builds a SwitchBotMeter for the WoIOSensor model`() {
        // Given
        val device = aDevice(model = DeviceModel("switchbot/WoIOSensor"))

        // When
        val driver = factory.createDevice(device)

        // Then
        driver.shouldBeInstanceOf<SwitchBotMeter>()
    }

    @Test fun `builds a SwitchBotMiniHub for the Hub Mini model`() {
        // Given
        val device = aDevice(model = DeviceModel("switchbot/Hub Mini"))

        // When
        val driver = factory.createDevice(device)

        // Then
        driver.shouldBeInstanceOf<SwitchBotMiniHub>()
        driver.provider shouldBe Provider.SWITCHBOT
    }
}
