package org.agrfesta.sh.api.providers.hon.devices

import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.agrfesta.sh.api.core.application.devices.DeviceModelCatalog
import org.agrfesta.sh.api.core.domain.devices.DeviceFeature
import org.agrfesta.sh.api.core.domain.devices.DeviceModel
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.providers.hon.HonService
import org.junit.jupiter.api.Test

class HonPrototypesTest {

    @Test
    fun `the AC models resolve in the catalog to prototypes bound to the HonAc driver`() {
        // Given
        val catalog = DeviceModelCatalog(listOf(HonAcAs25Prototype(), HonAcAs35Prototype()))

        // Then
        listOf(HonService.AC_AS25PBPHRA_PRE_MODEL, HonService.AC_AS35PBPHRA_PRE_MODEL).forEach { model ->
            withClue("$model should resolve to an AC prototype bound to HonAc") {
                val prototype = catalog.prototypeOf(DeviceModel(model)).shouldNotBeNull()
                // ACTUATOR only: the AC is deliberately NOT a sensor (its ambient readings are
                // position-biased, live-verified in #250/#251) — DevicePrototypesContractTest
                // pins roles to the driver's capability interfaces.
                prototype.roles shouldBe setOf(DeviceFeature.ACTUATOR)
                prototype.driverType shouldBe HonAc::class
                prototype.provider shouldBe Provider.HON
            }
        }
    }
}
