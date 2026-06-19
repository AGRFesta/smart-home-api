package org.agrfesta.sh.api.providers.switchbot.devices

import io.kotest.matchers.shouldBe
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.test.mothers.aRandomUniqueString
import org.junit.jupiter.api.Test
import java.util.UUID

class SwitchBotMiniHubTest {

    @Test fun `provider is SWITCHBOT`() {
        // Given
        val miniHub = SwitchBotMiniHub(
            uuid = UUID.randomUUID(),
            deviceProviderId = aRandomUniqueString()
        )

        // When
        val provider = miniHub.provider

        // Then
        provider shouldBe Provider.SWITCHBOT
    }
}
