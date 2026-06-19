package org.agrfesta.sh.api.providers.switchbot.devices

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.mockk
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.domain.aDevice
import org.agrfesta.sh.api.providers.switchbot.SwitchBotDevicesClient
import org.junit.jupiter.api.Test

class SwitchBotDevicesFactoryTest {
    private val client: SwitchBotDevicesClient = mockk()
    private val factory = SwitchBotDevicesFactory(client)

    @Test fun `creates a SWITCHBOT driver for a featureless device`() {
        // Given
        val device = aDevice(features = emptySet())

        // When
        val driver = factory.createDevice(device)

        // Then
        driver.shouldBeInstanceOf<SwitchBotMiniHub>()
        driver.provider shouldBe Provider.SWITCHBOT
    }
}
