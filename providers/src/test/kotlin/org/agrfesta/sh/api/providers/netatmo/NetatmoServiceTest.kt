package org.agrfesta.sh.api.providers.netatmo

import arrow.core.right
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import org.agrfesta.sh.api.core.domain.devices.DeviceModel
import org.junit.jupiter.api.Test

class NetatmoServiceTest {
    private val netatmoClient: NetatmoClient = mockk()
    private val mapper = jacksonObjectMapper()

    private val sut = NetatmoService(netatmoClient)

    @Test
    fun `getAllDevices() assigns the constant provider-qualified Netatmo model`() {
        // Given
        coEvery { netatmoClient.getHomesData() } returns mapper.aHomeData().right()

        // When
        val result = sut.getAllDevices().shouldBeRight()

        // Then
        val device = result.single()
        withClue("Netatmo devices carry the constant provider-qualified model") {
            device.model shouldBe DeviceModel("netatmo/Smarther")
        }
    }
}
