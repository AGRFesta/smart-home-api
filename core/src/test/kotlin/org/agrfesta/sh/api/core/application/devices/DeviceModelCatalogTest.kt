package org.agrfesta.sh.api.core.application.devices

import io.kotest.matchers.shouldBe
import org.agrfesta.sh.api.core.domain.devices.DeviceFeature.ACTUATOR
import org.agrfesta.sh.api.core.domain.devices.DeviceFeature.SENSOR
import org.agrfesta.sh.api.core.domain.devices.DeviceModel
import org.agrfesta.sh.api.domain.aDevicePrototype
import org.junit.jupiter.api.Test

class DeviceModelCatalogTest {

    @Test fun `prototypeOf() returns the prototype registered for that model`() {
        // Given
        val model = DeviceModel("switchbot/Meter")
        val prototype = aDevicePrototype(model = model)
        val catalog = DeviceModelCatalog(listOf(prototype))

        // When
        val result = catalog.prototypeOf(model)

        // Then
        result shouldBe prototype
    }

    @Test fun `prototypeOf() returns null for an unregistered model`() {
        // Given
        val catalog = DeviceModelCatalog(listOf(aDevicePrototype(model = DeviceModel("switchbot/Meter"))))

        // When
        val result = catalog.prototypeOf(DeviceModel("switchbot/Hub Mini"))

        // Then
        result shouldBe null
    }

    @Test fun `rolesOf() returns the roles of the prototype registered for that model`() {
        // Given
        val model = DeviceModel("netatmo/Smarther")
        val catalog = DeviceModelCatalog(listOf(aDevicePrototype(model = model, roles = setOf(SENSOR, ACTUATOR))))

        // When / Then
        catalog.rolesOf(model) shouldBe setOf(SENSOR, ACTUATOR)
    }

    @Test fun `rolesOf() returns empty set for a null model`() {
        val catalog = DeviceModelCatalog(listOf(aDevicePrototype(roles = setOf(SENSOR))))

        catalog.rolesOf(null) shouldBe emptySet()
    }

    @Test fun `rolesOf() returns empty set for an unregistered model`() {
        val catalog = DeviceModelCatalog(
            listOf(aDevicePrototype(model = DeviceModel("test/known"), roles = setOf(SENSOR)))
        )

        catalog.rolesOf(DeviceModel("test/unknown")) shouldBe emptySet()
    }
}
