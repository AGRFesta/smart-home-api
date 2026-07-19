package org.agrfesta.sh.api.providers.hon.devices

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.mockk
import org.agrfesta.sh.api.core.domain.devices.DeviceModel
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.domain.aDevice
import org.agrfesta.sh.api.providers.hon.HonApiClient
import org.agrfesta.sh.api.providers.hon.HonApplianceStore
import org.agrfesta.sh.api.providers.hon.HonService
import org.junit.jupiter.api.Test

class HonDevicesFactoryTest {
    private val store = HonApplianceStore()
    private val factory = HonDevicesFactory(store, mockk<HonApiClient>())

    @Test
    fun `builds the HonAc driver for the known AC models`() {
        // Given
        val as25 = aDevice(provider = Provider.HON, model = DeviceModel(HonService.AC_AS25PBPHRA_PRE_MODEL))
        val as35 = aDevice(provider = Provider.HON, model = DeviceModel(HonService.AC_AS35PBPHRA_PRE_MODEL))

        // When
        val driver = factory.createDevice(as25)

        // Then
        withClue("the driver should carry the record's identity") {
            driver.shouldBeInstanceOf<HonAc>()
            driver.uuid shouldBe as25.uuid
            driver.deviceProviderId shouldBe as25.deviceProviderId
        }
        withClue("both known AC models should get the AC driver") {
            factory.createDevice(as35).shouldBeInstanceOf<HonAc>()
        }
    }

    @Test
    fun `builds a no-op driver for unknown hOn models`() {
        // Given: an appliance type the provider lists but has no prototype for yet
        val fridge = aDevice(provider = Provider.HON, model = DeviceModel("hon/REFRIGERATOR-X"))

        // When
        val driver = factory.createDevice(fridge)

        // Then
        withClue("an unknown model should get the no-op driver, still carrying the identity") {
            driver.shouldBeInstanceOf<HonUnknownDevice>()
            driver.uuid shouldBe fridge.uuid
            driver.deviceProviderId shouldBe fridge.deviceProviderId
        }
    }
}
